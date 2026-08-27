package io.github.landwarderer.futon.mihon.model

import eu.kanade.tachiyomi.source.model.SChapter
import org.junit.Assert.assertEquals
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
    fun legacyNumberStillWorksForOlderExtensions() {
        val chapter = modernChapter(number = null, legacyNumber = 247f)

        assertEquals(247f, chapter.resolveContentChapterNumber(fallbackNumber = 253f), 0f)
    }

    @Test
    fun repositoryFallbackIsUsedOnlyWhenExtensionHasNoNumber() {
        val chapter = modernChapter(number = null, legacyNumber = -1f)

        assertEquals(42f, chapter.resolveContentChapterNumber(fallbackNumber = 42f), 0f)
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
