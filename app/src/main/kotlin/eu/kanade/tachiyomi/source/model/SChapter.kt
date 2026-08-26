// Ported and adapted from Kototoro at dec0ef781644245f6937dc1cafc8ca84963fe08e.
// Upstream project: Kototoro-app/Kototoro, Apache-2.0.
@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import java.io.Serializable
import kotlinx.serialization.json.JsonObject

interface SChapter : Serializable {

    var url: String
    var name: String
    var date_upload: Long
    var chapter_number: Float
    var scanlator: String?

    var number: String?
        get() = if (chapter_number >= 0) chapter_number.toString() else null
        set(value) {
            chapter_number = value?.toFloatOrNull() ?: -1f
        }

    var volume: String?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}

    var scanlators: List<String>
        get() = scanlator?.let { listOf(it) } ?: emptyList()
        set(value) {
            scanlator = value.joinToString(", ")
        }

    var note: String?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}

    var memo: JsonObject
        get() = JsonObject(emptyMap())
        set(@Suppress("UNUSED_PARAMETER") value) {}

    var locked: Boolean
        get() = false
        set(@Suppress("UNUSED_PARAMETER") value) {}

    var read: Boolean
        get() = false
        set(@Suppress("UNUSED_PARAMETER") value) {}

    var last_page_read: Int
        get() = 0
        set(@Suppress("UNUSED_PARAMETER") value) {}

    fun copyFrom(other: SChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        chapter_number = other.chapter_number
        scanlator = other.scanlator
        try {
            number = other.number
            volume = other.volume
            scanlators = other.scanlators
            note = other.note
            memo = other.memo
            locked = other.locked
            read = other.read
            last_page_read = other.last_page_read
        } catch (_: NoSuchMethodError) {
            // Compatibility fallback for older dynamically loaded implementations.
        }
    }

    companion object {
        fun create(): SChapter = SChapterImpl()
    }
}

class SChapterImpl : SChapter {

    override lateinit var url: String
    override lateinit var name: String
    override var date_upload: Long = 0
    override var chapter_number: Float = -1f
    override var scanlator: String? = null

    private var _number: String? = null
    override var number: String?
        get() {
            val local = _number
            if (local != null) return local
            return if (chapter_number >= 0) chapter_number.toString() else null
        }
        set(value) {
            _number = value
            val parsed = value?.toFloatOrNullCompat()
            if (parsed != null) chapter_number = parsed
        }

    override var volume: String? = null

    private var _scanlators: List<String>? = null
    override var scanlators: List<String>
        get() = _scanlators ?: scanlator?.let { listOf(it) } ?: emptyList()
        set(value) {
            _scanlators = value
            scanlator = value.joinToString(", ")
        }

    override var note: String? = null
    override var memo: JsonObject = JsonObject(emptyMap())
    override var locked: Boolean = false
    override var read: Boolean = false
    override var last_page_read: Int = 0
}

fun String.toFloatOrNullCompat(): Float? {
    toFloatOrNull()?.let { return it }
    return trim().replace(Regex("[a-zA-Z]+$"), "").toFloatOrNull()
}
