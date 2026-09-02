// Mihon chapter ABI conversion logic adapted from Kototoro at f4f37a5b7290da05c10b9325912f2a37ebeff0f9.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
package io.github.landwarderer.futon.mihon.model

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.toFloatOrNullCompat
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.landwarderer.futon.mihon.parsers.model.Content
import io.github.landwarderer.futon.mihon.parsers.model.ContentChapter
import io.github.landwarderer.futon.mihon.parsers.model.ContentPage
import io.github.landwarderer.futon.mihon.parsers.model.ContentRating
import io.github.landwarderer.futon.mihon.parsers.model.ContentSource
import io.github.landwarderer.futon.mihon.parsers.model.ContentState
import io.github.landwarderer.futon.mihon.parsers.model.ContentTag

/**
 * Convert Mihon SManga to Domain Content.
 */
fun SManga.toDomainContent(
    source: MihonMangaSource,
    chapters: List<ContentChapter>? = null,
    publicUrl: String = "",
): Content {
    // Get baseUrl from source if available to resolve relative URLs
    val baseUrl = (source.catalogueSource as? HttpSource)?.baseUrl ?: ""

    val safeUrl = try { url } catch (_: UninitializedPropertyAccessException) { "" }
    val safeThumbnail = try { thumbnail_url } catch (_: UninitializedPropertyAccessException) { null }
    val absoluteThumbnailUrl = resolveUrl(baseUrl, safeThumbnail)
    val absolutePublicUrl = resolveUrl(baseUrl, safeUrl) ?: safeUrl

    // Safely access lateinit and fork/modern compatibility properties. Futon's host ABI is a
    // deliberate superset of upstream Mihon's source-api so extensions compiled against Mihon,
    // Keiyoushi, TachiyomiX or Kototoro do not lose metadata while crossing the app domain model.
    val safeTitle = try { title } catch (_: UninitializedPropertyAccessException) { null }
    val safeGenres: List<String>? = try {
        genres
    } catch (_: UninitializedPropertyAccessException) {
        null
    } catch (_: AbstractMethodError) {
        null
    } catch (_: NoSuchMethodError) {
        null
    }
    val safeAuthor = try { author } catch (_: UninitializedPropertyAccessException) { null }
    val safeArtist = try { artist } catch (_: UninitializedPropertyAccessException) { null }
    val safeDescription = try { description } catch (_: UninitializedPropertyAccessException) { null }
    val safeStatus = try { status } catch (_: UninitializedPropertyAccessException) { SManga.UNKNOWN }
    val safeAltTitles = try {
        altTitles.toSet()
    } catch (_: AbstractMethodError) {
        emptySet()
    } catch (_: NoSuchMethodError) {
        emptySet()
    }
    val safeBanner = try {
        banner?.let { resolveUrl(baseUrl, it) }
    } catch (_: AbstractMethodError) {
        null
    } catch (_: NoSuchMethodError) {
        null
    }
    val calculatedRating = try {
        val safeScore = score
        when {
            safeScore == null || safeScore <= 0 -> 0f
            safeScore <= 10 -> safeScore / 10f
            safeScore <= 100 -> safeScore / 100f
            else -> 0f
        }
    } catch (_: AbstractMethodError) {
        0f
    } catch (_: NoSuchMethodError) {
        0f
    }

    return Content(
        id = generateContentId(safeUrl, source.name),
        title = safeTitle ?: "Unknown",
        altTitles = safeAltTitles,
        url = safeUrl,
        Url = publicUrl.ifBlank { absolutePublicUrl },
        rating = calculatedRating,
        contentRating = run<ContentRating?> {
            val explicitRating = try {
                when (contentRating) {
                    SManga.ContentRating.SAFE -> ContentRating.SAFE
                    SManga.ContentRating.SUGGESTIVE -> ContentRating.SUGGESTIVE
                    SManga.ContentRating.ADULT -> ContentRating.ADULT
                }
            } catch (_: AbstractMethodError) {
                null
            } catch (_: NoSuchMethodError) {
                null
            }

            if (source.isNsfw) {
                ContentRating.ADULT
            } else if (explicitRating != null) {
                explicitRating
            } else {
                val safeTags = setOf("safe", "all ages", "non-h", "sfw", "非h", "正常向", "全年龄", "全年龄向")
                val isExplicitlySafe = safeGenres?.any { it.lowercase() in safeTags } == true

                val adultGenres = setOf("adult", "hentai", "18+", "nsfw", "mature", "ecchi")
                val isContentNsfw = safeGenres?.any { it.lowercase() in adultGenres } == true

                if (isExplicitlySafe) {
                    ContentRating.SAFE
                } else if (isContentNsfw) {
                    ContentRating.ADULT
                } else {
                    null
                }
            }
        },
        coverUrl = absoluteThumbnailUrl,
        largeCoverUrl = safeBanner ?: absoluteThumbnailUrl,
        tags = safeGenres?.map { genreName: String ->
            ContentTag(
                title = genreName,
                key = genreName.lowercase().replace(" ", "_"),
                source = source,
            )
        }?.toSet() ?: emptySet(),
        state = when (safeStatus) {
            SManga.ONGOING -> ContentState.ONGOING
            SManga.COMPLETED -> ContentState.FINISHED
            SManga.ON_HIATUS -> ContentState.PAUSED
            SManga.CANCELLED -> ContentState.ABANDONED
            SManga.LICENSED -> ContentState.RESTRICTED
            SManga.PUBLISHING_FINISHED -> ContentState.FINISHED
            else -> ContentState.ONGOING
        },
        authors = buildSet {
            safeAuthor?.takeIf { it.isNotBlank() }?.let { add(it) }
            safeArtist?.takeIf { it.isNotBlank() && it != safeAuthor }?.let { add(it) }
        },
        description = safeDescription,
        chapters = chapters,
        source = source,
    )
}

/**
 * Convert app Content to Mihon SManga (for calling Mihon APIs).
 */
fun Content.toMihonManga(): SManga {
    // Get baseUrl from source if available
    val baseUrl = (source as? MihonMangaSource)?.let { mihonSource ->
        (mihonSource.catalogueSource as? HttpSource)?.baseUrl ?: ""
    } ?: ""

    var cleanUrl = url

    // Check if URL has duplicate protocol/baseUrl (e.g., "https://domain.comhttps//domain.com/path")
    // Look for embedded "http" that's not at the start
    val httpIndex = cleanUrl.indexOf("http", startIndex = 1)
    if (httpIndex > 0) {
        // Extract everything from the second "http" onwards
        cleanUrl = cleanUrl.substring(httpIndex)
        android.util.Log.w("MihonDataConverters", "Detected duplicate baseUrl, extracting: '$url' -> '$cleanUrl'")
    }

    // Fix malformed protocols (https// -> https://)
    cleanUrl = cleanUrl.replace(Regex("^(https?)/+"), "$1://")

    // If URL is absolute and starts with baseUrl, strip it to avoid duplicates in HttpSource
    if (baseUrl.isNotBlank()) {
        val baseHost = baseUrl.trimEnd('/')
        if (cleanUrl.startsWith(baseHost)) {
            val stripped = cleanUrl.substring(baseHost.length)
            if (stripped.startsWith("/") || stripped.isEmpty()) {
                cleanUrl = stripped
                android.util.Log.d("MihonDataConverters", "Stripped baseUrl from absolute URL: '$url' -> '$cleanUrl'")
            }
        }
    }

    // If URL still doesn't look absolute, log warning
    if (!cleanUrl.matches(Regex("^https?://.*")) && !cleanUrl.startsWith("/")) {
        android.util.Log.w("MihonDataConverters", "URL may be invalid after cleanup: '$cleanUrl' (original: '$url')")
    }

    // NOTE: Do NOT add a leading slash to non-absolute URLs.
    // Some extensions (e.g., zaimanhua) use pure IDs like "84652" which are then
    // internally combined with their API path. Adding a slash would cause
    // double-slash issues like "detail//84652" instead of "detail/84652".

    return SManga.create().apply {
        this.url = cleanUrl
        this.title = this@toMihonManga.title
        this.author = this@toMihonManga.authors.firstOrNull()
        this.artist = this@toMihonManga.authors.drop(1).firstOrNull()
        this.description = this@toMihonManga.description
        this.genre = this@toMihonManga.tags.joinToString(", ") { it.title }
        this.status = when (this@toMihonManga.state) {
            ContentState.ONGOING -> SManga.ONGOING
            ContentState.FINISHED -> SManga.COMPLETED
            ContentState.PAUSED -> SManga.ON_HIATUS
            ContentState.ABANDONED -> SManga.CANCELLED
            ContentState.RESTRICTED -> SManga.LICENSED
            ContentState.UPCOMING -> SManga.UNKNOWN
            null -> SManga.UNKNOWN
        }
        this.thumbnail_url = this@toMihonManga.coverUrl
        this.initialized = true
        try {
            this.genres = this@toMihonManga.tags.map { it.title }
            this.altTitles = this@toMihonManga.altTitles.toList()
            this.banner = this@toMihonManga.largeCoverUrl
            this.contentRating = when (this@toMihonManga.contentRating) {
                ContentRating.SAFE -> SManga.ContentRating.SAFE
                ContentRating.SUGGESTIVE -> SManga.ContentRating.SUGGESTIVE
                ContentRating.ADULT -> SManga.ContentRating.ADULT
                null -> SManga.ContentRating.SAFE
            }
        } catch (_: AbstractMethodError) {
            // Compatibility fallback for older dynamically loaded implementations.
        } catch (_: NoSuchMethodError) {
            // Compatibility fallback for older dynamically loaded implementations.
        }
    }
}

// ============ SChapter <-> ContentChapter ============

/**
 * Resolve chapter identity from extension metadata before using host list position.
 *
 * Modern extensions may populate only SChapter.number, older ones populate chapter_number.
 * The repository-provided list index is only a last-resort fallback when neither ABI field
 * provides a usable number.
 */
internal fun SChapter.resolveContentChapterNumber(fallbackNumber: Float? = null): Float {
    val modernNumber = try {
        number?.toFloatOrNullCompat()
    } catch (_: NoSuchMethodError) {
        null
    }
    return modernNumber
        ?: chapter_number.takeIf { it >= 0 }
        ?: fallbackNumber
        ?: 0f
}

/**
 * Convert Mihon SChapter to App ContentChapter.
 */
fun SChapter.toContentChapter(source: ContentSource, fallbackNumber: Float? = null): ContentChapter {
    val chapterId = generateChapterId(url, source.name)
    val finalNumber = resolveContentChapterNumber(fallbackNumber)
    val finalVolume = try {
        volume?.toIntOrNull() ?: 0
    } catch (_: NoSuchMethodError) {
        0
    }
    val finalScanlator = try {
        scanlators.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: scanlator
    } catch (_: NoSuchMethodError) {
        scanlator
    }

    return ContentChapter(
        id = chapterId,
        title = name.takeIf { it.isNotBlank() },
        number = finalNumber,
        volume = finalVolume,
        url = url,
        scanlator = finalScanlator,
        uploadDate = date_upload,
        branch = finalScanlator, // Use scanlator as branch for grouping
        source = source,
    )
}

/**
 * Convert Apps ContentChapter to Mihon SChapter.
 */
fun ContentChapter.toMihonChapter(): SChapter {
    return SChapter.create().apply {
        this.url = this@toMihonChapter.url
        this.name = this@toMihonChapter.title ?: "Chapter ${this@toMihonChapter.number}"
        this.chapter_number = this@toMihonChapter.number
        this.date_upload = this@toMihonChapter.uploadDate
        this.scanlator = this@toMihonChapter.scanlator
        try {
            this.number = this@toMihonChapter.number.toString()
            this.volume = this@toMihonChapter.volume.takeIf { it > 0 }?.toString()
            this.scanlators = this@toMihonChapter.scanlator?.let { listOf(it) } ?: emptyList()
        } catch (_: NoSuchMethodError) {
            // Compatibility fallback for older dynamically loaded implementations.
        }
    }
}

// ============ Page <-> ContentPage ============

/**
 * Convert Mihon Page to App's ContentPage.
 *
 * NOTE: The chapter parameter is needed to generate unique page IDs.
 * Without it, all chapters would have pages with IDs 0, 1, 2... which causes
 * cache conflicts in the reader.
 */
fun Page.asContentPage(
    source: ContentSource,
    chapter: SChapter,
    headers: Map<String, String> = emptyMap()
): ContentPage {
    // Generate a unique page ID by combining chapter URL and page index
    // This prevents cache collisions between pages from different chapters
    val pageId = "${chapter.url}|page|$index".hashCode().toLong() and Long.MAX_VALUE

    return ContentPage(
        id = pageId,
        url = imageUrl ?: url,
        preview = null,
        headers = headers,
        source = source,
    )
}

/**
 * Convert App's ContentPage to Mihon Page.
 */
fun ContentPage.toMihonPage(): Page {
    return Page(
        index = id.toInt(),
        url = url,
        imageUrl = url.takeIf { it.isNotBlank() },
    )
}

// ============ ID Generation ============

/**
 * Generate a stable ID for a manga based on URL and source.
 */
private fun generateContentId(url: String, sourceName: String): Long {
    return "$sourceName|$url".hashCode().toLong() and Long.MAX_VALUE
}

/**
 * Generate a stable ID for a chapter based on URL and source.
 */
private fun generateChapterId(url: String, sourceName: String): Long {
    return "$sourceName|chapter|$url".hashCode().toLong() and Long.MAX_VALUE
}

// ============ URL Helpers ============

/**
 * Get the public URL for a manga from an HttpSource.
 */
fun HttpSource.getPublicContentUrl(manga: SManga): String {
    return try {
        getMangaUrl(manga)
    } catch (e: Exception) {
        ""
    }
}

/**
 * Get the public URL for a chapter from an HttpSource.
 */
fun HttpSource.getPublicChapterUrl(chapter: SChapter): String {
    return try {
        getChapterUrl(chapter)
    } catch (e: Exception) {
        ""
    }
}
/**
 * Resolve relative URL using baseUrl.
 */
private fun resolveUrl(baseUrl: String, url: String?): String? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("http")) return url
    if (url.startsWith("//")) return "https:$url"

    if (baseUrl.isNotBlank()) {
        return baseUrl.trimEnd('/') + "/" + url.trimStart('/')
    }
    return url
}