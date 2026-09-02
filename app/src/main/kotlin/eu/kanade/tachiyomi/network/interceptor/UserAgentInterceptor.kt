package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Supplies Mihon's default User-Agent only when a request does not already provide one.
 * Source-specific User-Agent headers therefore remain authoritative.
 */
class UserAgentInterceptor(
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.header("User-Agent").isNullOrEmpty()) {
            return chain.proceed(request)
        }

        val requestWithUserAgent = request.newBuilder()
            .header("User-Agent", defaultUserAgentProvider())
            .build()
        return chain.proceed(requestWithUserAgent)
    }
}
