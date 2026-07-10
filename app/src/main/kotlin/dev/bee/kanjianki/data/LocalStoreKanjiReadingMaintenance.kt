package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.KanjiReadingAligner
import dev.bee.kanjianki.core.RecordsImportModels

/**
 * Rebuilds the reading-usage content tables (Goal 77) from the freshly analyzed
 * dashboard rows, mirroring how [LocalStoreSimilarKanjiMaintenance] rebuilds
 * `similar_kanji_pairs`: delete-all + reinsert inside the sync save transaction.
 *
 * For each row's examples the word's kana reading is attributed to its
 * constituent kanji via [KanjiReadingAligner]; each aligned
 * `(kanji, canonicalReading)` pair becomes one `kanji_reading_usage` row.
 * Words that fail alignment (jukujikun etc.) are skipped silently (D-R3).
 *
 * It also rebuilds `kanji_reading_pool`: the union of each kanji's attested
 * canonical readings and its bundled-dictionary canonical readings, so the
 * availability predicates that need "≥ N distinct readings exist" stay pure SQL
 * (Goal 77 design) without plumbing the dictionary into every read.
 */
internal class LocalStoreKanjiReadingMaintenance {
    fun rebuildKanjiReadingUsage(
        db: SQLiteDatabase,
        rows: List<RecordsImportModels.DashboardRow>?,
        dictionary: DictionaryLookup?,
    ) {
        db.delete(LocalStoreBase.TABLE_KANJI_READING_USAGE, null, null)
        db.delete(LocalStoreBase.TABLE_KANJI_READING_POOL, null, null)
        if (dictionary == null || rows.isNullOrEmpty()) {
            // Without a dictionary the aligner cannot build reading inventories;
            // leave the tables empty (they simply gate no rungs) rather than
            // guess. Real syncs always pass the bundled dictionary.
            return
        }
        // Attested canonical readings per kanji, deduped so the pool is a set.
        val attestedByKanji = HashMap<String, MutableSet<String>>()
        for (row in rows) {
            for (example in row.examples) {
                insertUsageForExample(db, example, dictionary, attestedByKanji)
            }
        }
        rebuildReadingPool(db, rows, dictionary, attestedByKanji)
    }

    private fun insertUsageForExample(
        db: SQLiteDatabase,
        example: RecordsImportModels.Example,
        dictionary: DictionaryLookup,
        attestedByKanji: HashMap<String, MutableSet<String>>,
    ) {
        val expression = example.expression
        val reading = example.reading
        if (expression.isEmpty() || reading.isEmpty()) {
            return
        }
        val pairs = KanjiReadingAligner.alignPlain(expression, reading, dictionary) ?: return
        for (pair in pairs) {
            val values = ContentValues()
            values.put(LocalStoreBase.COLUMN_KANJI, pair.kanji)
            values.put(LocalStoreBase.COLUMN_READING, pair.canonicalReading)
            values.put(LocalStoreBase.COLUMN_EXPRESSION, expression)
            values.put(LocalStoreBase.COLUMN_NOTE_ID, example.noteId)
            values.put(LocalStoreBase.COLUMN_SOURCE_TYPE, example.sourceType)
            values.put(LocalStoreBase.COLUMN_MATURE, if (example.mature) 1 else 0)
            values.put(LocalStoreBase.COLUMN_LAPSES, example.lapses)
            values.put(LocalStoreBase.COLUMN_INTERVAL_DAYS, example.intervalDays)
            // PK (kanji, reading, note_id): a note reused across sibling kanji
            // rows still yields one row per distinct kanji; a re-align of the
            // same word onto the same kanji replaces (idempotent).
            db.insertWithOnConflict(
                LocalStoreBase.TABLE_KANJI_READING_USAGE,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            attestedByKanji.getOrPut(pair.kanji) { LinkedHashSet() }.add(pair.canonicalReading)
        }
    }

    private fun rebuildReadingPool(
        db: SQLiteDatabase,
        rows: List<RecordsImportModels.DashboardRow>,
        dictionary: DictionaryLookup,
        attestedByKanji: HashMap<String, MutableSet<String>>,
    ) {
        // Pool kanji = every inventory kanji that appears as a dashboard row.
        val kanjiSet = LinkedHashSet<String>()
        for (row in rows) {
            if (row.kanji.isNotEmpty()) {
                kanjiSet.add(row.kanji)
            }
        }
        for (kanji in kanjiSet) {
            val attested = attestedByKanji[kanji] ?: emptySet<String>()
            val dictionaryReadings = dictionaryCanonicalReadings(dictionary, kanji)
            val allReadings = LinkedHashSet<String>()
            allReadings.addAll(attested)
            allReadings.addAll(dictionaryReadings)
            for (reading in allReadings) {
                val values = ContentValues()
                values.put(LocalStoreBase.COLUMN_KANJI, kanji)
                values.put(LocalStoreBase.COLUMN_READING, reading)
                values.put(LocalStoreBase.COLUMN_ATTESTED, if (attested.contains(reading)) 1 else 0)
                db.insertWithOnConflict(
                    LocalStoreBase.TABLE_KANJI_READING_POOL,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
    }

    private fun dictionaryCanonicalReadings(dictionary: DictionaryLookup, kanji: String): Set<String> {
        val entry = dictionary.lookupKanji(kanji) ?: return emptySet()
        val out = LinkedHashSet<String>()
        for ((_, canonical) in KanjiReadingAligner.readingInventory(entry)) {
            if (canonical.isNotEmpty()) {
                out.add(canonical)
            }
        }
        return out
    }
}
