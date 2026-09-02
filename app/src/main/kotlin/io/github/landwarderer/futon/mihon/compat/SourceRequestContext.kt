// Ported and adapted from Kototoro at dec0ef781644245f6937dc1cafc8ca84963fe08e.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon.compat

import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Immutable source authority attached to one Mihon HTTP request. */
data class SourceRequestContext(
    val source: ContentSource,
    val allowedBrowserOrigins: Set<String> = emptySet(),
) {
    fun allowsBrowserRequest(url: String): Boolean = url.toHttpsOrigin() in allowedBrowserOrigins

    companion object {
        fun from(source: ContentSource): SourceRequestContext {
            val httpSource = (source as? MihonMangaSource)?.catalogueSource as? HttpSource
            val baseOrigin = httpSource?.baseUrl?.toHttpsOrigin()
            return SourceRequestContext(
                source = source,
                allowedBrowserOrigins = baseOrigin?.let(::setOf).orEmpty(),
            )
        }

        /**
         * HttpSource already knows its declared base URL even when the lightweight ContentSource
         * tag cannot be cast back to MihonMangaSource. Keep that authority on the request so the
         * WebView/Cloudflare transport can enforce the same-origin policy without losing context.
         */
        fun from(source: ContentSource, declaredBaseUrl: String): SourceRequestContext = SourceRequestContext(
            source = source,
            allowedBrowserOrigins = declaredBaseUrl.toHttpsOrigin()?.let(::setOf).orEmpty(),
        )
    }
}

private fun String.toHttpsOrigin(): String? {
    val url = toHttpUrlOrNull()?.takeIf { it.scheme == "https" } ?: return null
    return buildString {
        append(url.scheme)
        append("://")
        append(url.host)
        if (url.port != HttpUrl.defaultPort(url.scheme)) append(':').append(url.port)
    }
}
