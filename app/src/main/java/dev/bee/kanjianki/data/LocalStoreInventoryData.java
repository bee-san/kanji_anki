package dev.bee.kanjianki.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsStudyModels;

final class LocalStoreInventoryData {
    private final LocalStoreHistory activity;

    LocalStoreInventoryData(LocalStoreHistory activity) {
        this.activity = activity;
    }

    RecordsImportModels.KanjiInventoryItem readInventoryItem(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(LocalStoreBase.TABLE_KANJI_INVENTORY, null, LocalStoreBase.WHERE_KANJI, new String[]{kanji}, null, null, null, "1");
        try {
            return cursor.moveToFirst() ? readInventoryItem(db, cursor) : null;
        } finally {
            cursor.close();
        }
    }

    RecordsImportModels.KanjiInventoryItem readInventoryItem(SQLiteDatabase db, Cursor cursor) {
        String kanji = LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI);
        return new RecordsImportModels.KanjiInventoryItem(
                kanji,
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_PRIMARY_MEANING),
                LocalStoreBase.string(cursor, "readings"),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_BROWSER_SEARCH),
                LocalStoreBase.integer(cursor, "source_count"),
                LocalStoreBase.integer(cursor, "example_count"),
                activity.isKanjiSuspended(db, kanji),
                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_LAST_SEEN_AT)
        );
    }

    RecordsImportModels.DashboardRow readDashboardRow(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(LocalStoreBase.TABLE_DASHBOARD_ROWS, null, LocalStoreBase.WHERE_KANJI, new String[]{kanji}, null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return readDashboardRow(db, cursor);
        } finally {
            cursor.close();
        }
    }

    RecordsImportModels.DashboardRow readDashboardRow(SQLiteDatabase db, Cursor cursor) {
        String kanji = LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI);
        return new RecordsImportModels.DashboardRow(
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
                activity.examplesForKanji(db, kanji)
        );
    }

    RecordsStudyModels.StudyItem studyItemForKanji(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(LocalStoreBase.TABLE_STUDY_ITEMS, null, LocalStoreBase.WHERE_KANJI, new String[]{kanji}, null, null, "state='retired' ASC, due_at ASC", "1");
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            RecordsStudyModels.StudyItem item = activity.readStudyItem(cursor);
            boolean hasSimilar = kanjiHasSimilarNeighbor(db, kanji);
            return hasSimilar != item.hasSimilarKanji ? item.withHasSimilarKanji(hasSimilar) : item;
        } finally {
            cursor.close();
        }
    }

    boolean kanjiHasSimilarNeighbor(SQLiteDatabase db, String kanji) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM " + LocalStoreBase.TABLE_SIMILAR_KANJI_PAIRS
                        + " WHERE kanji_a = ? OR kanji_b = ? LIMIT 1",
                new String[]{kanji, kanji}
        )) {
            return cursor.moveToFirst();
        }
    }
}
