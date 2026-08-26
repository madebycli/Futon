// Ported and adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.core.exceptions.resolve

import io.github.landwarderer.futon.core.exceptions.CloudFlareProtectedException
import io.github.landwarderer.futon.core.network.CloudflareHostCooldown
import io.github.landwarderer.futon.core.network.ContentHttpClient
import io.github.landwarderer.futon.core.network.webview.WebViewExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class CaptchaAutoResolveResult {
    SOLVED,
    INTERACTIVE_REQUIRED,
    HARD_BLOCKED,
    TIMED_OUT,
    COOLDOWN,
    FAILED,
}

/**
 * Kototoro-style automatic Cloudflare coordinator adapted to Futon's existing WebView solver.
 *
 * One off-screen Chromium solve is shared per host. A successful solve is verified by replaying
 * the original request when enough request context is available. Failed hosts enter a short
 * cooldown so concurrent reader/download/image requests cannot spawn a challenge storm.
 */
@Singleton
class CaptchaAutoResolveCoordinator @Inject constructor(
    private val webViewExecutor: WebViewExecutor,
    @ContentHttpClient private val httpClient: OkHttpClient,
    private val hostCooldown: CloudflareHostCooldown,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gates = ConcurrentHashMap<String, HostGate>()

    suspend fun resolve(
        exception: CloudFlareProtectedException,
        timeoutMs: Long = DEFAULT_SOLVE_TIMEOUT_MS,
    ): CaptchaAutoResolveResult {
        val host = runCatching { exception.originalUrl.toHttpUrl().host.lowercase() }
            .getOrElse { runCatching { exception.url.toHttpUrl().host.lowercase() }.getOrNull() }
            ?: return CaptchaAutoResolveResult.FAILED

        if (hostCooldown.isInCooldown(host)) return CaptchaAutoResolveResult.COOLDOWN
        return gates.computeIfAbsent(host) { HostGate(host) }.join(exception, timeoutMs)
    }

    private inner class HostGate(private val host: String) {
        private val mutex = Mutex()
        private var active: Deferred<CaptchaAutoResolveResult>? = null
        private var waiters = 0

        suspend fun join(
            exception: CloudFlareProtectedException,
            timeoutMs: Long,
        ): CaptchaAutoResolveResult {
            val deferred = mutex.withLock {
                waiters++
                active?.takeIf { !it.isCompleted }
                    ?: scope.async { runSolve(exception, timeoutMs) }.also { active = it }
            }
            return try {
                deferred.await()
            } finally {
                mutex.withLock {
                    waiters--
                    if (waiters <= 0) {
                        waiters = 0
                        if (active === deferred) {
                            active = null
                            if (deferred.isActive) deferred.cancel()
                        }
                        if (active == null) gates.remove(host, this@HostGate)
                    }
                }
            }
        }

        private suspend fun runSolve(
            exception: CloudFlareProtectedException,
            timeoutMs: Long,
        ): CaptchaAutoResolveResult {
            val solved = try {
                webViewExecutor.tryResolveCaptcha(exception, timeoutMs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }

            if (!solved) {
                hostCooldown.coolDown(host)
                return CaptchaAutoResolveResult.INTERACTIVE_REQUIRED
            }

            val verified = verifyOriginalRequest(exception)
            if (verified) return CaptchaAutoResolveResult.SOLVED

            hostCooldown.coolDown(host)
            return CaptchaAutoResolveResult.INTERACTIVE_REQUIRED
        }
    }

    private suspend fun verifyOriginalRequest(exception: CloudFlareProtectedException): Boolean {
        val originalUrl = exception.originalUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return true
        val method = exception.method.uppercase()
        if (method !in setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE")) return true

        val request = runCatching {
            val builder = Request.Builder()
                .url(originalUrl)
                .headers(exception.headers)
            val body = when {
                method == "GET" || method == "HEAD" -> null
                exception.body != null -> exception.body.toRequestBody(exception.contentType?.toMediaTypeOrNull())
                else -> ByteArray(0).toRequestBody(exception.contentType?.toMediaTypeOrNull())
            }
            builder.method(method, body).build()
        }.getOrNull() ?: return true

        repeat(PROBE_MAX_ATTEMPTS) { attempt ->
            val clear = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    CloudFlareHelper.checkResponseForProtection(response) == CloudFlareHelper.PROTECTION_NOT_DETECTED
                }
            }.getOrDefault(false)
            if (clear) return true
            if (attempt + 1 < PROBE_MAX_ATTEMPTS) delay(PROBE_RETRY_DELAY_MS)
        }
        return false
    }

    companion object {
        const val DEFAULT_SOLVE_TIMEOUT_MS = 20_000L
        private const val PROBE_MAX_ATTEMPTS = 10
        private const val PROBE_RETRY_DELAY_MS = 1_000L
    }
}
