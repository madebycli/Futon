// Ported and adapted from Kototoro at dec0ef781644245f6937dc1cafc8ca84963fe08e.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.RefreshContext
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import rx.Observable

/** Complete parent-owned Tachiyomi/Mihon source host ABI. */
interface Source {

    val id: Long
    val name: String

    val lang: String
        get() = ""

    fun isNovelSource(): Boolean = false

    val supportsLatest: Boolean
        get() = false

    fun getFilterList(): FilterList = FilterList()

    suspend fun getPopularManga(page: Int): MangasPage =
        throw IllegalStateException("Not used")

    suspend fun getLatestUpdates(page: Int): MangasPage =
        throw IllegalStateException("Not used")

    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        throw IllegalStateException("Not used")

    @Suppress("DEPRECATION")
    suspend fun getMangaDetails(manga: SManga): SManga =
        fetchMangaDetails(manga).toBlocking().first()

    @Suppress("DEPRECATION")
    suspend fun getChapterList(manga: SManga): List<SChapter> =
        fetchChapterList(manga).toBlocking().first()

    @Deprecated(
        "Fork compatibility API superseded by getMangaUpdate",
        ReplaceWith("getMangaUpdate"),
    )
    suspend fun getChapterList(manga: SManga, context: RefreshContext): List<SChapter> =
        getChapterList(manga)

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
    suspend fun getPageList(chapter: SChapter): List<Page> =
        fetchPageList(chapter).toBlocking().first()

    suspend fun fetchPageText(page: Page): String =
        throw UnsupportedOperationException("Not a novel source")

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
