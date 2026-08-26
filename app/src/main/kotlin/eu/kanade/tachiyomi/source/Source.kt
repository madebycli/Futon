// Adapted from Kototoro's Tachiyomi ABI host surface at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import rx.Observable

/**
 * Mihon/TachiyomiX-compatible source host API used by dynamically loaded extensions.
 */
interface Source {
    val id: Long
    val name: String

    val lang: String
        get() = ""

    /** TachiyomiX 1.6 default surface. */
    val supportsLatest: Boolean
        get() = false

    /** TachiyomiX 1.6 default surface. */
    fun getFilterList(): FilterList = FilterList()

    /** TachiyomiX 1.6 default surface for non-CatalogueSource implementations. */
    suspend fun getPopularManga(page: Int): MangasPage =
        throw IllegalStateException("Not used")

    /** TachiyomiX 1.6 default surface for non-CatalogueSource implementations. */
    suspend fun getLatestUpdates(page: Int): MangasPage =
        throw IllegalStateException("Not used")

    /** TachiyomiX 1.6 default surface for non-CatalogueSource implementations. */
    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        throw IllegalStateException("Not used")

    @Suppress("DEPRECATION")
    suspend fun getMangaDetails(manga: SManga): SManga {
        return fetchMangaDetails(manga).toBlocking().first()
    }

    @Suppress("DEPRECATION")
    suspend fun getChapterList(manga: SManga): List<SChapter> {
        return fetchChapterList(manga).toBlocking().first()
    }

    /**
     * TachiyomiX 1.6 combined update API. Older sources inherit the legacy fallback.
     */
    suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) getMangaDetails(manga) else manga
        val updatedChapters = if (fetchChapters) getChapterList(manga) else chapters
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    @Suppress("DEPRECATION")
    suspend fun getPageList(chapter: SChapter): List<Page> {
        return fetchPageList(chapter).toBlocking().first()
    }

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getMangaDetails"))
    fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getChapterList"))
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPageList"))
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        throw IllegalStateException("Not used")
}
