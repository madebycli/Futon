package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Mihon-compatible safety interceptor.
 *
 * OkHttp only treats [IOException] as a normal request failure. Extensions can throw
 * unchecked exceptions from later interceptors, so convert those failures to IOExceptions
 * instead of letting them escape the dispatcher and crash the app.
 *
 * This interceptor must be installed before all other application interceptors.
 */
class UncaughtExceptionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return try {
            chain.proceed(chain.request())
        } catch (e: Exception) {
            if (e is IOException) throw e
            throw IOException(e.message, e)
        }
    }
}
