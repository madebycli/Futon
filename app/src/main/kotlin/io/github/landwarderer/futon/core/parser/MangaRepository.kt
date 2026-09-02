// Slowdown and image-request contracts adapted from Kototoro at c1128b91140053b081cc7453c87a16f52ab2f12a.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.core.parser

import android.content.Context
import androidx.annotation.AnyThread
import androidx.collection.ArrayMap
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.model.LocalMangaSource
import io.github.landwarderer.futon.core.model.MangaSourceInfo
import io.github.landwarderer.futon.core.model.TestMangaSource
import io.github.landwarderer.futon.core.model.UnknownMangaSource
import io.github.landwarderer.futon.core.parser.external.ExternalMangaRepository
import io.github.landwarderer.futon.core.parser.external.ExternalMangaSource
import io.github.landwarderer.futon.local.data.LocalMangaRepository
import io.github.landwarderer.futon.mihon.MihonExtensionManager
import io.github.landwarderer.futon.mihon.MihonMangaRepository
import io.github.landwarderer.futon.mihon.fetchNativePageResponse
import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import io.github.landwarderer.futon.mihon.state.MihonSnapshotPersistence
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

interface MangaRepository {

    val source: MangaSource
    val sortOrders: Set<SortOrder>
    var defaultSortOrder: SortOrder
    val filterCapabilities: MangaListFilterCapabilities

    suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga>
    suspend fun getDetails(manga: Manga): Manga
    suspend fun getPages(chapter: MangaChapter): List<MangaPage>
    suspend fun getPageUrl(page: MangaPage): String
    suspend fun getFilterOptions(): MangaListFilterOptions
    suspend fun getRelated(seed: Manga): List<Manga>

    /**
     * Optional repository-owned image client.
     *
     * Mihon HttpSource implementations frequently install source-specific interceptors, cookies or
     * TLS behavior on their own client. Other repository types keep Futon's shared Manga client.
     */
    fun imageRequestClient(): OkHttpClient? = (this as? MihonMangaRepository)?.getImageClient()

    /**
     * Optional repository-owned page request. Null keeps Futon's generic image GET.
     */
    fun buildPageRequest(pageUrl: String, page: MangaPage): Request? =
        (this as? MihonMangaRepository)?.createPageRequest(pageUrl, page)

    /**
     * Optional repository-owned cover request. Null keeps Futon's generic image GET.
     */
    fun buildCoverRequest(imageUrl: String): Request? =
        (this as? MihonMangaRepository)?.createCoverRequest(imageUrl)

    /**
     * Optional native page execution path.
     *
     * Mihon HttpSource may override getImage(Page) with token refresh, request rewriting or retry
     * behavior that cannot be preserved by merely reconstructing a Request in the host.
     */
    suspend fun fetchPageResponse(pageUrl: String, page: MangaPage): Response? =
        (this as? MihonMangaRepository)?.fetchNativePageResponse(pageUrl, page)

    /**
     * Whether downloader requests for this repository should respect the configured per-source
     * delay. Mihon extensions return true by default, matching Kototoro's generic repository
     * behavior, rather than being skipped just because they are not ParserMangaRepository.
     */
    fun isSlowdownEnabled(): Boolean = source != LocalMangaSource && source != TestMangaSource

    suspend fun find(manga: Manga): Manga? {
        val list = getList(0, SortOrder.RELEVANCE, MangaListFilter(query = manga.title))
        return list.find { x -> x.id == manga.id }
    }

    @Singleton
    class Factory @Inject constructor(
        @ApplicationContext private val context: Context,
        private val localMangaRepository: LocalMangaRepository,
        private val loaderContext: MangaLoaderContext,
        private val contentCache: MemoryContentCache,
        private val mirrorSwitcher: MirrorSwitcher,
        private val mihonExtensionManager: MihonExtensionManager,
        private val mihonSnapshotPersistence: MihonSnapshotPersistence,
    ) {
        private val cache = ArrayMap<MangaSource, WeakReference<MangaRepository>>()

        @AnyThread
        fun create(source: MangaSource): MangaRepository {
            when (source) {
                is MangaSourceInfo -> return create(source.mangaSource)
                LocalMangaSource -> return localMangaRepository
                UnknownMangaSource -> return EmptyMangaRepository(source)
            }
            cache[source]?.get()?.let { return it }
            return synchronized(cache) {
                cache[source]?.get()?.let { return it }
                val repository = createRepository(source)
                if (repository != null) {
                    cache[source] = WeakReference(repository)
                    repository
                } else {
                    EmptyMangaRepository(source)
                }
            }
        }

        private fun createRepository(source: MangaSource): MangaRepository? = when (source) {
            is MangaParserSource -> ParserMangaRepository(
                parser = loaderContext.newParserInstance(source),
                cache = contentCache,
                mirrorSwitcher = mirrorSwitcher,
            )

            TestMangaSource -> TestMangaRepository(
                loaderContext = loaderContext,
                cache = contentCache,
            )

            is ExternalMangaSource -> if (source.isAvailable(context)) {
                ExternalMangaRepository(
                    contentResolver = context.contentResolver,
                    source = source,
                    cache = contentCache,
                )
            } else {
                EmptyMangaRepository(source)
            }

            is MihonMangaSource -> MihonMangaRepository(
                source = source,
                cache = contentCache,
                snapshotPersistence = mihonSnapshotPersistence,
            )

            else -> {
                if (source.name.startsWith("mihon:", ignoreCase = true) || source.name.startsWith("MIHON_")) {
                    val loadedSource = mihonExtensionManager.getMihonMangaSourceByName(source.name)
                    loadedSource?.let {
                        MihonMangaRepository(
                            source = it,
                            cache = contentCache,
                            snapshotPersistence = mihonSnapshotPersistence,
                        )
                    } ?: AwaitingMihonMangaRepository(
                        source = source,
                        extensionManager = mihonExtensionManager,
                        cache = contentCache,
                        snapshotPersistence = mihonSnapshotPersistence,
                    )
                } else {
                    null
                }
            }
        }
    }
}
