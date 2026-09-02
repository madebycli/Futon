package io.github.landwarderer.futon.mihon.model

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MihonDataConvertersTest {

    @Test
    fun modernNumberWinsOverRepositoryFallback() {
        val chapter = modernChapter(number = "169.5", legacyNumber = -1f)

        assertEquals(169.5f, chapter.resolveContentChapterNumber(fallbackNumber = 170f), 0f)
    }

    @Test
    fun modernNumberKeepsLaterIntegerChapterFromDrifting() {
        val chapter = modernChapter(number = "170", legacyNumber = -1f)

        assertEquals(170f, chapter.resolveContentChapterNumber(fallbackNumber = 171f), 0f)
    }

    @Test
    fun compatModernNumberWithSuffixStillBeatsFallback() {
        val chapter = modernChapter(number = "169.5a", legacyNumber = -1f)

        assertEquals(169.5f, chapter.resolveContentChapterNumber(fallbackNumber = 170f), 0f)
    }

    @Test
    fun invalidModernNumberFallsBackToLegacyNumber() {
        val chapter = modernChapter(number = "special", legacyNumber = 247f)

        assertEquals(247f, chapter.resolveContentChapterNumber(fallbackNumber = 253f), 0f)
    }

    @Test
    fun legacyNumberStillWorksForOlderExtensions() {
        val chapter = modernChapter(number = null, legacyNumber = 247f)

        assertEquals(247f, chapter.resolveContentChapterNumber(fallbackNumber = 253f), 0f)
    }

    @Test
    fun repositoryFallbackIsUsedOnlyWhenExtensionHasNoNumber() {
        val chapter = modernChapter(number = null, legacyNumber = -1f)

        assertEquals(42f, chapter.resolveContentChapterNumber(fallbackNumber = 42f), 0f)
    }

    @Test
    fun chapterCopyFromPreservesModernExtensionState() {
        val memo = JsonObject(mapOf("token" to JsonPrimitive("chapter-state")))
        val original = SChapter.create().apply {
            url = "/chapter/169-5"
            name = "Chapter 169.5"
            date_upload = 123456L
            chapter_number = 169.5f
            scanlator = "Legacy Scanlator"
            number = "169.5"
            volume = "12"
            scanlators = listOf("Group A", "Group B")
            note = "special release"
            this.memo = memo
            locked = true
            read = true
            last_page_read = 8
        }

        val copy = SChapter.create().apply { copyFrom(original) }

        assertEquals(original.url, copy.url)
        assertEquals(original.name, copy.name)
        assertEquals(169.5f, copy.chapter_number, 0f)
        assertEquals("169.5", copy.number)
        assertEquals("12", copy.volume)
        assertEquals(listOf("Group A", "Group B"), copy.scanlators)
        assertEquals("special release", copy.note)
        assertEquals(memo, copy.memo)
        assertTrue(copy.locked)
        assertTrue(copy.read)
        assertEquals(8, copy.last_page_read)
    }

    @Test
    fun mangaCopyPreservesModernExtensionState() {
        val memo = JsonObject(mapOf("token" to JsonPrimitive("manga-state")))
        val original = SManga.create().apply {
            url = "/manga/test"
            title = "Test Manga"
            artist = "Artist"
            author = "Author"
            description = "Description"
            genre = "Action, Drama"
            status = SManga.ONGOING
            thumbnail_url = "https://example.test/cover.jpg"
            initialized = true
            genres = listOf("Action", "Drama")
            altTitles = listOf("Alternative")
            banner = "https://example.test/banner.jpg"
            contentRating = SManga.ContentRating.SUGGESTIVE
            score = 87
            readingMode = SManga.ReadingMode.LONG_STRIP
            this.memo = memo
        }

        val copy = original.copy()

        assertEquals(original.url, copy.url)
        assertEquals(original.title, copy.title)
        assertEquals(listOf("Action", "Drama"), copy.genres)
        assertEquals(listOf("Alternative"), copy.altTitles)
        assertEquals("https://example.test/banner.jpg", copy.banner)
        assertEquals(SManga.ContentRating.SUGGESTIVE, copy.contentRating)
        assertEquals(87, copy.score)
        assertEquals(SManga.ReadingMode.LONG_STRIP, copy.readingMode)
        assertEquals(memo, copy.memo)
        assertTrue(copy.initialized)
        assertFalse(copy.genres.isEmpty())
    }

    private fun modernChapter(number: String?, legacyNumber: Float): SChapter = object : SChapter {
        override var url: String = "/chapter/test"
        override var name: String = "Chapter ${number ?: "unknown"}"
        override var date_upload: Long = 0L
        override var chapter_number: Float = legacyNumber
        override var scanlator: String? = null
        override var number: String? = number
    }
}
