// Ported and adapted from Kototoro at f4f37a5b7290da05c10b9325912f2a37ebeff0f9.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.core.network.webview

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.util.Predicate
import io.github.landwarderer.futon.core.network.cookies.MutableCookieJar
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.Request
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Fresh off-screen Cloudflare solver used by the Mihon extension network stack.
 *
 * The request URL and safe request headers are loaded in a new WebView using the same user agent
 * as OkHttp. The WebView shares Futon's Android cookie store, so a newly issued cf_clearance can
 * be reused when the extension retries its original request. No Activity is opened by this class.
 */
class WebViewClearanceSolver(
    context: Context,
    private val cookieJar: CookieJar,
) {
    private val appContext = context.applicationContext
    private val executor = ContextCompat.getMainExecutor(appContext)

    suspend fun solve(request: Request): Boolean {
        val url = request.url.toString()
        val oldClearance = CloudFlareHelper.getClearanceCookie(cookieJar, url)
        removeClearance(request)
        val headers = parseHeaders(request.headers)

        return withTimeoutOrNull(WAIT_TIMEOUT_MS) {
            var session: SolveSession? = null
            try {
                suspendCancellableCoroutine { continuation ->
                    val created = SolveSession(url, oldClearance, request, headers) { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                    session = created
                    continuation.invokeOnCancellation { created.destroy() }
                    created.start()
                }
            } finally {
                session?.destroy()
            }
        } ?: false
    }

    private fun removeClearance(request: Request) {
        (cookieJar as? MutableCookieJar)?.removeCookies(
            request.url,
            Predicate { it.name in CLOUDFLARE_COOKIE_NAMES },
        )
    }

    private fun createWebView(request: Request): WebView = WebView(appContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        request.header("User-Agent")?.let { settings.userAgentString = it }
    }

    private fun parseHeaders(headers: Headers): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (i in 0 until headers.size) {
            val name = headers.name(i)
            val value = headers.value(i)
            if (isRequestHeaderSafe(name, value)) result.putIfAbsent(name, value)
        }
        return result
    }

    private inner class SolveSession(
        private val url: String,
        private val oldClearance: String?,
        private val request: Request,
        private val headers: Map<String, String>,
        private val onSettled: (Boolean) -> Unit,
    ) {
        @Volatile
        private var webView: WebView? = null

        @Volatile
        private var challengeFound = false

        private val settled = AtomicBoolean(false)
        private val destroyed = AtomicBoolean(false)

        fun start() {
            executor.execute {
                if (destroyed.get()) return@execute
                val created = try {
                    createWebView(request)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "createWebView failed: $url", e)
                    settle(false)
                    return@execute
                }
                webView = created
                if (destroyed.get()) {
                    destroyWebView(created)
                    return@execute
                }
                created.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        if (hasNewClearance()) {
                            settle(true)
                            return
                        }
                        if (finishedUrl == url && !challengeFound) settle(false)
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        if (request?.isForMainFrame != true) return
                        if (errorResponse?.statusCode in ERROR_CODES) {
                            challengeFound = true
                        } else {
                            settle(false)
                        }
                    }
                }
                created.loadUrl(url, headers)
            }
        }

        private fun hasNewClearance(): Boolean {
            val current = CloudFlareHelper.getClearanceCookie(cookieJar, url)
            return current != null && current != oldClearance
        }

        fun destroy() {
            if (!destroyed.compareAndSet(false, true)) return
            executor.execute { webView?.let(::destroyWebView) }
        }

        private fun settle(result: Boolean) {
            if (settled.compareAndSet(false, true)) onSettled(result)
        }
    }

    private fun destroyWebView(webView: WebView) {
        runCatching {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    companion object {
        internal const val WAIT_TIMEOUT_MS = 30_000L
        private const val TAG = "WebViewClearanceSolver"
        private val ERROR_CODES = setOf(403, 503)
        private val CLOUDFLARE_COOKIE_NAMES = setOf("cf_clearance")
        private val UNSAFE_HEADER_NAMES = setOf(
            "content-length", "host", "trailer", "te", "upgrade",
            "cookie2", "keep-alive", "transfer-encoding", "set-cookie",
        )

        internal fun isRequestHeaderSafe(rawName: String, rawValue: String): Boolean {
            val name = rawName.lowercase(Locale.ENGLISH)
            val value = rawValue.lowercase(Locale.ENGLISH)
            if (name in UNSAFE_HEADER_NAMES || name.startsWith("proxy-")) return false
            if (name == "connection" && value == "upgrade") return false
            return true
        }
    }
}
