// Ported and adapted from Kototoro at dec0ef781644245f6937dc1cafc8ca84963fe08e.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package eu.kanade.tachiyomi.source.model

/**
 * Additive refresh context used by Tachiyomi-ABI forks. Kept in the host because
 * eu.kanade.tachiyomi.source.model.* is parent-owned by the extension ClassLoader policy.
 */
data class RefreshContext(
    val mangaId: Long,
    val existingChapters: List<SChapter>,
    val lastFetchTime: Long,
    val forceRefresh: Boolean = false,
)
