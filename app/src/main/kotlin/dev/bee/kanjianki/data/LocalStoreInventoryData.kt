package dev.bee.kanjianki.data

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
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
            activity.examplesForKanji(db, kanji),
        )
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
}
