package dev.bee.kanjianki.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.HistoricalKanjiAggregate;
import dev.bee.kanjianki.core.KanjiImpactAnalyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class KanjiImpactReportStore {
    private static final String TABLE_SYNC_RUNS = "sync_runs";
    private static final String TABLE_SYNC_KANJI_SNAPSHOTS = "sync_kanji_snapshots";
    private static final String TABLE_STUDY_ITEMS = "study_items";
    private static final String TABLE_SUSPENDED_IMPORTS = "suspended_imports";
    private static final String COLUMN_KANJI = "kanji";
    private static final String COLUMN_SYNC_ID = "sync_id";
    private static final String COLUMN_MATURE_SUPPORT_COUNT = "mature_support_count";
    private static final String ORDER_ID_DESC = "id DESC";
    private static final String ORDER_KANJI_ASC = "kanji ASC";
    private static final String STATUS_SUCCESS = "success";
    private final LocalStore store;

    KanjiImpactReportStore(LocalStore store) {
        this.store = store;
    }

    KanjiImpactAnalyzer.Report report() {
        SQLiteDatabase db = store.getReadableDatabase();
        long latestSyncId = latestSuccessfulSyncId(db);
        if (latestSyncId == 0L) {
            return new KanjiImpactAnalyzer.Report(0, 0, 0, List.of());
        }
        Map<String, KanjiImpactAnalyzer.MetricSnapshot> currentByKanji = kanjiMetricsForSync(db, latestSyncId);
        Map<String, Integer> reviewCounts = reviewCountsByKanji(db);
        Set<String> candidates = impactCandidateKanji(db, latestSyncId);
        candidates.addAll(reviewCounts.keySet());
        List<KanjiImpactAnalyzer.KanjiHistory> histories = new ArrayList<>();
        for (String kanji : candidates) {
            HistoricalKanjiSnapshot baseline = baselineKanjiSnapshot(db, kanji);
            KanjiImpactAnalyzer.MetricSnapshot current = currentByKanji.get(kanji);
            SameCardMetrics sameCards = baseline == null || baseline.syncId == latestSyncId
                    ? SameCardMetrics.EMPTY
                    : sameCardMetrics(db, kanji, baseline.syncId, latestSyncId);
            int commonCards = sameCards.current == null ? 0 : sameCards.current.totalCards();
            int currentCards = current == null ? 0 : current.totalCards();
            histories.add(new KanjiImpactAnalyzer.KanjiHistory(
                    kanji,
                    baseline == null ? null : baseline.metrics,
                    current,
                    sameCards.baseline,
                    sameCards.current,
                    commonCards,
                    Math.max(0, currentCards - commonCards),
                    reviewCounts.getOrDefault(kanji, 0)
            ));
        }
        return new KanjiImpactAnalyzer().analyze(histories);
    }

    private long latestSuccessfulSyncId(SQLiteDatabase db) {
        Cursor cursor = db.query(
                TABLE_SYNC_RUNS,
                new String[]{"id"},
                "status=?",
                new String[]{STATUS_SUCCESS},
                null,
                null,
                ORDER_ID_DESC,
                "1"
        );
        try {
            return cursor.moveToFirst() ? longValue(cursor, "id") : 0L;
        } finally {
            cursor.close();
        }
    }

    private Map<String, Integer> reviewCountsByKanji(SQLiteDatabase db) {
        Map<String, Integer> counts = new HashMap<>();
        Cursor cursor = db.rawQuery("SELECT kanji, COUNT(*) AS review_count FROM review_log GROUP BY kanji", null);
        try {
            while (cursor.moveToNext()) {
                counts.put(string(cursor, COLUMN_KANJI), integer(cursor, "review_count"));
            }
        } finally {
            cursor.close();
        }
        return counts;
    }

    private Set<String> impactCandidateKanji(SQLiteDatabase db, long latestSyncId) {
        Set<String> candidates = new HashSet<>();
        addKanjiFromCursor(candidates, db.query(
                TABLE_SYNC_KANJI_SNAPSHOTS,
                new String[]{COLUMN_KANJI},
                "sync_id=? AND (weakness_score>0 OR reason_code<>'' OR active_example_count>0 OR suspended_example_count>0)",
                new String[]{Long.toString(latestSyncId)},
                null,
                null,
                null
        ));
        addKanjiFromCursor(candidates, db.query(true, TABLE_STUDY_ITEMS, new String[]{COLUMN_KANJI}, null, null, null, null, null, null));
        addKanjiFromCursor(candidates, db.query(true, TABLE_SUSPENDED_IMPORTS, new String[]{COLUMN_KANJI}, null, null, null, null, null, null));
        return candidates;
    }

    private void addKanjiFromCursor(Set<String> candidates, Cursor cursor) {
        try {
            while (cursor.moveToNext()) {
                candidates.add(string(cursor, COLUMN_KANJI));
            }
        } finally {
            cursor.close();
        }
    }

    private Map<String, KanjiImpactAnalyzer.MetricSnapshot> kanjiMetricsForSync(SQLiteDatabase db, long syncId) {
        Map<String, KanjiImpactAnalyzer.MetricSnapshot> out = new LinkedHashMap<>();
        Cursor cursor = db.query(
                TABLE_SYNC_KANJI_SNAPSHOTS,
                null,
                "sync_id=?",
                new String[]{Long.toString(syncId)},
                null,
                null,
                ORDER_KANJI_ASC
        );
        try {
            while (cursor.moveToNext()) {
                out.put(string(cursor, COLUMN_KANJI), readKanjiImpactMetric(cursor));
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    private HistoricalKanjiSnapshot baselineKanjiSnapshot(SQLiteDatabase db, String kanji) {
        long startedAt = firstKaniSignalAt(db, kanji);
        if (startedAt <= 0L) {
            return firstKanjiSnapshot(db, kanji);
        }
        HistoricalKanjiSnapshot atOrAfterStart = firstKanjiSnapshotAtOrAfter(db, kanji, startedAt);
        if (atOrAfterStart != null) {
            return atOrAfterStart;
        }
        return latestKanjiSnapshotAtOrBefore(db, kanji, startedAt);
    }

    private long firstKaniSignalAt(SQLiteDatabase db, String kanji) {
        long first = minLongQuery(
                db,
                "SELECT MIN(occurred_at) FROM kanji_timeline_events WHERE kanji=?",
                new String[]{kanji}
        );
        long firstReview = minLongQuery(
                db,
                "SELECT MIN(reviewed_at) FROM review_log WHERE kanji=?",
                new String[]{kanji}
        );
        long firstStudyItem = minLongQuery(
                db,
                "SELECT MIN(created_at) FROM study_items WHERE kanji=?",
                new String[]{kanji}
        );
        long firstSuspendedImport = minLongQuery(
                db,
                "SELECT MIN(first_imported_at) FROM suspended_imports WHERE kanji=?",
                new String[]{kanji}
        );
        first = earliestPositive(first, firstReview);
        first = earliestPositive(first, firstStudyItem);
        return earliestPositive(first, firstSuspendedImport);
    }

    private long minLongQuery(SQLiteDatabase db, String sql, String[] args) {
        Cursor cursor = db.rawQuery(sql, args);
        try {
            cursor.moveToFirst();
            if (cursor.isNull(0)) {
                return 0L;
            }
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    private long earliestPositive(long left, long right) {
        if (left <= 0L) {
            return Math.max(0L, right);
        }
        if (right <= 0L) {
            return left;
        }
        return Math.min(left, right);
    }

    private HistoricalKanjiSnapshot firstKanjiSnapshotAtOrAfter(SQLiteDatabase db, String kanji, long startedAt) {
        Cursor cursor = db.query(
                TABLE_SYNC_KANJI_SNAPSHOTS,
                null,
                "kanji=? AND finished_at>=?",
                new String[]{kanji, Long.toString(startedAt)},
                null,
                null,
                "finished_at ASC, sync_id ASC",
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new HistoricalKanjiSnapshot(longValue(cursor, COLUMN_SYNC_ID), readKanjiImpactMetric(cursor));
        } finally {
            cursor.close();
        }
    }

    private HistoricalKanjiSnapshot latestKanjiSnapshotAtOrBefore(SQLiteDatabase db, String kanji, long startedAt) {
        Cursor cursor = db.query(
                TABLE_SYNC_KANJI_SNAPSHOTS,
                null,
                "kanji=? AND finished_at<=?",
                new String[]{kanji, Long.toString(startedAt)},
                null,
                null,
                "finished_at DESC, sync_id DESC",
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new HistoricalKanjiSnapshot(longValue(cursor, COLUMN_SYNC_ID), readKanjiImpactMetric(cursor));
        } finally {
            cursor.close();
        }
    }

    private HistoricalKanjiSnapshot firstKanjiSnapshot(SQLiteDatabase db, String kanji) {
        Cursor cursor = db.query(
                TABLE_SYNC_KANJI_SNAPSHOTS,
                null,
                "kanji=?",
                new String[]{kanji},
                null,
                null,
                "sync_id ASC",
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new HistoricalKanjiSnapshot(longValue(cursor, COLUMN_SYNC_ID), readKanjiImpactMetric(cursor));
        } finally {
            cursor.close();
        }
    }

    private KanjiImpactAnalyzer.MetricSnapshot readKanjiImpactMetric(Cursor cursor) {
        return new KanjiImpactAnalyzer.MetricSnapshot(
                integer(cursor, "active_cards"),
                integer(cursor, "suspended_cards"),
                integer(cursor, COLUMN_MATURE_SUPPORT_COUNT),
                cursor.getDouble(cursor.getColumnIndexOrThrow("average_interval_days")),
                integer(cursor, "total_reps"),
                integer(cursor, "total_lapses"),
                nullableDouble(cursor, "fsrs_stability_avg"),
                nullableDouble(cursor, "fsrs_difficulty_avg"),
                nullableDouble(cursor, "fsrs_retrievability_avg")
        );
    }

    private SameCardMetrics sameCardMetrics(SQLiteDatabase db, String kanji, long baselineSyncId, long currentSyncId) {
        HistoricalKanjiAggregate baseline = new HistoricalKanjiAggregate(kanji);
        HistoricalKanjiAggregate current = new HistoricalKanjiAggregate(kanji);
        Cursor cursor = db.rawQuery(
                "SELECT "
                        + "b.interval_days AS b_interval_days, b.reps AS b_reps, b.lapses AS b_lapses, b.suspended AS b_suspended, b.mature AS b_mature, b.fsrs_stability AS b_fsrs_stability, b.fsrs_difficulty AS b_fsrs_difficulty, b.fsrs_retrievability AS b_fsrs_retrievability, "
                        + "c.interval_days AS c_interval_days, c.reps AS c_reps, c.lapses AS c_lapses, c.suspended AS c_suspended, c.mature AS c_mature, c.fsrs_stability AS c_fsrs_stability, c.fsrs_difficulty AS c_fsrs_difficulty, c.fsrs_retrievability AS c_fsrs_retrievability "
                        + "FROM sync_card_snapshots b "
                        + "JOIN sync_card_snapshots c ON c.card_id=b.card_id "
                        + "JOIN sync_note_snapshots nb ON nb.sync_id=b.sync_id AND nb.note_id=b.note_id "
                        + "JOIN sync_note_snapshots nc ON nc.sync_id=c.sync_id AND nc.note_id=c.note_id "
                        + "WHERE b.sync_id=? AND c.sync_id=? AND instr(nb.extracted_kanji, ?) > 0 AND instr(nc.extracted_kanji, ?) > 0",
                new String[]{Long.toString(baselineSyncId), Long.toString(currentSyncId), kanji, kanji}
        );
        try {
            while (cursor.moveToNext()) {
                addCardMetrics(baseline, cursor, "b_");
                addCardMetrics(current, cursor, "c_");
            }
        } finally {
            cursor.close();
        }
        if (current.activeCards() + current.suspendedCards() == 0) {
            return SameCardMetrics.EMPTY;
        }
        return new SameCardMetrics(baseline.impactMetricSnapshot(), current.impactMetricSnapshot());
    }

    private void addCardMetrics(HistoricalKanjiAggregate aggregate, Cursor cursor, String prefix) {
        aggregate.addCard(
                integer(cursor, prefix + "interval_days"),
                integer(cursor, prefix + "reps"),
                integer(cursor, prefix + "lapses"),
                integer(cursor, prefix + "suspended") == 1,
                integer(cursor, prefix + "mature") == 1,
                new HistoricalKanjiAggregate.FsrsMemoryValues(
                        nullableDouble(cursor, prefix + "fsrs_stability"),
                        nullableDouble(cursor, prefix + "fsrs_difficulty"),
                        nullableDouble(cursor, prefix + "fsrs_retrievability")
                )
        );
    }

    private static String string(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    private static int integer(Cursor cursor, String column) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(column));
    }

    private static Double nullableDouble(Cursor cursor, String column) {
        int idx = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(idx) ? null : cursor.getDouble(idx);
    }

    private static long longValue(Cursor cursor, String column) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(column));
    }

    private record HistoricalKanjiSnapshot(long syncId, KanjiImpactAnalyzer.MetricSnapshot metrics) {
    }

    private record SameCardMetrics(
            KanjiImpactAnalyzer.MetricSnapshot baseline,
            KanjiImpactAnalyzer.MetricSnapshot current
    ) {
        private static final SameCardMetrics EMPTY = new SameCardMetrics(null, null);
    }

}
