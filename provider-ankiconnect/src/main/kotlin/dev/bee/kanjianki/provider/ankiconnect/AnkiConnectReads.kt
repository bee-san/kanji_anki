package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectJson.Json

/**
 * Typed parsers for AnkiConnect read-action results. Each function takes the
 * already-unwrapped success result [Json] (from [AnkiConnectEnvelope.parse])
 * and maps it to a typed value, or null when the shape does not match — the
 * caller treats null as a protocol error. Nothing here performs I/O or builds
 * requests; request construction is [AnkiConnectRequests].
 *
 * These cover the read surface of the outbound allowlist: note-type discovery
 * (`modelNamesAndIds`, `modelFieldNames`), deck discovery (`deckNamesAndIds`),
 * and note/card enumeration and detail (`findNotes`, `notesInfo`, `findCards`,
 * `cardsInfo`).
 */
object AnkiConnectReads {
    /** A single note as reported by `notesInfo`. */
    data class NoteInfo(
        val noteId: Long,
        val modelName: String,
        val tags: List<String>,
        /** Field name → value, preserving AnkiConnect's field order. */
        val fields: Map<String, String>,
    )

    /** A single card as reported by `cardsInfo`. */
    data class CardInfo(
        val cardId: Long,
        val noteId: Long,
        val deckName: String,
        val modelName: String,
        val queue: Long,
        val due: Long,
        val interval: Long,
        val reps: Long,
        val lapses: Long,
    )

    /** Parses `modelNamesAndIds` / `deckNamesAndIds`: an object of name→id. */
    fun namesAndIds(result: Json): Map<String, Long>? {
        val obj = result as? Json.Obj ?: return null
        val out = LinkedHashMap<String, Long>(obj.entries.size)
        for ((name, value) in obj.entries) {
            val id = (value as? Json.Num)?.value ?: return null
            out[name] = id
        }
        return out
    }

    /** Parses `modelFieldNames`: an array of field-name strings. */
    fun fieldNames(result: Json): List<String>? = stringArray(result)

    /** Parses `findNotes` / `findCards`: an array of numeric ids. */
    fun ids(result: Json): List<Long>? {
        val arr = result as? Json.Arr ?: return null
        val out = ArrayList<Long>(arr.items.size)
        for (item in arr.items) {
            out.add((item as? Json.Num)?.value ?: return null)
        }
        return out
    }

    /** Parses `notesInfo`: an array of note objects. */
    fun notesInfo(result: Json): List<NoteInfo>? {
        val arr = result as? Json.Arr ?: return null
        val out = ArrayList<NoteInfo>(arr.items.size)
        for (item in arr.items) {
            out.add(noteInfo(item) ?: return null)
        }
        return out
    }

    /** Parses `cardsInfo`: an array of card objects. */
    fun cardsInfo(result: Json): List<CardInfo>? {
        val arr = result as? Json.Arr ?: return null
        val out = ArrayList<CardInfo>(arr.items.size)
        for (item in arr.items) {
            out.add(cardInfo(item) ?: return null)
        }
        return out
    }

    private fun noteInfo(item: Json): NoteInfo? {
        val obj = item as? Json.Obj ?: return null
        val noteId = longField(obj, "noteId") ?: return null
        val modelName = stringField(obj, "modelName") ?: return null
        val tags = (obj.entries["tags"]?.let(::stringArray)) ?: return null
        val fieldsObj = obj.entries["fields"] as? Json.Obj ?: return null
        val fields = LinkedHashMap<String, String>(fieldsObj.entries.size)
        for ((fieldName, fieldValue) in fieldsObj.entries) {
            // AnkiConnect wraps each field as {value, order}.
            val valueObj = fieldValue as? Json.Obj ?: return null
            fields[fieldName] = stringField(valueObj, "value") ?: return null
        }
        return NoteInfo(noteId, modelName, tags, fields)
    }

    private fun cardInfo(item: Json): CardInfo? {
        val obj = item as? Json.Obj ?: return null
        return CardInfo(
            cardId = longField(obj, "cardId") ?: return null,
            noteId = longField(obj, "note") ?: return null,
            deckName = stringField(obj, "deckName") ?: return null,
            modelName = stringField(obj, "modelName") ?: return null,
            queue = longField(obj, "queue") ?: return null,
            due = longField(obj, "due") ?: return null,
            interval = longField(obj, "interval") ?: return null,
            reps = longField(obj, "reps") ?: return null,
            lapses = longField(obj, "lapses") ?: return null,
        )
    }

    private fun stringArray(result: Json): List<String>? {
        val arr = result as? Json.Arr ?: return null
        val out = ArrayList<String>(arr.items.size)
        for (item in arr.items) {
            out.add((item as? Json.Str)?.value ?: return null)
        }
        return out
    }

    private fun longField(obj: Json.Obj, key: String): Long? = (obj.entries[key] as? Json.Num)?.value

    private fun stringField(obj: Json.Obj, key: String): String? = (obj.entries[key] as? Json.Str)?.value
}
