package dev.bee.kanjianki.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.KanjiInventoryBuilder
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels

internal class LocalStoreInventoryData(
    private val activity: LocalStoreHistory,
) {
    fun readInventoryItem(db: SQLiteDatabase, kanji: String?): RecordsImportModels.KanjiInventoryItem? {
        if (kanji == null) {
            return null
        }
        db.query(
            LocalStoreBase.TABLE_KANJI_INVENTORY,
            null,
            LocalStoreBase.WHERE_KANJI,
            arrayOf(kanji),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) readInventoryItem(db, cursor) else null
        }
    }

    fun readInventoryItem(db: SQLiteDatabase, cursor: Cursor): RecordsImportModels.KanjiInventoryItem {
        val kanji = LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI)
        return RecordsImportModels.KanjiInventoryItem(
            kanji,
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_PRIMARY_MEANING),
            LocalStoreBase.string(cursor, "readings"),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_BROWSER_SEARCH),
            LocalStoreBase.integer(cursor, "source_count"),
            LocalStoreBase.integer(cursor, "example_count"),
            activity.isKanjiSuspended(db, kanji),
            LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_LAST_SEEN_AT),
        )
    }

    fun readDashboardRow(db: SQLiteDatabase, kanji: String?): RecordsImportModels.DashboardRow? {
        if (kanji == null) {
            return null
        }
        db.query(
            LocalStoreBase.TABLE_DASHBOARD_ROWS,
            null,
            LocalStoreBase.WHERE_KANJI,
            arrayOf(kanji),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }
            return readDashboardRow(db, cursor)
        }
    }

    fun readDashboardRow(db: SQLiteDatabase, cursor: Cursor): RecordsImportModels.DashboardRow {
        val kanji = LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI)
        return RecordsImportModels.DashboardRow(
            kanji,
            LocalStoreBase.nullableInt(cursor, LocalStoreBase.COLUMN_JITEN_RANK),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_PRIMARY_MEANING),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_READING),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_BROWSER_SEARCH),
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_WEAKNESS_SCORE),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_REASON_CODE),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_REASON_TEXT),
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_ACTIVE_EXAMPLE_COUNT),
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_SUSPENDED_EXAMPLE_COUNT),
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_MATURE_SUPPORT_COUNT),
            examplesForKanji(db, kanji),
        )
    }

    fun examplesForKanji(db: SQLiteDatabase, kanji: String): List<RecordsImportModels.Example> {
        val examples = ArrayList<RecordsImportModels.Example>()
        db.query(
            LocalStoreBase.TABLE_KANJI_EXAMPLES,
            null,
            LocalStoreBase.WHERE_KANJI,
            arrayOf(kanji),
            null,
            null,
            "source_type DESC, id ASC",
            "8",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                examples.add(readExample(cursor))
            }
        }
        return examples
    }

    /**
     * Batched replacement for calling [examplesForKanji] once per dashboard row.
     *
     * Loading dashboard rows previously fired one query per kanji (up to 120), which on a cold
     * boot cost ~240ms on the main thread because each query paid its own cursor + file-cache
     * cost. This reads every example for the requested kanji in a single query, then groups in
     * memory while preserving the original per-kanji ordering (source_type DESC, id ASC) and the
     * 8-example cap.
     */
    fun examplesForKanjiBatch(
        db: SQLiteDatabase,
        kanji: Collection<String>,
    ): Map<String, List<RecordsImportModels.Example>> {
        val distinct = kanji.filter { it.isNotEmpty() }.distinct()
        if (distinct.isEmpty()) {
            return emptyMap()
        }
        val result = LinkedHashMap<String, ArrayList<RecordsImportModels.Example>>()
        val placeholders = distinct.joinToString(",") { "?" }
        db.query(
            LocalStoreBase.TABLE_KANJI_EXAMPLES,
            null,
            "${LocalStoreBase.COLUMN_KANJI} IN ($placeholders)",
            distinct.toTypedArray(),
            null,
            null,
            "${LocalStoreBase.COLUMN_KANJI} ASC, source_type DESC, id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val rowKanji = LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI)
                val bucket = result.getOrPut(rowKanji) { ArrayList() }
                if (bucket.size >= EXAMPLES_PER_KANJI_CAP) {
                    continue
                }
                bucket.add(readExample(cursor))
            }
        }
        return result
    }

    private fun readExample(cursor: Cursor): RecordsImportModels.Example {
        return RecordsImportModels.Example(
            LocalStoreBase.string(cursor, "source_type"),
            LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_CARD_ID),
            LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_NOTE_ID),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_EXPRESSION),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_READING),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_MEANING),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_SENTENCE),
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_MATURE) == 1,
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_LAPSES),
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_INTERVAL_DAYS),
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_REPS),
            LocalStoreBase.nullableDouble(cursor, LocalStoreBase.COLUMN_FSRS_STABILITY),
            LocalStoreBase.nullableDouble(cursor, LocalStoreBase.COLUMN_FSRS_DIFFICULTY),
            LocalStoreBase.nullableDouble(cursor, LocalStoreBase.COLUMN_FSRS_RETRIEVABILITY),
        )
    }

    private companion object {
        const val EXAMPLES_PER_KANJI_CAP = 8
    }

    fun studyItemForKanji(db: SQLiteDatabase, kanji: String?): RecordsStudyModels.StudyItem? {
        if (kanji == null) {
            return null
        }
        db.query(
            LocalStoreBase.TABLE_STUDY_ITEMS,
            null,
            LocalStoreBase.WHERE_KANJI,
            arrayOf(kanji),
            null,
            null,
            "state='retired' ASC, due_at ASC",
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }
            val item = activity.readStudyItem(cursor)
            val hasSimilar = kanjiHasSimilarNeighbor(db, kanji)
            return if (hasSimilar != item.hasSimilarKanji) item.withHasSimilarKanji(hasSimilar) else item
        }
    }

    fun kanjiHasSimilarNeighbor(db: SQLiteDatabase, kanji: String?): Boolean {
        if (kanji == null) {
            return false
        }
        db.rawQuery(
            "SELECT 1 FROM " + LocalStoreBase.TABLE_SIMILAR_KANJI_PAIRS +
                " WHERE kanji_a = ? OR kanji_b = ? LIMIT 1",
            arrayOf(kanji, kanji),
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun previousInventoryItems(db: SQLiteDatabase): Map<String, KanjiInventoryBuilder.PreviousItem> {
        val previous = LinkedHashMap<String, KanjiInventoryBuilder.PreviousItem>()
        db.query(
            LocalStoreBase.TABLE_KANJI_INVENTORY,
            null,
            null,
            null,
            null,
            null,
            LocalStoreBase.ORDER_KANJI_ASC,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                previous[LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI)] =
                    KanjiInventoryBuilder.PreviousItem(
                        LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_PRIMARY_MEANING),
                        LocalStoreBase.string(cursor, "readings"),
                        LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_BROWSER_SEARCH),
                        LocalStoreBase.integer(cursor, "source_count"),
                        LocalStoreBase.integer(cursor, "example_count"),
                        LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_FIRST_SEEN_AT),
                        LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_LAST_SEEN_AT),
                    )
            }
        }
        return previous
    }

    fun firstExampleForKanji(db: SQLiteDatabase, kanji: String): LocalStoreBase.SourceSnapshot {
        db.query(
            LocalStoreBase.TABLE_KANJI_EXAMPLES,
            arrayOf(LocalStoreBase.COLUMN_EXPRESSION, LocalStoreBase.COLUMN_READING),
            LocalStoreBase.WHERE_KANJI,
            arrayOf(kanji),
            null,
            null,
            "source_type ASC, id ASC",
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return LocalStoreBase.SourceSnapshot.EMPTY
            }
            return LocalStoreBase.SourceSnapshot(
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_EXPRESSION),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_READING),
            )
        }
    }

    fun firstSuspendedSourceForKanji(db: SQLiteDatabase, kanji: String): LocalStoreBase.SourceSnapshot {
        db.query(
            LocalStoreBase.TABLE_SUSPENDED_SOURCES,
            arrayOf(LocalStoreBase.COLUMN_EXPRESSION, LocalStoreBase.COLUMN_READING),
            LocalStoreBase.WHERE_KANJI,
            arrayOf(kanji),
            null,
            null,
            "card_id ASC",
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return LocalStoreBase.SourceSnapshot.EMPTY
            }
            return LocalStoreBase.SourceSnapshot(
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_EXPRESSION),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_READING),
            )
        }
    }

    fun rowSnapshots(db: SQLiteDatabase): Map<String, LocalStoreBase.RowSnapshot> {
        val rows = LinkedHashMap<String, LocalStoreBase.RowSnapshot>()
        db.query(
            LocalStoreBase.TABLE_DASHBOARD_ROWS,
            null,
            null,
            null,
            null,
            null,
            LocalStoreBase.ORDER_KANJI_ASC,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val row = rowSnapshotFromCursor(db, cursor)
                rows[row.kanji] = row
            }
        }
        return rows
    }

    fun rowSnapshot(db: SQLiteDatabase, kanji: String): LocalStoreBase.RowSnapshot? {
        db.query(
            LocalStoreBase.TABLE_DASHBOARD_ROWS,
            null,
            LocalStoreBase.WHERE_KANJI,
            arrayOf(kanji),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            return if (cursor.moveToFirst()) rowSnapshotFromCursor(db, cursor) else null
        }
    }

    fun rowSnapshotFromCursor(db: SQLiteDatabase, cursor: Cursor): LocalStoreBase.RowSnapshot {
        val kanji = LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI)
        return LocalStoreBase.RowSnapshot(
            kanji,
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_WEAKNESS_SCORE),
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_MATURE_SUPPORT_COUNT),
            LocalStoreBase.longValue(cursor, "rebuilt_at"),
            firstExampleForKanji(db, kanji),
        )
    }
}
