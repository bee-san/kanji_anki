package dev.bee.kanjianki.kani.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import dev.bee.kanjianki.kani.core.AnkiText
import dev.bee.kanjianki.kani.core.KanjiState
import dev.bee.kanjianki.kani.core.Mixup
import dev.bee.kanjianki.kani.core.ScanAnalyzer
import dev.bee.kanjianki.kani.core.Status

/** Everything Kani 1.0 remembers. Nothing here is written back to Anki except opt-in tags. */
class KaniStore(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    data class Word(val noteId: Long, val expression: String, val reading: String, val meaning: String)

    data class Answer(val day: Int, val kanji: String, val correct: Boolean, val latencyMs: Long)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE kanji (
                kanji TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                reason TEXT,
                note_ids TEXT NOT NULL DEFAULT '',
                good_days TEXT NOT NULL DEFAULT '',
                last_miss_day INTEGER,
                last_seen_day INTEGER,
                meaning_misses INTEGER NOT NULL DEFAULT 0,
                hook TEXT,
                tagged INTEGER NOT NULL DEFAULT 0
            )""",
        )
        db.execSQL("CREATE TABLE word (note_id INTEGER PRIMARY KEY, expression TEXT NOT NULL, reading TEXT NOT NULL, meaning TEXT NOT NULL)")
        db.execSQL(
            """CREATE TABLE mixup (
                target TEXT NOT NULL, picked TEXT NOT NULL, count INTEGER NOT NULL, last_day INTEGER NOT NULL,
                PRIMARY KEY (target, picked)
            )""",
        )
        db.execSQL("CREATE TABLE answer (id INTEGER PRIMARY KEY AUTOINCREMENT, day INTEGER NOT NULL, kanji TEXT NOT NULL, correct INTEGER NOT NULL, latency_ms INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE early (kanji TEXT PRIMARY KEY, look_alike TEXT NOT NULL, shown INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE known (kanji TEXT PRIMARY KEY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Kani 1.0 starts fresh; future versions migrate here.
    }

    // region kanji

    fun allKanji(): List<KanjiState> = readableDatabase.query("kanji", null, null, null, null, null, null).use { c ->
        buildList { while (c.moveToNext()) add(c.toState()) }
    }

    fun kanji(kanji: String): KanjiState? =
        readableDatabase.query("kanji", null, "kanji=?", arrayOf(kanji), null, null, null).use { c ->
            if (c.moveToFirst()) c.toState() else null
        }

    fun save(state: KanjiState) {
        writableDatabase.insertWithOnConflict("kanji", null, state.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Applies a scan: adds new problem kanji, refreshes note ids, keeps progress. */
    fun applyScan(result: ScanAnalyzer.Result, words: List<Word>) {
        writableDatabase.transaction {
            val db = this
            for (f in result.flagged) {
                val existing = kanji(f.kanji)
                val next = existing?.copy(noteIds = existing.noteIds + f.problemNoteIds, reason = existing.reason ?: f.reason)
                    ?: KanjiState.fresh(f.kanji, f.reason, f.problemNoteIds)
                // A kanji the user forced back in, or one Kani cleared before, follows the new scan.
                val status = if (existing?.status == Status.CLEARED) Status.TRAINING else next.status
                db.insertWithOnConflict("kanji", null, next.copy(status = status).toValues(), SQLiteDatabase.CONFLICT_REPLACE)
            }
            for (c in result.cleared) {
                if (kanji(c.kanji) != null) continue
                val state = KanjiState.fresh(c.kanji, null, c.problemNoteIds).copy(status = Status.CLEARED)
                db.insertWithOnConflict("kanji", null, state.toValues(), SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.delete("word", null, null)
            for (w in words) {
                db.insert("word", null, ContentValues().apply {
                    put("note_id", w.noteId); put("expression", w.expression); put("reading", w.reading); put("meaning", w.meaning)
                })
            }
            for (k in result.known) {
                db.insertWithOnConflict("known", null, ContentValues().apply { put("kanji", k) }, SQLiteDatabase.CONFLICT_IGNORE)
            }
            for ((k, lookAlike) in result.early) {
                db.insertWithOnConflict(
                    "early", null,
                    ContentValues().apply { put("kanji", k); put("look_alike", lookAlike) },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
        }
    }

    // endregion

    // region words

    fun wordsWith(kanji: String, limit: Int = 8): List<Word> =
        readableDatabase.query("word", null, "expression LIKE ?", arrayOf("%$kanji%"), "expression", null, "length(expression)", limit.toString())
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(c.toWord())
                    }
                }
            }

    fun word(noteId: Long): Word? =
        readableDatabase.query("word", null, "note_id=?", arrayOf(noteId.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) c.toWord() else null
        }

    fun isKnown(kanji: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM known WHERE kanji=?", arrayOf(kanji)).use { it.moveToFirst() }

    // endregion

    // region mix-ups

    fun recordMixup(target: String, picked: String, day: Int) {
        // No UPSERT: SQLite only has it from Android 11, and minSdk is 26.
        val db = writableDatabase
        db.execSQL("UPDATE mixup SET count = count + 1, last_day = ? WHERE target = ? AND picked = ?", arrayOf<Any>(day, target, picked))
        db.execSQL("INSERT OR IGNORE INTO mixup (target, picked, count, last_day) VALUES (?, ?, 1, ?)", arrayOf<Any>(target, picked, day))
    }

    fun mixups(): List<Mixup> =
        readableDatabase.query("mixup", null, null, null, null, null, "count DESC, last_day DESC").use { c ->
            buildList { while (c.moveToNext()) add(Mixup(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3))) }
        }

    // endregion

    // region answers

    fun logAnswer(answer: Answer) {
        writableDatabase.insert("answer", null, ContentValues().apply {
            put("day", answer.day); put("kanji", answer.kanji); put("correct", if (answer.correct) 1 else 0); put("latency_ms", answer.latencyMs)
        })
    }

    // endregion

    // region early

    fun nextEarly(): Pair<String, String>? =
        readableDatabase.rawQuery("SELECT kanji, look_alike FROM early WHERE shown = 0 LIMIT 1", null).use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
        }

    fun markEarlyShown(kanji: String) {
        writableDatabase.execSQL("UPDATE early SET shown = 1 WHERE kanji = ?", arrayOf(kanji))
    }

    // endregion

    /** Stored meanings are a raw plain-text prefix; clean them into a gloss on the way out. */
    private fun Cursor.toWord() = Word(getLong(0), getString(1), getString(2), AnkiText.meaning(getString(3)).take(MAX_MEANING))

    private fun Cursor.toState(): KanjiState {
        fun str(name: String): String? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
        fun int(name: String): Int? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getInt(it) }
        return KanjiState(
            kanji = str("kanji")!!,
            status = Status.valueOf(str("status")!!),
            reason = str("reason")?.let { runCatching { ScanAnalyzer.Reason.valueOf(it) }.getOrNull() },
            noteIds = str("note_ids").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet(),
            goodDays = str("good_days").orEmpty().split(',').mapNotNull { it.toIntOrNull() },
            lastMissDay = int("last_miss_day"),
            lastSeenDay = int("last_seen_day"),
            meaningMisses = int("meaning_misses") ?: 0,
            hook = str("hook"),
            tagged = (int("tagged") ?: 0) != 0,
        )
    }

    private fun KanjiState.toValues() = ContentValues().apply {
        put("kanji", kanji)
        put("status", status.name)
        put("reason", reason?.name)
        put("note_ids", noteIds.joinToString(","))
        put("good_days", goodDays.joinToString(","))
        put("last_miss_day", lastMissDay)
        put("last_seen_day", lastSeenDay)
        put("meaning_misses", meaningMisses)
        put("hook", hook)
        put("tagged", if (tagged) 1 else 0)
    }

    companion object {
        const val NAME = "kani1.db"

        /** The pre-1.0 database. Kani 1.0 starts fresh, so the old one is removed on first run. */
        const val LEGACY_NAME = "kanji_anki_simple.db"
        private const val MAX_MEANING = 120
        private const val VERSION = 1
    }
}
