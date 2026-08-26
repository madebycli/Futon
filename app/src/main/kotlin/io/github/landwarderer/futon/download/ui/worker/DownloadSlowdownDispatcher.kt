// Ported and adapted from Kototoro at e036c5940af6b849c055ab46d73c0ec4896276f7.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.download.ui.worker

import android.content.Context
import android.os.SystemClock
import androidx.collection.MutableObjectLongMap
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.core.parser.MangaRepository
import kotlinx.coroutines.delay
import org.koitharu.kotatsu.parsers.model.MangaSource
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes request starts per source using a user-configurable delay. */
@Singleton
class DownloadSlowdownDispatcher @Inject constructor(
    private val mangaRepositoryFactory: MangaRepository.Factory,
    @ApplicationContext context: Context,
) {
    private val timeMap = MutableObjectLongMap<MangaSource>()
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    suspend fun delay(source: MangaSource) {
        val repo = mangaRepositoryFactory.create(source)
        if (!repo.isSlowdownEnabled()) return

        val delayMs = prefs.getString(KEY_DOWNLOAD_REQUEST_DELAY, DEFAULT_DELAY_MS.toString())
            ?.toLongOrNull()
            ?.coerceIn(0L, MAX_DELAY_MS)
            ?: DEFAULT_DELAY_MS
        if (delayMs <= 0L) return

        val now = SystemClock.elapsedRealtime()
        val lastRequest = synchronized(timeMap) {
            val res = timeMap.getOrDefault(source, 0L)
            timeMap[source] = now
            res
        }
        if (lastRequest != 0L) {
            val waitMs = lastRequest + delayMs - SystemClock.elapsedRealtime()
            if (waitMs > 0L) delay(waitMs)
        }
    }

    companion object {
        const val KEY_DOWNLOAD_REQUEST_DELAY = "download_request_delay"
        const val DEFAULT_DELAY_MS = 1_600L
        const val MAX_DELAY_MS = 60_000L
    }
}
