// Adapted from Kototoro's Tachiyomi ABI host surface at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import rx.Observable

/** Mihon-compatible catalogue source interface. */
interface CatalogueSource : Source {

    /** Keiyoushi/TachiyomiX v1.6 related-manga contract. */
    val supportsRelatedMangas: Boolean
        get() = false

    val disableRelatedMangasBySearch: Boolean
        get() = false

    val disableRelatedMangas: Boolean
        get() = false

    suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = emptyList()

    override val lang: String
    override val supportsLatest: Boolean

    @Suppress("DEPRECATION")
    override suspend fun getPopularManga(page: Int): MangasPage {
        return fetchPopularManga(page).toBlocking().first()
    }

    @Suppress("DEPRECATION")
    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        return fetchSearchManga(page, query, filters).toBlocking().first()
    }

    @Suppress("DEPRECATION")
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        return fetchLatestUpdates(page).toBlocking().first()
    }

    override fun getFilterList(): FilterList

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPopularManga"))
    fun fetchPopularManga(page: Int): Observable<MangasPage> =
        throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getSearchManga"))
    fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getLatestUpdates"))
    fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
        throw IllegalStateException("Not used")
}
