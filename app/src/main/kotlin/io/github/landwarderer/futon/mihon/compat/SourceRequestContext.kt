// Ported and adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
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
