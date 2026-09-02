package io.github.landwarderer.futon.core.parser

import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.core.exceptions.UnsupportedSourceException
import io.github.landwarderer.futon.mihon.MihonExtensionManager
import io.github.landwarderer.futon.mihon.MihonMangaRepository
import io.github.landwarderer.futon.mihon.fetchNativePageResponse
import io.github.landwarderer.futon.mihon.state.MihonSnapshotPersistence
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder

/**
 * Repository used only for a Mihon source restored from Futon's database before the asynchronous
 * extension scan has finished. Native parser repositories never pass through this class.
 */
internal class AwaitingMihonMangaRepository(
    source: MangaSource,
    private val extensionManager: MihonExtensionManager,
    private val cache: MemoryContentCache,
    private val snapshotPersistence: MihonSnapshotPersistence,
) : EmptyMangaRepository(source) {

    @Volatile
    private var resolved: MihonMangaRepository? = null
    private var pendingSortOrder: SortOrder = SortOrder.POPULARITY

    override val sortOrders: Set<SortOrder>
        get() = resolveNow()?.sortOrders ?: super.sortOrders

    override var defaultSortOrder: SortOrder
        get() = resolveNow()?.defaultSortOrder ?: pendingSortOrder
        set(value) {
            pendingSortOrder = value
            resolveNow()?.defaultSortOrder = value
        }

    override val filterCapabilities: MangaListFilterCapabilities
        get() = resolveNow()?.filterCapabilities ?: super.filterCapabilities

    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> =
        delegate().getList(offset, order, filter)

    override suspend fun getDetails(manga: Manga): Manga = delegate().getDetails(manga)

    override suspend fun getPages(chapter: MangaChapter): List<MangaPage> = delegate().getPages(chapter)

    override suspend fun getPageUrl(page: MangaPage): String = delegate().getPageUrl(page)

    override suspend fun getFilterOptions(): MangaListFilterOptions = delegate().getFilterOptions()

    override suspend fun getRelated(seed: Manga): List<Manga> = delegate().getRelated(seed)

    override fun imageRequestClient(): OkHttpClient? = resolveNow()?.getImageClient()

    override fun buildPageRequest(pageUrl: String, page: MangaPage): Request? =
        resolveNow()?.createPageRequest(pageUrl, page)

    override fun buildCoverRequest(imageUrl: String): Request? = resolveNow()?.createCoverRequest(imageUrl)

    override suspend fun fetchPageResponse(pageUrl: String, page: MangaPage): Response? =
        delegate().fetchNativePageResponse(pageUrl, page)

    private suspend fun delegate(): MihonMangaRepository {
        resolved?.let { return it }
        extensionManager.awaitInitialLoad()
        return resolveNow() ?: throw UnsupportedSourceException(
            "Mihon source is unavailable after the initial extension scan: ${source.name}",
            null,
            source,
        )
    }

    private fun resolveNow(): MihonMangaRepository? {
        resolved?.let { return it }
        val mihonSource = extensionManager.getMihonMangaSourceByName(source.name)
            ?: legacySourceId(source.name)?.let(extensionManager::getMihonMangaSourceById)
            ?: return null
        return MihonMangaRepository(
            source = mihonSource,
            cache = cache,
            snapshotPersistence = snapshotPersistence,
        ).also { repository ->
            repository.defaultSortOrder = pendingSortOrder
            resolved = repository
        }
    }

    private fun legacySourceId(name: String): Long? {
        if (!name.startsWith("mihon:", ignoreCase = true)) return null
        return name.substringAfter(':').toLongOrNull()
    }
}
