package eu.kanade.tachiyomi.network.interceptor

import io.github.landwarderer.futon.core.network.CloudFlareInterceptor as FutonCloudFlareInterceptor
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Mihon-compatible Cloudflare interceptor name backed by Futon's existing detector.
 *
 * Keiyoushi sources inspect the default client's application interceptors by simple class
 * name and may remove/reorder this interceptor. Keeping the functional Cloudflare handler
 * behind the expected Mihon class therefore preserves both compatibility and Futon's
 * existing challenge handling.
 */
class CloudflareInterceptor(
    private val delegate: Interceptor = FutonCloudFlareInterceptor(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = delegate.intercept(chain)
}
