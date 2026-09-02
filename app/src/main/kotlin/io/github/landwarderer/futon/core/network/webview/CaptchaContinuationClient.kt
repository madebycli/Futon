package io.github.landwarderer.futon.core.network.webview

import android.graphics.Bitmap
import android.webkit.WebView
import io.github.landwarderer.futon.core.network.cookies.MutableCookieJar
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import kotlin.coroutines.Continuation

/**
 * Waits for Chromium/WebView to obtain a fresh Cloudflare clearance cookie.
 *
 * Keep this intentionally close to Usagi's implementation. In particular, do not proxy
 * WebView resource requests through a separate OkHttpClient: Cloudflare managed challenges
 * and Turnstile depend on the browser's own networking, JS, cookies and browser fingerprint.
 */
class CaptchaContinuationClient(
    private val cookieJar: MutableCookieJar,
    private val targetUrl: String,
    continuation: Continuation<Unit>,
) : ContinuationResumeWebViewClient(continuation) {

    private val oldClearance = CloudFlareHelper.getClearanceCookie(cookieJar, targetUrl)

    override fun onPageFinished(view: WebView?, url: String?) = Unit

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        checkClearance(view)
    }

    private fun checkClearance(view: WebView?) {
        val clearance = CloudFlareHelper.getClearanceCookie(cookieJar, targetUrl)
        if (clearance != null && clearance != oldClearance) {
            resumeContinuation(view)
        }
    }
}
