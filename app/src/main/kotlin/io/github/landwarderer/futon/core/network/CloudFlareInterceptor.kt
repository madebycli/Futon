// Request replay context adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.core.network

import android.os.Build
import io.github.landwarderer.futon.core.exceptions.CloudFlareBlockedException
import io.github.landwarderer.futon.core.exceptions.CloudFlareProtectedException
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.IOException
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper

class CloudFlareInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val protectionType = if (Build.VERSION.SDK_INT == 23) {
            try {
                val bodyBytes = response.peekBody(512).bytes()
                val bodyString = String(bodyBytes, Charsets.UTF_8)
                when {
                    bodyString.contains("cf-challenge") ||
                        bodyString.contains("ray_id") ||
                        bodyString.contains("jschl_vc") -> CloudFlareHelper.PROTECTION_CAPTCHA
                    bodyString.contains("cf-error-details") -> CloudFlareHelper.PROTECTION_BLOCKED
                    else -> CloudFlareHelper.PROTECTION_NOT_DETECTED
                }
            } catch (e: Exception) {
                e.printStackTraceDebug("CloudFlareInterceptor")
                CloudFlareHelper.PROTECTION_NOT_DETECTED
            }
        } else {
            try {
                CloudFlareHelper.checkResponseForProtection(response)
            } catch (e: IllegalArgumentException) {
                if (e.message?.contains("Bad position") == true) {
                    CloudFlareHelper.PROTECTION_NOT_DETECTED
                } else {
                    e.printStackTraceDebug("CloudFlareInterceptor")
                    CloudFlareHelper.PROTECTION_NOT_DETECTED
                }
            }
        }

        return when (protectionType) {
            CloudFlareHelper.PROTECTION_BLOCKED -> response.closeThrowing(
                CloudFlareBlockedException(
                    url = request.url.toString(),
                    source = request.tag(MangaSource::class.java),
                ),
            )

            CloudFlareHelper.PROTECTION_CAPTCHA -> response.closeThrowing(
                CloudFlareProtectedException(
                    // Futon's parser runtime does not expose Kototoro's newer
                    // CloudFlareHelper.getBrowserChallengeUrl() helper. Keep the device-proven
                    // Futon challenge URL behavior while retaining the richer replay context.
                    url = request.url.toString(),
                    source = request.tag(MangaSource::class.java),
                    headers = request.headers,
                    method = request.method,
                    body = request.readUtf8BodyOrNull(),
                    contentType = request.header("Content-Type"),
                    originalUrl = request.url.toString(),
                ),
            )

            else -> response
        }
    }

    private fun Request.readUtf8BodyOrNull(): String? {
        val requestBody = body ?: return null
        if (requestBody.isDuplex() || requestBody.isOneShot()) return null
        if (requestBody.contentLength() > MAX_REPLAY_BODY_BYTES) return null
        return runCatching {
            Buffer().use { buffer ->
                requestBody.writeTo(buffer)
                if (buffer.size > MAX_REPLAY_BODY_BYTES) return@runCatching null
                buffer.readUtf8()
            }
        }.getOrNull()
    }

    private fun Response.closeThrowing(error: IOException): Nothing {
        try {
            close()
        } catch (e: Exception) {
            error.addSuppressed(e)
        }
        throw error
    }

    private companion object {
        const val MAX_REPLAY_BODY_BYTES = 2L * 1024L * 1024L
    }
}
