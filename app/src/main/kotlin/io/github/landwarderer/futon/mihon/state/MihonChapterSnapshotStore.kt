package io.github.landwarderer.futon.mihon.state

import androidx.collection.LruCache
import eu.kanade.tachiyomi.source.model.SChapter
import io.github.landwarderer.futon.mihon.model.snapshot

/**
 * Process-local bounded state for extension-provided chapter metadata.
 *
 * The key deliberately contains the Mihon source ID and the exact chapter URL. No URL
 * normalization or Futon chapter ID is used here because extensions may depend on their
 * original source-specific URL and metadata.
 */
internal data class ChapterSnapshotKey(
    val sourceId: Long,
    val chapterUrl: String,
)

internal object MihonChapterSnapshotStore {

    private const val MAX_SNAPSHOTS = 500
    private val lock = Any()
    private val snapshots = LruCache<ChapterSnapshotKey, SChapter>(MAX_SNAPSHOTS)

    fun put(sourceId: Long, chapterUrl: String, chapter: SChapter) {
        if (chapterUrl.isBlank()) return
        val key = ChapterSnapshotKey(sourceId, chapterUrl)
        val copy = chapter.snapshot()
        synchronized(lock) {
            snapshots.put(key, copy)
        }
    }

    fun get(sourceId: Long, chapterUrl: String): SChapter? {
        if (chapterUrl.isBlank()) return null
        val key = ChapterSnapshotKey(sourceId, chapterUrl)
        val copy = synchronized(lock) {
            snapshots.get(key)
        }
        return copy?.snapshot()
    }

    internal fun clearForTests() {
        synchronized(lock) {
            snapshots.evictAll()
        }
    }
}
