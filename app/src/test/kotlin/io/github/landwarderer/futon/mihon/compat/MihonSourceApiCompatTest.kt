package io.github.landwarderer.futon.mihon.compat

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.github.landwarderer.futon.core.parser.MangaRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MihonSourceApiCompatTest {

    @Test
    fun extensionsLib16CombinedUpdateAbiIsExposed() {
        val method = Source::class.java.methods.singleOrNull { it.name == "getMangaUpdate" }
        requireNotNull(method) { "Source.getMangaUpdate must be present for extensions-lib 1.6" }

        assertTrue(method.parameterTypes.any { it.name == "kotlin.coroutines.Continuation" })
        assertEquals(SManga::class.java, SMangaUpdate::class.java.getMethod("getManga").returnType)
        assertEquals(List::class.java, SMangaUpdate::class.java.getMethod("getChapters").returnType)
    }

    @Test
    fun repositoryExposesMihonImageCompatibilityHooks() {
        val methodNames = MangaRepository::class.java.methods.mapTo(mutableSetOf()) { it.name }

        assertTrue(methodNames.contains("imageRequestClient"))
        assertTrue(methodNames.contains("buildPageRequest"))
        assertTrue(methodNames.contains("buildCoverRequest"))
        assertTrue(methodNames.contains("fetchPageResponse"))
    }

    @Test
    fun legacySourceGetsCombinedUpdateFallback() = runTest {
        val original = SManga.create().apply {
            title = "Original"
            url = "/series/example"
        }
        val updated = SManga.create().apply {
            title = "Updated"
            url = original.url
        }
        val chapter = SChapter.create().apply {
            name = "Chapter 1"
            url = "/chapter/1"
        }

        val legacySource = object : Source {
            override val id = 1L
            override val name = "Legacy source"

            override suspend fun getMangaDetails(manga: SManga): SManga = updated

            override suspend fun getChapterList(manga: SManga): List<SChapter> = listOf(chapter)
        }

        val result = legacySource.getMangaUpdate(
            manga = original,
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = true,
        )

        assertSame(updated, result.manga)
        assertEquals(listOf(chapter), result.chapters)
    }

    @Test
    fun combinedUpdateFallbackHonorsFetchFlags() = runTest {
        val original = SManga.create().apply {
            title = "Original"
            url = "/series/example"
        }
        val existingChapter = SChapter.create().apply {
            name = "Existing"
            url = "/chapter/existing"
        }

        val legacySource = object : Source {
            override val id = 2L
            override val name = "Legacy source"

            override suspend fun getMangaDetails(manga: SManga): SManga = error("details must not be fetched")

            override suspend fun getChapterList(manga: SManga): List<SChapter> = error("chapters must not be fetched")
        }

        val detailsOnly = object : Source {
            override val id = 3L
            override val name = "Details source"

            override suspend fun getMangaDetails(manga: SManga): SManga = manga

            override suspend fun getChapterList(manga: SManga): List<SChapter> = error("chapters must not be fetched")
        }

        val result = detailsOnly.getMangaUpdate(
            manga = original,
            chapters = listOf(existingChapter),
            fetchDetails = true,
            fetchChapters = false,
        )

        assertSame(original, result.manga)
        assertEquals(listOf(existingChapter), result.chapters)

        // Keep a no-op reference so this fake source is compiled as an older implementation that
        // relies entirely on the host default instead of declaring getMangaUpdate itself.
        assertEquals("Legacy source", legacySource.name)
    }
}
