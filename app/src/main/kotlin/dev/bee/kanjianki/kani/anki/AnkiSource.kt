package dev.bee.kanjianki.kani.anki

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri

/**
 * Read-only view of the user's AnkiDroid collection for Kani 1.0.
 *
 * Everything goes through Anki search strings on the notes URI, so the provider does the
 * filtering (leeches, lapses, suspended) and Kani never has to read scheduler columns that
 * older AnkiDroid releases do not expose.
 */
class AnkiSource(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    enum class State { NOT_INSTALLED, NEEDS_PERMISSION, READY }

    data class Model(val id: Long, val name: String, val fields: List<String>)

    data class Note(val id: Long, val modelId: Long, val fields: List<String>, val tags: List<String>)

    private data class Target(val authority: String, val permission: String)

    fun state(): State {
        val target = target() ?: return State.NOT_INSTALLED
        return if (appContext.checkSelfPermission(target.permission) == PackageManager.PERMISSION_GRANTED) {
            State.READY
        } else {
            State.NEEDS_PERMISSION
        }
    }

    /** The runtime permission to request, or null when AnkiDroid is not installed. */
    fun permission(): String? = target()?.permission

    fun models(): List<Model> {
        val target = target() ?: return emptyList()
        val out = ArrayList<Model>()
        resolver.query(uri(target, "models"), null, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.long("_id") ?: continue
                val name = c.string("name").orEmpty()
                val fields = c.string("field_names").orEmpty().split(FIELD_SEPARATOR).filter { it.isNotBlank() }
                out += Model(id, name, fields)
            }
        }
        return out
    }

    /** Notes matching an Anki search string, e.g. `note:"Core 2k" tag:leech`. */
    fun notes(search: String, limit: Int = Int.MAX_VALUE): List<Note> {
        val target = target() ?: return emptyList()
        val out = ArrayList<Note>()
        val cursor = try {
            resolver.query(uri(target, "notes"), arrayOf("_id", "mid", "flds", "tags"), search, null, null)
        } catch (_: IllegalArgumentException) {
            resolver.query(uri(target, "notes"), null, search, null, null)
        } ?: return emptyList()
        cursor.use { c ->
            while (out.size < limit && c.moveToNext()) {
                val id = c.long("_id") ?: continue
                out += Note(
                    id = id,
                    modelId = c.long("mid") ?: 0L,
                    fields = c.string("flds").orEmpty().split(FIELD_SEPARATOR),
                    tags = c.string("tags").orEmpty().trim().split(WHITESPACE).filter { it.isNotEmpty() },
                )
            }
        }
        return out
    }

    fun noteIds(search: String): Set<Long> {
        val target = target() ?: return emptySet()
        val ids = LinkedHashSet<Long>()
        val cursor = try {
            resolver.query(uri(target, "notes"), arrayOf("_id"), search, null, null)
        } catch (_: IllegalArgumentException) {
            resolver.query(uri(target, "notes"), null, search, null, null)
        } ?: return emptySet()
        cursor.use { c -> while (c.moveToNext()) c.long("_id")?.let(ids::add) }
        return ids
    }

    /** Every deck name in the collection. */
    fun deckNames(): List<String> {
        val target = target() ?: return emptyList()
        val names = ArrayList<String>()
        resolver.query(uri(target, "decks"), null, null, null, null)?.use { c ->
            while (c.moveToNext()) c.string("deck_name")?.let(names::add)
        }
        return names
    }

    /**
     * Notes per deck for the given base search, counting each note in its own deck only
     * (subdecks are excluded from their parent's count).
     */
    fun noteCountsByDeck(baseSearch: String): List<Pair<String, Int>> = deckNames().mapNotNull { deck ->
        val q = deck.replace("\\", "\\\\").replace("\"", "\\\"")
        val count = noteIds("$baseSearch deck:\"$q\" -deck:\"$q::*\"").size
        if (count > 0) deck to count else null
    }.sortedByDescending { it.second }

    /**
     * Adds the opt-in [SOLID_TAG] to each note. Returns the notes that now carry the tag
     * (including ones that already had it); a note missing from the result failed and should
     * be retried later.
     */
    fun tagSolid(noteIds: Set<Long>): Set<Long> {
        val target = target() ?: return emptySet()
        if (state() != State.READY) return emptySet()
        val tagged = LinkedHashSet<Long>()
        for (noteId in noteIds) {
            val noteUri = uri(target, "notes", noteId.toString())
            try {
                val tags = resolver.query(noteUri, arrayOf("tags"), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.string("tags").orEmpty() else null
                } ?: continue
                val list = tags.trim().split(WHITESPACE).filter { it.isNotEmpty() }
                if (list.any { it.equals(SOLID_TAG, ignoreCase = true) }) {
                    tagged += noteId
                    continue
                }
                val values = ContentValues().apply { put("tags", (list + SOLID_TAG).joinToString(" ")) }
                if (resolver.update(noteUri, values, null, null) > 0) tagged += noteId
            } catch (_: RuntimeException) {
                // One bad note must not stop the rest; it is left out and retried next session.
            }
        }
        return tagged
    }

    private fun target(): Target? {
        val pm = appContext.packageManager
        return TARGETS.firstOrNull { pm.resolveContentProvider(it.authority, 0) != null }
    }

    private fun uri(target: Target, vararg segments: String): Uri {
        val b = Uri.Builder().scheme("content").authority(target.authority)
        segments.forEach { b.appendPath(it) }
        return b.build()
    }

    private fun Cursor.string(column: String): String? {
        val i = getColumnIndex(column)
        return if (i < 0 || isNull(i)) null else getString(i)
    }

    private fun Cursor.long(column: String): Long? {
        val i = getColumnIndex(column)
        return if (i < 0 || isNull(i)) null else getLong(i)
    }

    companion object {
        const val SOLID_TAG = "kani_solid"
        private const val FIELD_SEPARATOR = '\u001f'
        private val WHITESPACE = Regex("\\s+")
        private const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
        private const val DEBUG_PERMISSION = "com.ichi2.anki.debug.permission.READ_WRITE_DATABASE"
        private val TARGETS = listOf(
            Target("com.ichi2.anki.flashcards", PERMISSION),
            Target("com.ichi2.anki.debug.flashcards", DEBUG_PERMISSION),
        )

        fun noteSearch(modelNames: Collection<String>): String {
            val parts = modelNames.map { "note:\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }
            return if (parts.size == 1) parts[0] else parts.joinToString(" OR ", "(", ")")
        }
    }
}
