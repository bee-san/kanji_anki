package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import dev.bee.kanjianki.core.TextUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

abstract class LocalStoreInventory extends LocalStoreSimilarKanji {
    LocalStoreInventory(Context context) {
        super(context);
    }

    public List<RecordsImportModels.DashboardRow> dashboardRows() {
        SQLiteDatabase db = getReadableDatabase();
        List<RecordsImportModels.DashboardRow> rows = new ArrayList<>();
        try (Cursor cursor = db.query(TABLE_DASHBOARD_ROWS, null, null, null, null, null, "weakness_score DESC, suspended_example_count DESC, kanji ASC", "120")) {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, COLUMN_KANJI);
                rows.add(new RecordsImportModels.DashboardRow(
                        kanji,
                        nullableInt(cursor, COLUMN_JITEN_RANK),
                        string(cursor, COLUMN_PRIMARY_MEANING),
                        string(cursor, COLUMN_READING),
                        string(cursor, COLUMN_BROWSER_SEARCH),
                        integer(cursor, COLUMN_WEAKNESS_SCORE),
                        string(cursor, COLUMN_REASON_CODE),
                        string(cursor, COLUMN_REASON_TEXT),
                        integer(cursor, COLUMN_ACTIVE_EXAMPLE_COUNT),
                        integer(cursor, COLUMN_SUSPENDED_EXAMPLE_COUNT),
                        integer(cursor, COLUMN_MATURE_SUPPORT_COUNT),
                        examplesForKanji(db, kanji)
                ));
            }
        }
        return rows;
    }

    public List<RecordsImportModels.DashboardRow> activeDashboardRows() {
        Set<String> suspended = locallySuspendedKanji();
        if (suspended.isEmpty()) {
            return dashboardRows();
        }
        List<RecordsImportModels.DashboardRow> out = new ArrayList<>();
        for (RecordsImportModels.DashboardRow row : dashboardRows()) {
            if (!suspended.contains(row.kanji)) {
                out.add(row);
            }
        }
        return out;
    }

    public RecordsImportModels.DashboardRow rowForKanji(String kanji) {
        return readDashboardRow(getReadableDatabase(), kanji);
    }

    public RecordsImportModels.KanjiInventoryItem inventoryItemForKanji(String kanji) {
        return readInventoryItem(getReadableDatabase(), kanji);
    }

    public List<RecordsImportModels.KanjiInventoryItem> searchKanjiInventory(String query) {
        SQLiteDatabase db = getReadableDatabase();
        String normalized = TextUtil.normalizeJapanese(query == null ? "" : query).toLowerCase(Locale.ROOT);
        List<RecordsImportModels.KanjiInventoryItem> out = new ArrayList<>();
        String selection = null;
        String[] args = null;
        if (!normalized.isEmpty()) {
            selection = "search_text LIKE ?";
            args = new String[]{"%" + normalized + "%"};
        }
        try (Cursor cursor = db.query(
                TABLE_KANJI_INVENTORY,
                null,
                selection,
                args,
                null,
                null,
                ORDER_KANJI_ASC,
                "300"
        )) {
            while (cursor.moveToNext()) {
                out.add(readInventoryItem(db, cursor));
            }
        }
        return out;
    }

    public Set<String> locallySuspendedKanji() {
        Set<String> out = new HashSet<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_LOCAL_KANJI_SUSPENSIONS, new String[]{COLUMN_KANJI}, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                out.add(string(cursor, COLUMN_KANJI));
            }
        }
        return out;
    }

    public boolean isKanjiLocallySuspended(String kanji) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_LOCAL_KANJI_SUSPENSIONS, new String[]{COLUMN_KANJI}, WHERE_KANJI, new String[]{kanji}, null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    public void setKanjiLocallySuspended(String kanji, boolean suspended, long nowMillis) {
        if (kanji == null || kanji.isEmpty()) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (suspended) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_KANJI, kanji);
                values.put("suspended_at", nowMillis);
                db.insertWithOnConflict(TABLE_LOCAL_KANJI_SUSPENSIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                db.delete(TABLE_LEARNING_REPEATS, WHERE_KANJI, new String[]{kanji});
            } else {
                db.delete(TABLE_LOCAL_KANJI_SUSPENSIONS, WHERE_KANJI, new String[]{kanji});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public RecordsStudyModels.KanjiRecoveryTimeline timelineForKanji(String kanji) {
        SQLiteDatabase db = getReadableDatabase();
        RecordsImportModels.KanjiInventoryItem inventoryItem = readInventoryItem(db, kanji);
        RecordsImportModels.DashboardRow row = readDashboardRow(db, kanji);
        RecordsStudyModels.StudyItem item = studyItemForKanji(db, kanji);
        List<RecordsImportModels.KanjiTimelineEvent> events = new ArrayList<>();
        Cursor cursor = db.query(
                TABLE_KANJI_TIMELINE_EVENTS,
                null,
                WHERE_KANJI,
                new String[]{kanji},
                null,
                null,
                "occurred_at DESC, id DESC",
                "50"
        );
        try {
            while (cursor.moveToNext()) {
                events.add(readTimelineEvent(cursor));
            }
        } finally {
            cursor.close();
        }
        Collections.reverse(events);
        return new RecordsStudyModels.KanjiRecoveryTimeline(inventoryItem, row, item, events);
    }

    public List<RecordsStudyModels.StudyItem> studyItems() {
        SQLiteDatabase db = getReadableDatabase();
        List<RecordsStudyModels.StudyItem> items = new ArrayList<>();
        try (Cursor cursor = db.query(TABLE_STUDY_ITEMS, null, null, null, null, null, "due_at ASC")) {
            while (cursor.moveToNext()) {
                items.add(readStudyItem(cursor));
            }
        }
        Set<String> withSimilar = kanjiWithSimilarNeighbors(db);
        for (int i = 0; i < items.size(); i++) {
            RecordsStudyModels.StudyItem current = items.get(i);
            boolean hasSimilar = withSimilar.contains(current.kanji);
            if (hasSimilar != current.hasSimilarKanji) {
                items.set(i, current.withHasSimilarKanji(hasSimilar));
            }
        }
        return items;
    }

    /**
     * Returns the set of kanji that have at least one entry in the
     * {@code similar_kanji_pairs} table, either as kanji_a or kanji_b.
     * This set is the data source for
     * {@link RecordsStudyModels.StudyItem#hasSimilarKanji}: when a study item's kanji
     * is present here, the {@code similar_kanji} rung is included in the
     * ladder for that card.
     */
    Set<String> kanjiWithSimilarNeighbors(SQLiteDatabase db) {
        Set<String> out = new HashSet<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT kanji_a FROM " + TABLE_SIMILAR_KANJI_PAIRS
                        + " UNION SELECT kanji_b FROM " + TABLE_SIMILAR_KANJI_PAIRS,
                null
        )) {
            while (cursor.moveToNext()) {
                String k = cursor.getString(0);
                if (k != null && !k.isEmpty()) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    /**
     * Re-applies the {@link RecordsStudyModels.StudyItem#hasSimilarKanji} predicate to
     * each item in the given list using the current
     * {@code similar_kanji_pairs} contents. Call this after
     * {@link BridgeScheduler#seedQueue} produces new items but before
     * persisting them so the ladder scheduler's {@code similar_kanji} rung
     * inclusion decision is consistent with the just-rebuilt similarity
     * data, without waiting for a follow-up DB reload.
     */
    public List<RecordsStudyModels.StudyItem> annotateSimilarKanjiAvailability(List<RecordsStudyModels.StudyItem> items) {
        if (items == null || items.isEmpty()) {
            return items == null ? Collections.emptyList() : items;
        }
        Set<String> withSimilar = kanjiWithSimilarNeighbors(getReadableDatabase());
        List<RecordsStudyModels.StudyItem> out = new ArrayList<>(items.size());
        for (RecordsStudyModels.StudyItem item : items) {
            boolean hasSimilar = withSimilar.contains(item.kanji);
            out.add(hasSimilar == item.hasSimilarKanji ? item : item.withHasSimilarKanji(hasSimilar));
        }
        return out;
    }

    public List<RecordsImportModels.SuspendedImport> suspendedImports() {
        SQLiteDatabase db = getReadableDatabase();
        Map<String, MutableSuspendedImport> imports = new LinkedHashMap<>();
        try (Cursor cursor = db.query(TABLE_SUSPENDED_IMPORTS, null, null, null, null, null, "jiten_rank ASC, kanji ASC")) {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, COLUMN_KANJI);
                imports.put(kanji, new MutableSuspendedImport(
                        kanji,
                        nullableInt(cursor, COLUMN_JITEN_RANK),
                        integer(cursor, COLUMN_RANK_KNOWN) == 1,
                        integer(cursor, COLUMN_CUTOFF_USED)
                ));
            }
        }

        try (Cursor sources = db.query(TABLE_SUSPENDED_SOURCES, null, null, null, null, null, "kanji ASC, card_id ASC")) {
            while (sources.moveToNext()) {
                MutableSuspendedImport imported = imports.get(string(sources, COLUMN_KANJI));
                if (imported == null) {
                    continue;
                }
                imported.sources.add(new RecordsImportModels.SuspendedSource(
                        imported.kanji,
                        longValue(sources, COLUMN_CARD_ID),
                        longValue(sources, COLUMN_NOTE_ID),
                        string(sources, COLUMN_EXPRESSION),
                        string(sources, COLUMN_READING),
                        string(sources, COLUMN_MEANING),
                        string(sources, COLUMN_SENTENCE)
                ));
            }
        }

        List<RecordsImportModels.SuspendedImport> out = new ArrayList<>();
        for (MutableSuspendedImport imported : imports.values()) {
            out.add(imported.build());
        }
        return out;
    }

}
