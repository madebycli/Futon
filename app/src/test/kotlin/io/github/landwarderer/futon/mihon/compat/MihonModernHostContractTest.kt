package io.github.landwarderer.futon.mihon.compat

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.test.runTest
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import rx.Observable
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class MihonModernHostContractTest {

    @Test
    fun defaultClientPreservesFullHostConfigurationWhileRebuildingInterceptors() {
        val proxy = Proxy(
            Proxy.Type.HTTP,
            InetSocketAddress.createUnresolved("127.0.0.1", 8123),
        )
        val baseClient = OkHttpClient.Builder()
            .proxy(proxy)
            .callTimeout(37, TimeUnit.SECONDS)
            .addInterceptor(BrotliInterceptor)
            .build()

        val helper = MihonNetworkHelper(
            baseClient = baseClient,
            cookieJar = CookieJar.NO_COOKIES,
        )

        assertSame(proxy, helper.client.proxy)
        assertEquals(baseClient.callTimeoutMillis, helper.client.callTimeoutMillis)
        assertFalse(helper.client.interceptors.any { it === BrotliInterceptor })
    }

    @Test
    fun legacyCloudflareClientKeepsBrotliSeparateFromKeiyoushiClient() {
        val helper = MihonNetworkHelper(
            baseClient = OkHttpClient.Builder().build(),
            cookieJar = CookieJar.NO_COOKIES,
        )

        assertFalse(helper.client.networkInterceptors.any { it === BrotliInterceptor })
        assertEquals(
            1,
            helper.cloudflareClient.networkInterceptors.count { it === BrotliInterceptor },
        )
    }

    @Test
    fun httpSourceExposesBaseUrlAsHomeUrl() {
        val source = LegacyPageFetchSource()

        assertEquals("https://example.org", source.getHomeUrl())
    }

    @Test
    fun suspendPageListUsesCustomLegacyFetchWhenRequestHelperIsNotOverridden() = runTest {
        val source = LegacyPageFetchSource()
        val chapter = SChapter.create().apply {
            name = "Chapter"
            url = "/chapter"
        }

        val pages = source.getPageList(chapter)

        assertEquals(listOf("https://images.example.org/1.jpg"), pages.map { it.imageUrl })
        assertEquals(1, source.fetchCalls)
    }

    @Test
    fun suspendChapterListPreservesCustomLegacyFetchEvenWithRequestHelper() = runTest {
        val source = LegacyChapterFetchWithRequestSource()
        val manga = SManga.create().apply {
            title = "Manga"
            url = "/manga"
        }

        val chapters = source.getChapterList(manga)

        assertEquals(listOf("Legacy Chapter"), chapters.map { it.name })
        assertEquals(1, source.fetchCalls)
        assertTrue(source.requestHelperWasNotCalled)
    }

    private abstract class BaseTestHttpSource : HttpSource() {
        override val baseUrl: String = "https://example.org"
        override val lang: String = "en"
        override val name: String = "Compatibility Test"
        override val supportsLatest: Boolean = false

        override fun popularMangaRequest(page: Int): Request = unusedRequest()
        override fun popularMangaParse(response: Response): MangasPage = MangasPage(emptyList(), false)
        override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = unusedRequest()
        override fun searchMangaParse(response: Response): MangasPage = MangasPage(emptyList(), false)
        override fun latestUpdatesRequest(page: Int): Request = unusedRequest()
        override fun latestUpdatesParse(response: Response): MangasPage = MangasPage(emptyList(), false)
        override fun mangaDetailsParse(response: Response): SManga = SManga.create()
        override fun chapterListParse(response: Response): List<SChapter> = emptyList()
        override fun pageListParse(response: Response): List<Page> = emptyList()
        override fun imageUrlParse(response: Response): String = error("unused")
    }

    private class LegacyPageFetchSource : BaseTestHttpSource() {
        var fetchCalls = 0

        override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
            fetchCalls++
            return Observable.just(
                listOf(
                    Page(
                        index = 0,
                        imageUrl = "https://images.example.org/1.jpg",
                    ),
                ),
            )
        }
    }

    private class LegacyChapterFetchWithRequestSource : BaseTestHttpSource() {
        var fetchCalls = 0
        var requestHelperWasNotCalled = true

        override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
            fetchCalls++
            return Observable.just(
                listOf(
                    SChapter.create().apply {
                        name = "Legacy Chapter"
                        url = "/legacy"
                    },
                ),
            )
        }

        override fun chapterListRequest(manga: SManga): Request {
            requestHelperWasNotCalled = false
            error("legacy fetch override must win")
        }
    }

    companion object {
        private fun unusedRequest(): Request = Request.Builder()
            .url("https://example.org/unused")
            .build()
    }
}
