// Request-context recovery adapted from Kototoro at c1128b91140053b081cc7453c87a16f52ab2f12a.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon.compat

import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Restores the active Mihon source tag on requests produced inside extension code.
 *
 * Some extension APIs, especially custom image paths, construct a fresh Request without carrying
 * Futon's tag. Kototoro recovers the source from its request context before Cloudflare handling.
 * Keep the same invariant here so image/CDN requests still retain source-aware cookies and
 * interactive challenge routing.
 */
class MihonSourceContextInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.tag(ContentSource::class.java) != null) {
            return chain.proceed(request)
        }

        val source = MihonRequestContext.currentSource()
            ?: MihonRequestContext.sourceForHost(request.url.host)
            ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .tag(ContentSource::class.java, source)
                .build(),
        )
    }
}
