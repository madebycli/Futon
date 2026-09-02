package io.github.landwarderer.futon.mihon.model

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.json.JsonObject

/**
 * Creates a host-owned defensive copy of an extension-provided SChapter.
 *
 * The Mihon host ABI is a deliberate superset of older extension contracts. Optional modern
 * fields are read defensively so older dynamically loaded implementations remain usable.
 */
internal fun SChapter.snapshot(): SChapter = SChapter.create().also { snapshot ->
    snapshot.url = readMihonField("") { url }
    snapshot.name = readMihonField("") { name }
    snapshot.date_upload = readMihonField(0L) { date_upload }
    snapshot.chapter_number = readMihonField(-1f) { chapter_number }
    snapshot.scanlator = readMihonField<String?>(null) { scanlator }
    snapshot.number = readMihonField<String?>(null) { number }
    snapshot.volume = readMihonField<String?>(null) { volume }
    snapshot.scanlators = readMihonField(emptyList()) { scanlators }.toList()
    snapshot.note = readMihonField<String?>(null) { note }
    snapshot.memo = JsonObject(readMihonField(JsonObject(emptyMap())) { memo }.toMap())
    snapshot.locked = readMihonField(false) { locked }
    snapshot.read = readMihonField(false) { read }
    snapshot.last_page_read = readMihonField(0) { last_page_read }
}

private inline fun <T> SChapter.readMihonField(defaultValue: T, getter: SChapter.() -> T): T {
    return try {
        getter()
    } catch (_: UninitializedPropertyAccessException) {
        defaultValue
    } catch (_: AbstractMethodError) {
        defaultValue
    } catch (_: NoSuchMethodError) {
        defaultValue
    }
}
