package io.github.landwarderer.futon.mihon.state

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MihonSnapshotPersistenceTest {

    @Test
    fun mangaAndChapterSnapshotsSurviveStoreRecreation() {
        val snapshotFile = newSnapshotFile()
        val writer = MihonSnapshotPersistence(snapshotFile)

        val manga = mangaWithMetadata()
        val chapter = chapterWithMetadata()
        writer.putManga(SOURCE_ID, manga)
        writer.putChapter(SOURCE_ID, chapter)

        manga.title = "mutated after persist"
        manga.memo = buildJsonObject { put("token", "mutated") }
        chapter.name = "mutated after persist"
        chapter.memo = buildJsonObject { put("path", "mutated") }

        val reader = MihonSnapshotPersistence(snapshotFile)
        val restoredManga = requireNotNull(reader.getManga(SOURCE_ID, MANGA_URL))
        val restoredChapter = requireNotNull(reader.getChapter(SOURCE_ID, CHAPTER_URL))

        assertEquals("Persisted manga", restoredManga.title)
        assertEquals("Author", restoredManga.author)
        assertEquals("Artist", restoredManga.artist)
        assertEquals("Persisted description", restoredManga.description)
        assertEquals("Legacy genre string", restoredManga.genre)
        assertEquals(SManga.COMPLETED, restoredManga.status)
        assertEquals("https://example.test/cover.jpg", restoredManga.thumbnail_url)
        assertEquals(UpdateStrategy.ONLY_FETCH_ONCE, restoredManga.update_strategy)
        assertTrue(restoredManga.initialized)
        assertEquals(listOf("Action", "Drama"), restoredManga.genres)
        assertEquals(listOf("Alt title"), restoredManga.altTitles)
        assertEquals("https://example.test/banner.jpg", restoredManga.banner)
        assertEquals(SManga.ContentRating.SUGGESTIVE, restoredManga.contentRating)
        assertEquals(87, restoredManga.score)
        assertEquals(SManga.ReadingMode.RIGHT_TO_LEFT, restoredManga.readingMode)
        assertEquals("persisted-token", restoredManga.memo["token"]?.toString()?.trim('"'))

        assertEquals("Persisted chapter", restoredChapter.name)
        assertEquals(1_717_171_717L, restoredChapter.date_upload)
        assertEquals(12.5f, restoredChapter.chapter_number)
        assertEquals("Group", restoredChapter.scanlator)
        assertEquals("12.5", restoredChapter.number)
        assertEquals("3", restoredChapter.volume)
        assertEquals(listOf("Group", "Second Group"), restoredChapter.scanlators)
        assertEquals("Extension note", restoredChapter.note)
        assertEquals("private/chapter/path", restoredChapter.memo["path"]?.toString()?.trim('"'))
        assertTrue(restoredChapter.locked)
        assertFalse(restoredChapter.read)
        assertEquals(4, restoredChapter.last_page_read)
    }

    @Test
    fun sourceIdsIsolateIdenticalUrlsOnDisk() {
        val snapshotFile = newSnapshotFile()
        val writer = MihonSnapshotPersistence(snapshotFile)

        writer.putManga(SOURCE_ID, mangaWithMetadata("source-a"))
        writer.putManga(SOURCE_ID + 1L, mangaWithMetadata("source-b"))
        writer.putChapter(SOURCE_ID, chapterWithMetadata("chapter-a"))
        writer.putChapter(SOURCE_ID + 1L, chapterWithMetadata("chapter-b"))

        val reader = MihonSnapshotPersistence(snapshotFile)

        assertEquals(
            "source-a",
            reader.getManga(SOURCE_ID, MANGA_URL)?.memo?.get("token")?.toString()?.trim('"'),
        )
        assertEquals(
            "source-b",
            reader.getManga(SOURCE_ID + 1L, MANGA_URL)?.memo?.get("token")?.toString()?.trim('"'),
        )
        assertEquals(
            "chapter-a",
            reader.getChapter(SOURCE_ID, CHAPTER_URL)?.memo?.get("path")?.toString()?.trim('"'),
        )
        assertEquals(
            "chapter-b",
            reader.getChapter(SOURCE_ID + 1L, CHAPTER_URL)?.memo?.get("path")?.toString()?.trim('"'),
        )
    }

    @Test
    fun corruptSnapshotFileFallsBackToCacheMiss() {
        val snapshotFile = newSnapshotFile().apply {
            parentFile?.mkdirs()
            writeText("{ definitely-not-valid-json")
        }

        val persistence = MihonSnapshotPersistence(snapshotFile)

        assertNull(persistence.getManga(SOURCE_ID, MANGA_URL))
        assertNull(persistence.getChapter(SOURCE_ID, CHAPTER_URL))

        persistence.putManga(SOURCE_ID, mangaWithMetadata())
        val recovered = MihonSnapshotPersistence(snapshotFile)
        assertEquals("Persisted manga", recovered.getManga(SOURCE_ID, MANGA_URL)?.title)
    }

    @Test
    fun unsupportedSchemaVersionIsIgnored() {
        val snapshotFile = newSnapshotFile().apply {
            parentFile?.mkdirs()
            writeText(
                """{"schemaVersion":999,"mangas":[],"chapters":[]}""",
            )
        }

        val persistence = MihonSnapshotPersistence(snapshotFile)

        assertNull(persistence.getManga(SOURCE_ID, MANGA_URL))
        assertNull(persistence.getChapter(SOURCE_ID, CHAPTER_URL))
    }

    private fun newSnapshotFile(): File {
        val directory = Files.createTempDirectory("futon-mihon-snapshots").toFile().apply {
            deleteOnExit()
        }
        return File(directory, "snapshots.json").apply { deleteOnExit() }
    }

    private fun mangaWithMetadata(token: String = "persisted-token"): SManga {
        return SManga.create().apply {
            url = MANGA_URL
            title = "Persisted manga"
            artist = "Artist"
            author = "Author"
            description = "Persisted description"
            status = SManga.COMPLETED
            thumbnail_url = "https://example.test/cover.jpg"
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            initialized = true
            genres = listOf("Action", "Drama")
            // Keep the legacy string intentionally distinct from the modern list. The host setter
            // mirrors genres into genre, so set the independent legacy value afterwards.
            genre = "Legacy genre string"
            altTitles = listOf("Alt title")
            banner = "https://example.test/banner.jpg"
            contentRating = SManga.ContentRating.SUGGESTIVE
            score = 87
            readingMode = SManga.ReadingMode.RIGHT_TO_LEFT
            memo = buildJsonObject {
                put("token", token)
            }
        }
    }

    private fun chapterWithMetadata(path: String = "private/chapter/path"): SChapter {
        return SChapter.create().apply {
            url = CHAPTER_URL
            name = "Persisted chapter"
            date_upload = 1_717_171_717L
            number = "12.5"
            chapter_number = 12.5f
            volume = "3"
            scanlators = listOf("Group", "Second Group")
            // Keep the legacy value intentionally distinct from the modern list. The host setter
            // mirrors scanlators into scanlator, so set the independent legacy value afterwards.
            scanlator = "Group"
            note = "Extension note"
            memo = buildJsonObject {
                put("path", path)
            }
            locked = true
            read = false
            last_page_read = 4
        }
    }

    private companion object {
        const val SOURCE_ID = 7_654_321L
        const val MANGA_URL = "/manga/persisted"
        const val CHAPTER_URL = "/chapter/persisted"
    }
}
