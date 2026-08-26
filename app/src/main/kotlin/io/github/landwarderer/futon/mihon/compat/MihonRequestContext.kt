// Ported and adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon.compat

import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

/** Carries the active Mihon source through extension calls so nested HTTP requests recover it. */
object MihonRequestContext {
    private val currentSource = ThreadLocal<ContentSource?>()
    private val registeredSources = ConcurrentHashMap<String, ContentSource>()

    fun currentSource(): ContentSource? = currentSource.get()

    /** Host lookup is only a hint. SourceRequestContext still validates allowed origins. */
    fun sourceForHost(host: String): ContentSource? = registeredSources[host.lowercase()]

    fun registerSource(source: ContentSource) {
        val httpSource = (source as? MihonMangaSource)?.catalogueSource as? HttpSource ?: return
        val host = httpSource.baseUrl.toHttpUrlOrNull()?.host?.lowercase() ?: return
        registeredSources[host] = source
    }

    fun <T> withSourceBlocking(source: ContentSource, block: () -> T): T {
        registerSource(source)
        val previous = currentSource.get()
        currentSource.set(source)
        return try {
            block()
        } finally {
            if (previous == null) currentSource.remove() else currentSource.set(previous)
        }
    }

    suspend fun <T> withSource(source: ContentSource, block: suspend () -> T): T {
        registerSource(source)
        return withContext(currentSource.asContextElement(source)) { block() }
    }
}
