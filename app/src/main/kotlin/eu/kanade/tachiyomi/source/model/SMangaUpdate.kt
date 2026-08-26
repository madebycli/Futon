package eu.kanade.tachiyomi.source.model

/**
 * Combined manga metadata/chapter update introduced by extensions-lib 1.6.
 *
 * Current Keiyoushi sources use this API so a host can request details and chapters in a
 * single source-controlled operation. Keep this host model binary-compatible with the
 * extensions-lib constructor/getters used by extension APKs.
 */
data class SMangaUpdate(
    val manga: SManga,
    val chapters: List<SChapter>,
)
