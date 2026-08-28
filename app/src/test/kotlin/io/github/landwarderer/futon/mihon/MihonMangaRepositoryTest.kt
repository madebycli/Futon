package io.github.landwarderer.futon.mihon

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import io.github.landwarderer.futon.core.cache.MemoryContentCache
import io.github.landwarderer.futon.mihon.model.MihonMangaSource
import io.github.landwarderer.futon.mihon.model.toDomainContent
import io.github.landwarderer.futon.mihon.model.toManga
import io.github.landwarderer.futon.mihon.state.MihonChapterSnapshotStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

private fun snapshotPath(chapter: SChapter?): String? {
    return chapter?.memo?.get("path")?.toString()?.trim('"')
}

class MihonMangaRepositoryTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
        MihonChapterSnapshotStore.clearForTests()
    }

    @After
    fun tearDown() {
        MihonChapterSnapshotStore.clearForTests()
        Dispatchers.resetMain()
    }

    @Test
    fun chapterSnapshotSurvivesRepositoryInstanceChange() = runTest {
        val chapter = chapterWithMetadata()
        val source = SnapshotCatalogueSource(SOURCE_ID, chapter)
        val sourceWrapperA = MihonMangaSource(source, "test.snapshot.a")
        val repositoryA = MihonMangaRepository(sourceWrapperA, memoryCache())

        val initialManga = SManga.create().apply {
            url = MANGA_URL
            title = "Snapshot test manga"
            initialized = true
        }.toDomainContent(sourceWrapperA).toManga()

        val details = repositoryA.getDetails(initialManga)
        val returnedChapter = requireNotNull(details.chapters).single()

        chapter.memo = buildJsonObject {
            put("path", "mutated-after-details")
        }

        val sourceWrapperB = MihonMangaSource(source, "test.snapshot.b")
        val repositoryB = MihonMangaRepository(sourceWrapperB, memoryCache())
        val pages = repositoryB.getPages(returnedChapter)

        assertEquals(1, pages.size)
        assertEquals("private/chapter/path", source.observedPath)
        assertTrue(source.receivedPageRequest)
    }

    @Test
    fun sourceIdsIsolateIdenticalChapterUrls() {
        val chapterA = chapterWithMetadata("source-a")
        val chapterB = chapterWithMetadata("source-b")

        MihonChapterSnapshotStore.put(SOURCE_ID, CHAPTER_URL, chapterA)
        MihonChapterSnapshotStore.put(SOURCE_ID + 1L, CHAPTER_URL, chapterB)

        assertEquals("source-a", snapshotPath(MihonChapterSnapshotStore.get(SOURCE_ID, CHAPTER_URL)))
        assertEquals("source-b", snapshotPath(MihonChapterSnapshotStore.get(SOURCE_ID + 1L, CHAPTER_URL)))
    }

    @Test
    fun returnedChapterSnapshotIsDefensiveCopy() {
        val original = chapterWithMetadata()
        MihonChapterSnapshotStore.put(SOURCE_ID, CHAPTER_URL, original)

        val returned = requireNotNull(MihonChapterSnapshotStore.get(SOURCE_ID, CHAPTER_URL))
        returned.name = "mutated"
        returned.memo = buildJsonObject {
            put("path", "mutated")
        }

        val reread = requireNotNull(MihonChapterSnapshotStore.get(SOURCE_ID, CHAPTER_URL))
        assertEquals("Original chapter", reread.name)
        assertEquals("private/chapter/path", snapshotPath(reread))
    }

    private fun memoryCache(): MemoryContentCache {
        return mock(MemoryContentCache::class.java)
    }

    private fun chapterWithMetadata(path: String = "private/chapter/path"): SChapter {
        return SChapter.create().apply {
            url = CHAPTER_URL
            name = "Original chapter"
            date_upload = 1_717_171_717L
            chapter_number = 12.5f
            scanlator = "Group"
            number = "12.5"
            volume = "3"
            scanlators = listOf("Group")
            note = "Extension note"
            memo = buildJsonObject {
                put("path", path)
            }
            locked = true
            read = false
            last_page_read = 4
        }
    }

    private class SnapshotCatalogueSource(
        override val id: Long,
        private val chapter: SChapter,
    ) : CatalogueSource {
        override val name: String = "Snapshot test source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = false
        var observedPath: String? = null
        var receivedPageRequest: Boolean = false

        override fun getFilterList(): FilterList = FilterList()

        override suspend fun getPopularManga(page: Int): MangasPage {
            return MangasPage(emptyList(), false)
        }

        override suspend fun getMangaDetails(manga: SManga): SManga = manga

        override suspend fun getChapterList(manga: SManga): List<SChapter> = listOf(chapter)

        override suspend fun getPageList(chapter: SChapter): List<Page> {
            receivedPageRequest = true
            observedPath = snapshotPath(chapter)
            assertEquals("private/chapter/path", observedPath)
            return listOf(
                Page(
                    index = 0,
                    url = "/page/1",
                    imageUrl = "https://images.example.test/page-1.jpg",
                ),
            )
        }
    }

    private companion object {
        const val SOURCE_ID = 7_654_321L
        const val MANGA_URL = "/manga/snapshot-test"
        const val CHAPTER_URL = "/chapter/snapshot-test"
    }
}
