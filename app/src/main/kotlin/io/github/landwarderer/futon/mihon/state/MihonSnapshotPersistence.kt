package io.github.landwarderer.futon.mihon.state

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Durable, host-owned backing store for Mihon model snapshots.
 *
 * Extension implementation classes are never serialized. Only values copied into Futon's own
 * SManga/SChapter implementations are encoded. The file lives below noBackupFilesDir so a stale
 * extension snapshot is never restored onto another installation through Android backup.
 */
@Singleton
class MihonSnapshotPersistence {

    companion object {
        private const val FILE_NAME = "mihon-model-snapshots-v1.json"
        private const val SCHEMA_VERSION = 1
        private const val MAX_MANGA_SNAPSHOTS = 500
        private const val MAX_CHAPTER_SNAPSHOTS = 1_000
    }

    private val snapshotFile: File
    private val lock = Any()
    private var loaded = false
    private val mangas = LinkedHashMap<MangaSnapshotKey, MangaSnapshotDto>(128, 0.75f, true)
    private val chapters = LinkedHashMap<ChapterSnapshotKey, ChapterSnapshotDto>(512, 0.75f, true)

    @Inject
    constructor(@ApplicationContext context: Context) {
        snapshotFile = File(context.noBackupFilesDir, FILE_NAME)
    }

    internal constructor(snapshotFile: File) {
        this.snapshotFile = snapshotFile
    }

    fun getManga(sourceId: Long, mangaUrl: String): SManga? {
        if (mangaUrl.isBlank()) return null
        synchronized(lock) {
            ensureLoadedLocked()
            return mangas[MangaSnapshotKey(sourceId, mangaUrl)]?.toModel()
        }
    }

    fun getChapter(sourceId: Long, chapterUrl: String): SChapter? {
        if (chapterUrl.isBlank()) return null
        synchronized(lock) {
            ensureLoadedLocked()
            return chapters[ChapterSnapshotKey(sourceId, chapterUrl)]?.toModel()
        }
    }

    fun putMangas(sourceId: Long, values: Collection<SManga>) {
        synchronized(lock) {
            ensureLoadedLocked()
            values.forEach { manga ->
                val dto = MangaSnapshotDto.from(manga) ?: return@forEach
                mangas[MangaSnapshotKey(sourceId, dto.url)] = dto
            }
            trimLocked(mangas, MAX_MANGA_SNAPSHOTS)
            persistLocked()
        }
    }

    fun putChapters(sourceId: Long, values: Collection<SChapter>) {
        synchronized(lock) {
            ensureLoadedLocked()
            values.forEach { chapter ->
                val dto = ChapterSnapshotDto.from(chapter) ?: return@forEach
                chapters[ChapterSnapshotKey(sourceId, dto.url)] = dto
            }
            trimLocked(chapters, MAX_CHAPTER_SNAPSHOTS)
            persistLocked()
        }
    }

    fun putManga(sourceId: Long, manga: SManga) = putMangas(sourceId, listOf(manga))

    fun putChapter(sourceId: Long, chapter: SChapter) = putChapters(sourceId, listOf(chapter))

    private fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true
        if (!snapshotFile.isFile) return

        try {
            val root = Json.parseToJsonElement(snapshotFile.readText()).jsonObject
            if (root.int("schemaVersion") != SCHEMA_VERSION) return

            root.array("mangas").forEach { element ->
                val entry = element as? JsonObject ?: return@forEach
                val sourceId = entry.long("sourceId") ?: return@forEach
                val dto = MangaSnapshotDto.fromJson(entry) ?: return@forEach
                mangas[MangaSnapshotKey(sourceId, dto.url)] = dto
            }
            root.array("chapters").forEach { element ->
                val entry = element as? JsonObject ?: return@forEach
                val sourceId = entry.long("sourceId") ?: return@forEach
                val dto = ChapterSnapshotDto.fromJson(entry) ?: return@forEach
                chapters[ChapterSnapshotKey(sourceId, dto.url)] = dto
            }
            trimLocked(mangas, MAX_MANGA_SNAPSHOTS)
            trimLocked(chapters, MAX_CHAPTER_SNAPSHOTS)
        } catch (_: Throwable) {
            // A cache file must never be able to break startup or extension loading.
            mangas.clear()
            chapters.clear()
        }
    }

    private fun persistLocked() {
        try {
            snapshotFile.parentFile?.mkdirs()
            val temp = File(snapshotFile.parentFile, "${snapshotFile.name}.tmp")
            val root = JsonObject(
                mapOf(
                    "schemaVersion" to JsonPrimitive(SCHEMA_VERSION),
                    "mangas" to JsonArray(mangas.map { (key, value) -> value.toJson(key.sourceId) }),
                    "chapters" to JsonArray(chapters.map { (key, value) -> value.toJson(key.sourceId) }),
                ),
            )
            FileOutputStream(temp).use { output ->
                output.write(root.toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(
                    temp.toPath(),
                    snapshotFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temp.toPath(),
                    snapshotFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (_: Throwable) {
            // Persistence is a best-effort cache. Runtime snapshots remain valid in memory.
        }
    }

    private fun <K, V> trimLocked(map: LinkedHashMap<K, V>, maxSize: Int) {
        while (map.size > maxSize) {
            val iterator = map.entries.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
        }
    }

    private data class MangaSnapshotKey(val sourceId: Long, val url: String)

    private data class ChapterSnapshotKey(val sourceId: Long, val url: String)

    private data class MangaSnapshotDto(
        val url: String,
        val title: String,
        val artist: String?,
        val author: String?,
        val description: String?,
        val genre: String?,
        val status: Int,
        val thumbnailUrl: String?,
        val updateStrategy: String,
        val initialized: Boolean,
        val genres: List<String>,
        val altTitles: List<String>,
        val banner: String?,
        val contentRating: String,
        val score: Int?,
        val readingMode: String?,
        val memo: JsonObject,
    ) {
        fun toModel(): SManga = SManga.create().also { manga ->
            manga.url = url
            manga.title = title
            manga.artist = artist
            manga.author = author
            manga.description = description
            // Set modern list metadata first because the host implementation mirrors it into the
            // legacy genre string. Reapply the persisted legacy value afterwards so both ABI views
            // survive a restart exactly as the extension supplied them.
            manga.genres = genres.toList()
            manga.genre = genre
            manga.status = status
            manga.thumbnail_url = thumbnailUrl
            manga.update_strategy = runCatching { UpdateStrategy.valueOf(updateStrategy) }
                .getOrDefault(UpdateStrategy.ALWAYS_UPDATE)
            manga.initialized = initialized
            manga.altTitles = altTitles.toList()
            manga.banner = banner
            manga.contentRating = runCatching { SManga.ContentRating.valueOf(contentRating) }
                .getOrDefault(SManga.ContentRating.SAFE)
            manga.score = score
            manga.readingMode = readingMode?.let { value ->
                runCatching { SManga.ReadingMode.valueOf(value) }.getOrNull()
            }
            manga.memo = JsonObject(memo.toMap())
        }

        fun toJson(sourceId: Long): JsonObject = JsonObject(
            mapOf(
                "sourceId" to JsonPrimitive(sourceId),
                "url" to JsonPrimitive(url),
                "title" to JsonPrimitive(title),
                "artist" to artist.json(),
                "author" to author.json(),
                "description" to description.json(),
                "genre" to genre.json(),
                "status" to JsonPrimitive(status),
                "thumbnailUrl" to thumbnailUrl.json(),
                "updateStrategy" to JsonPrimitive(updateStrategy),
                "initialized" to JsonPrimitive(initialized),
                "genres" to genres.jsonArray(),
                "altTitles" to altTitles.jsonArray(),
                "banner" to banner.json(),
                "contentRating" to JsonPrimitive(contentRating),
                "score" to score.json(),
                "readingMode" to readingMode.json(),
                "memo" to memo,
            ),
        )

        companion object {
            fun from(manga: SManga): MangaSnapshotDto? {
                val url = manga.safe("") { this.url }.takeIf(String::isNotBlank) ?: return null
                return MangaSnapshotDto(
                    url = url,
                    title = manga.safe("") { title },
                    artist = manga.safe<String?>(null) { artist },
                    author = manga.safe<String?>(null) { author },
                    description = manga.safe<String?>(null) { description },
                    genre = manga.safe<String?>(null) { genre },
                    status = manga.safe(SManga.UNKNOWN) { status },
                    thumbnailUrl = manga.safe<String?>(null) { thumbnail_url },
                    updateStrategy = manga.safe(UpdateStrategy.ALWAYS_UPDATE) { update_strategy }.name,
                    initialized = manga.safe(false) { initialized },
                    genres = manga.safe(emptyList()) { genres }.toList(),
                    altTitles = manga.safe(emptyList()) { altTitles }.toList(),
                    banner = manga.safe<String?>(null) { banner },
                    contentRating = manga.safe(SManga.ContentRating.SAFE) { contentRating }.name,
                    score = manga.safe<Int?>(null) { score },
                    readingMode = manga.safe<SManga.ReadingMode?>(null) { readingMode }?.name,
                    memo = JsonObject(manga.safe(JsonObject(emptyMap())) { memo }.toMap()),
                )
            }

            fun fromJson(json: JsonObject): MangaSnapshotDto? {
                val url = json.string("url")?.takeIf(String::isNotBlank) ?: return null
                return MangaSnapshotDto(
                    url = url,
                    title = json.string("title").orEmpty(),
                    artist = json.string("artist"),
                    author = json.string("author"),
                    description = json.string("description"),
                    genre = json.string("genre"),
                    status = json.int("status") ?: SManga.UNKNOWN,
                    thumbnailUrl = json.string("thumbnailUrl"),
                    updateStrategy = json.string("updateStrategy") ?: UpdateStrategy.ALWAYS_UPDATE.name,
                    initialized = json.boolean("initialized") ?: false,
                    genres = json.stringList("genres"),
                    altTitles = json.stringList("altTitles"),
                    banner = json.string("banner"),
                    contentRating = json.string("contentRating") ?: SManga.ContentRating.SAFE.name,
                    score = json.int("score"),
                    readingMode = json.string("readingMode"),
                    memo = json["memo"] as? JsonObject ?: JsonObject(emptyMap()),
                )
            }
        }
    }

    private data class ChapterSnapshotDto(
        val url: String,
        val name: String,
        val dateUpload: Long,
        val chapterNumber: Float,
        val scanlator: String?,
        val number: String?,
        val volume: String?,
        val scanlators: List<String>,
        val note: String?,
        val memo: JsonObject,
        val locked: Boolean,
        val read: Boolean,
        val lastPageRead: Int,
    ) {
        fun toModel(): SChapter = SChapter.create().also { chapter ->
            chapter.url = url
            chapter.name = name
            chapter.date_upload = dateUpload
            // number may derive chapter_number and scanlators may derive scanlator. Restore the
            // modern fields first, then reapply the independently persisted legacy values.
            chapter.number = number
            chapter.chapter_number = chapterNumber
            chapter.volume = volume
            chapter.scanlators = scanlators.toList()
            chapter.scanlator = scanlator
            chapter.note = note
            chapter.memo = JsonObject(memo.toMap())
            chapter.locked = locked
            chapter.read = read
            chapter.last_page_read = lastPageRead
        }

        fun toJson(sourceId: Long): JsonObject = JsonObject(
            mapOf(
                "sourceId" to JsonPrimitive(sourceId),
                "url" to JsonPrimitive(url),
                "name" to JsonPrimitive(name),
                "dateUpload" to JsonPrimitive(dateUpload),
                "chapterNumber" to JsonPrimitive(chapterNumber),
                "scanlator" to scanlator.json(),
                "number" to number.json(),
                "volume" to volume.json(),
                "scanlators" to scanlators.jsonArray(),
                "note" to note.json(),
                "memo" to memo,
                "locked" to JsonPrimitive(locked),
                "read" to JsonPrimitive(read),
                "lastPageRead" to JsonPrimitive(lastPageRead),
            ),
        )

        companion object {
            fun from(chapter: SChapter): ChapterSnapshotDto? {
                val url = chapter.safe("") { this.url }.takeIf(String::isNotBlank) ?: return null
                return ChapterSnapshotDto(
                    url = url,
                    name = chapter.safe("") { name },
                    dateUpload = chapter.safe(0L) { date_upload },
                    chapterNumber = chapter.safe(-1f) { chapter_number },
                    scanlator = chapter.safe<String?>(null) { scanlator },
                    number = chapter.safe<String?>(null) { number },
                    volume = chapter.safe<String?>(null) { volume },
                    scanlators = chapter.safe(emptyList()) { scanlators }.toList(),
                    note = chapter.safe<String?>(null) { note },
                    memo = JsonObject(chapter.safe(JsonObject(emptyMap())) { memo }.toMap()),
                    locked = chapter.safe(false) { locked },
                    read = chapter.safe(false) { read },
                    lastPageRead = chapter.safe(0) { last_page_read },
                )
            }

            fun fromJson(json: JsonObject): ChapterSnapshotDto? {
                val url = json.string("url")?.takeIf(String::isNotBlank) ?: return null
                return ChapterSnapshotDto(
                    url = url,
                    name = json.string("name").orEmpty(),
                    dateUpload = json.long("dateUpload") ?: 0L,
                    chapterNumber = json.float("chapterNumber") ?: -1f,
                    scanlator = json.string("scanlator"),
                    number = json.string("number"),
                    volume = json.string("volume"),
                    scanlators = json.stringList("scanlators"),
                    note = json.string("note"),
                    memo = json["memo"] as? JsonObject ?: JsonObject(emptyMap()),
                    locked = json.boolean("locked") ?: false,
                    read = json.boolean("read") ?: false,
                    lastPageRead = json.int("lastPageRead") ?: 0,
                )
            }
        }
    }
}

private fun String?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull
private fun Int?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull
private fun List<String>.jsonArray(): JsonArray = JsonArray(map(::JsonPrimitive))
private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull
private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull
private fun JsonObject.float(name: String): Float? = this[name]?.jsonPrimitive?.floatOrNull
private fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull
private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())
private fun JsonObject.stringList(name: String): List<String> = array(name).mapNotNull {
    (it as? JsonPrimitive)?.contentOrNull
}

private inline fun <T> SManga.safe(defaultValue: T, getter: SManga.() -> T): T = try {
    getter()
} catch (_: UninitializedPropertyAccessException) {
    defaultValue
} catch (_: AbstractMethodError) {
    defaultValue
} catch (_: NoSuchMethodError) {
    defaultValue
}

private inline fun <T> SChapter.safe(defaultValue: T, getter: SChapter.() -> T): T = try {
    getter()
} catch (_: UninitializedPropertyAccessException) {
    defaultValue
} catch (_: AbstractMethodError) {
    defaultValue
} catch (_: NoSuchMethodError) {
    defaultValue
}
