// Adapted from Kototoro KotoNetworkHelper at c1128b91140053b081cc7453c87a16f52ab2f12a.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon.compat

import android.os.Looper
import android.util.Log
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import io.github.landwarderer.futon.core.exceptions.CloudFlareBlockedException
import io.github.landwarderer.futon.core.exceptions.CloudFlareProtectedException
import io.github.landwarderer.futon.core.exceptions.InteractiveActionRequiredException
import io.github.landwarderer.futon.core.network.CloudFlareInterceptor as FutonCloudFlareInterceptor
import io.github.landwarderer.futon.core.network.webview.WebViewExecutor
import io.github.landwarderer.futon.mihon.model.toMangaSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.network.CloudFlareHelper
import io.github.landwarderer.futon.mihon.parsers.network.UserAgents
import kotlinx.coroutines.runBlocking
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okhttp3.zstd.Zstd
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of Mihon's NetworkHelper interface.
 *
 * Wraps App's existing OkHttpClient to provide Mihon extensions with
 * access to the network stack, including CloudFlare bypassing and cookie management.
 *
 * The host client is preserved so proxy, TLS, DNS, cache, authenticators, dispatcher,
 * connection pool, and every other OkHttp setting survive. Only the interceptor lists are
 * rebuilt because Mihon/Keiyoushi sources own their response-compression contract.
 */
class MihonNetworkHelper(
    baseClient: OkHttpClient,
    val cookieJar: CookieJar,
    private val webViewExecutor: WebViewExecutor? = null,
) : NetworkHelper() {

    // Dynamically loaded extensions reference this class outside the app's static dex graph.
    private val zstdRuntimeDependency = Zstd

    /**
     * The OkHttpClient for current Mihon/Keiyoushi extensions.
     *
     * The first three application interceptors deliberately use Mihon's expected class names.
     * Current Keiyoushi sources validate these names on the default client before applying
     * source-specific configuration. Compression interceptors are deliberately excluded.
     */
    override val client: OkHttpClient = run {
        val builder = baseClient.newBuilder().apply {
            interceptors().clear()
            networkInterceptors().clear()
            cookieJar(cookieJar)
        }

        // Match Mihon's default application interceptor contract. Keep the uncaught-exception
        // wrapper first so unchecked failures from extensions become ordinary IO failures.
        builder.addInterceptor(UncaughtExceptionInterceptor())
        builder.addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))

        // Keiyoushi requires an interceptor with this exact class name in position three.
        // Do not use Futon's throwing delegate here: it would abort the chain before the
        // Mihon-specific resolver below can run. The real Cloudflare detection/resolve/retry
        // remains in this helper while this wrapper satisfies the upstream client contract.
        builder.addInterceptor(
            CloudflareInterceptor(
                delegate = Interceptor { chain -> chain.proceed(chain.request()) },
            ),
        )

        // Keep construction side-effect free. These filters are also exercised by plain JVM
        // regression tests where android.util.Log is intentionally unavailable.
        baseClient.interceptors.forEach { interceptor ->
            if (isCompatibleInterceptor(interceptor) && !isDefaultMihonInterceptor(interceptor)) {
                builder.addInterceptor(interceptor)
            }
        }

        baseClient.networkInterceptors.forEach { interceptor ->
            if (isCompatibleInterceptor(interceptor)) {
                builder.addNetworkInterceptor(interceptor)
            }
        }

        builder.addInterceptor { chain ->
            val originalRequest = chain.request()
            val request = enrichApiRequestHeadersIfNeeded(originalRequest)
            val response = chain.proceed(request)
            val challengeUrl = request.toChallengeUrl()

            when (CloudFlareHelper.checkResponseForProtection(response)) {
                CloudFlareHelper.PROTECTION_BLOCKED -> response.closeThrowing(
                    CloudFlareBlockedException(
                        url = challengeUrl,
                        source = request.tag(ContentSource::class.java) as MangaSource?,
                    ),
                )

                CloudFlareHelper.PROTECTION_CAPTCHA -> {
                    val host = request.url.host.lowercase()
                    val previousClearance = getClearanceCookie(request)

                    if (tryResolveWithWebView(request, challengeUrl)) {
                        response.close()
                        val retryRequest = enrichApiRequestHeadersIfNeeded(originalRequest)
                        val retryClearance = getClearanceCookie(retryRequest)
                        Log.i(
                            "MihonNetwork",
                            "Cloudflare WebView solved host=$host, clearanceChanged=${retryClearance != null && retryClearance != previousClearance}; retrying source request",
                        )

                        val retryResponse = chain.proceed(retryRequest)
                        when (CloudFlareHelper.checkResponseForProtection(retryResponse)) {
                            CloudFlareHelper.PROTECTION_NOT_DETECTED -> {
                                recentChallengeAttempts.remove(host)
                                Log.i("MihonNetwork", "Cloudflare retry succeeded host=$host status=${retryResponse.code}")
                                return@addInterceptor retryResponse
                            }

                            CloudFlareHelper.PROTECTION_BLOCKED -> retryResponse.closeThrowing(
                                CloudFlareBlockedException(
                                    url = challengeUrl,
                                    source = retryRequest.tag(ContentSource::class.java) as MangaSource?,
                                ),
                            )

                            else -> {
                                Log.w("MihonNetwork", "Cloudflare retry still protected host=$host status=${retryResponse.code}")
                                throwUnresolvedChallenge(
                                    response = retryResponse,
                                    request = retryRequest,
                                    challengeUrl = challengeUrl,
                                    host = host,
                                )
                            }
                        }
                    }

                    throwUnresolvedChallenge(
                        response = response,
                        request = request,
                        challengeUrl = challengeUrl,
                        host = host,
                    )
                }

                else -> response
            }
        }

        builder.addInterceptor { chain ->
            val request = chain.request()
            val requestCookies = cookieJar.loadForRequest(request.url)
            val cfClearanceCookie = requestCookies.firstOrNull { it.name == "cf_clearance" }?.value
            val cookieNames = requestCookies.joinToString(",") { it.name }
            Log.d(
                "MihonNetwork",
                "RequestMeta: host=${request.url.host}, ua=${request.header("User-Agent")}, referer=${request.header("Referer")}, origin=${request.header("Origin")}, hasCfClearance=${cfClearanceCookie != null}, cfClearance=${maskCookieValue(cfClearanceCookie)}, cookies=[$cookieNames]",
            )
            Log.d("MihonNetwork", "Request: ${request.method} ${request.url}")

            val response = chain.proceed(request)
            val responseCode = response.code
            val contentType = response.header("Content-Type")
            Log.d(
                "MihonNetwork",
                "Response: $responseCode, Content-Type: $contentType, cf-ray=${response.header("cf-ray")}, cf-mitigated=${response.header("cf-mitigated")}, server=${response.header("server")}, URL: ${request.url}",
            )

            if (!response.isSuccessful) {
                val source = response.body.source()
                source.request(200)
                val buffer = source.buffer.clone()
                val preview = buffer.readUtf8(minOf(200, buffer.size))
                Log.w("MihonNetwork", "Non-successful response ($responseCode) preview: $preview")
            }

            response
        }

        builder.build()
    }

    private fun isCompatibleInterceptor(interceptor: Interceptor): Boolean {
        return interceptor !== BrotliInterceptor &&
            interceptor.javaClass.simpleName != "BrotliInterceptor" &&
            interceptor.javaClass.simpleName != "GZipInterceptor" &&
            interceptor.javaClass.simpleName != "IgnoreGzipInterceptor" &&
            interceptor !is FutonCloudFlareInterceptor
    }

    private fun isDefaultMihonInterceptor(interceptor: Interceptor): Boolean {
        return interceptor.javaClass.simpleName in MIHON_COMPAT_INTERCEPTOR_NAMES
    }

    @Deprecated("The regular client handles Cloudflare by default")
    override val cloudflareClient: OkHttpClient = client.newBuilder()
        .addNetworkInterceptor(BrotliInterceptor)
        .build()

    override fun defaultUserAgentProvider(): String = UserAgents.CHROME_MOBILE

    private fun Response.closeThrowing(error: Throwable): Nothing {
        try {
            close()
        } catch (e: Exception) {
            error.addSuppressed(e)
        }
        throw error
    }

    private fun Request.toChallengeUrl(): String {
        val referer = header("Referer")?.toHttpUrlOrNull()
        if (referer != null && referer.host == url.host) {
            return referer.newBuilder().query(null).fragment(null).build().toString()
        }
        return url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
    }

    private fun enrichApiRequestHeadersIfNeeded(request: Request): Request {
        if (!request.url.encodedPath.startsWith("/api/")) return request
        val cookies = cookieJar.loadForRequest(request.url)
        val hasCfClearance = cookies.any { it.name == "cf_clearance" }
        if (!hasCfClearance) return request
        val origin = "${request.url.scheme}://${request.url.host}"
        var modified = false
        val builder = request.newBuilder()
        if (request.header("Referer").isNullOrBlank()) {
            builder.header("Referer", "$origin/")
            modified = true
        }
        if (request.header("Origin").isNullOrBlank()) {
            builder.header("Origin", origin)
            modified = true
        }
        if (request.header("Accept").isNullOrBlank()) {
            builder.header("Accept", "application/json, text/plain, */*")
            modified = true
        }
        if (request.header("Accept-Language").isNullOrBlank()) {
            builder.header("Accept-Language", "en-US,en;q=0.9")
            modified = true
        }
        if (request.header("Sec-Fetch-Site").isNullOrBlank()) {
            builder.header("Sec-Fetch-Site", "same-origin")
            modified = true
        }
        if (request.header("Sec-Fetch-Mode").isNullOrBlank()) {
            builder.header("Sec-Fetch-Mode", "cors")
            modified = true
        }
        if (request.header("Sec-Fetch-Dest").isNullOrBlank()) {
            builder.header("Sec-Fetch-Dest", "empty")
            modified = true
        }
        if (request.header("X-Requested-With").isNullOrBlank()) {
            builder.header("X-Requested-With", "XMLHttpRequest")
            modified = true
        }
        if (request.header("X-XSRF-TOKEN").isNullOrBlank()) {
            val xsrf = cookies.firstOrNull { it.name == "XSRF-TOKEN" }?.value
            val decodedXsrf = xsrf?.let {
                runCatching { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }.getOrDefault(it)
            }
            if (!decodedXsrf.isNullOrBlank()) {
                builder.header("X-XSRF-TOKEN", decodedXsrf)
                modified = true
            }
        }
        return if (modified) builder.build() else request
    }

    private fun getClearanceCookie(request: Request): String? =
        cookieJar.loadForRequest(request.url).firstOrNull { it.name == "cf_clearance" }?.value

    private fun tryResolveWithWebView(request: Request, challengeUrl: String): Boolean {
        val executor = webViewExecutor ?: run {
            Log.w("MihonNetwork", "Cloudflare WebView resolver unavailable: WebViewExecutor is null")
            return false
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w("MihonNetwork", "Cloudflare automatic solve skipped on main thread")
            return false
        }

        val exception = CloudFlareProtectedException(
            url = challengeUrl,
            source = null,
            headers = request.headers,
        )

        return runCatching {
            runBlocking {
                executor.tryResolveCaptcha(exception, CLOUDFLARE_RESOLVE_TIMEOUT_MS)
            }
        }.onFailure {
            Log.w("MihonNetwork", "Cloudflare WebView resolver failed for ${request.url.host}", it)
        }.getOrDefault(false)
    }

    private fun throwUnresolvedChallenge(
        response: Response,
        request: Request,
        challengeUrl: String,
        host: String,
    ): Nothing {
        val clearance = getClearanceCookie(request)

        if (shouldSkipInteractiveAction(host, clearance)) {
            Log.w("MihonNetwork", "Skip interactive action for host=$host: repeated challenge with same cf_clearance")
            response.closeThrowing(
                CloudFlareBlockedException(
                    url = challengeUrl,
                    source = request.tag(ContentSource::class.java),
                ),
            )
        }

        val source = request.tag(ContentSource::class.java)
        if (source == null) {
            Log.w("MihonNetwork", "Cloudflare challenge unresolved for host=$host and request has no ContentSource tag")
            response.closeThrowing(
                CloudFlareProtectedException(
                    url = challengeUrl,
                    source = null,
                    headers = request.headers,
                ),
            )
        }

        response.closeThrowing(
            InteractiveActionRequiredException(
                source = source.toMangaSource(),
                url = challengeUrl,
            ),
        )
    }

    private fun maskCookieValue(value: String?): String {
        if (value.isNullOrEmpty()) return "<empty>"
        return if (value.length <= 8) "***" else "${value.take(4)}...${value.takeLast(4)}"
    }

    private fun shouldSkipInteractiveAction(host: String, clearance: String?): Boolean {
        if (clearance.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        val last = recentChallengeAttempts[host]
        if (last == null || now - last.timestampMs > INTERACTIVE_RETRY_WINDOW_MS || last.clearance != clearance) {
            recentChallengeAttempts[host] = ChallengeAttempt(
                clearance = clearance,
                timestampMs = now,
                count = 1,
            )
            return false
        }
        val nextCount = last.count + 1
        recentChallengeAttempts[host] = last.copy(timestampMs = now, count = nextCount)
        return nextCount >= 2
    }

    private data class ChallengeAttempt(
        val clearance: String,
        val timestampMs: Long,
        val count: Int,
    )

    companion object {
        private const val CLOUDFLARE_RESOLVE_TIMEOUT_MS = 20_000L
        private const val INTERACTIVE_RETRY_WINDOW_MS = 10 * 60 * 1000L
        private val recentChallengeAttempts = ConcurrentHashMap<String, ChallengeAttempt>()
        private val MIHON_COMPAT_INTERCEPTOR_NAMES = setOf(
            "UncaughtExceptionInterceptor",
            "UserAgentInterceptor",
            "CloudflareInterceptor",
        )
    }
}
