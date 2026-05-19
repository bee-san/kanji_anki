package dev.bee.kanjianki.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.LocalDayPolicy;
import dev.bee.kanjianki.core.RecentMistakePolicy;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.StudyImpactPolicy;
import dev.bee.kanjianki.core.StudyStreakPolicy;
import dev.bee.kanjianki.core.StudyTaskTimingPolicy;
import dev.bee.kanjianki.sync.SyncSettings;

import java.util.ArrayList;
import java.util.List;

final class StudyStatsQueries {
    private static final String TABLE_REVIEW_LOG = "review_log";
    private static final String TABLE_SYNC_KANJI_SNAPSHOTS = "sync_kanji_snapshots";
    private static final String TABLE_STUDY_ITEMS = "study_items";
    private static final String COLUMN_KANJI = "kanji";
    private static final String COLUMN_RATING = "rating";
    private static final String COLUMN_REVIEWED_AT = "reviewed_at";
    private static final String STATE_RETIRED = "retired";

    private final LocalStore store;

    StudyStatsQueries(LocalStore store) {
        this.store = store;
    }

    StudyStatsStore.StudyTaskTimeStats studyTaskTimeStats(long nowMillis) {
        StudyTaskTimingPolicy.Window window = StudyTaskTimingPolicy.windowFor(nowMillis);
        Cursor cursor = db().rawQuery(
                "SELECT "
                        + "COALESCE(SUM(CASE WHEN answered_at>=? THEN active_elapsed_ms ELSE 0 END), 0) AS today_elapsed, "
                        + "COALESCE(SUM(active_elapsed_ms), 0) AS week_elapsed, "
                        + "COUNT(*) AS week_tasks "
                        + "FROM study_task_log WHERE answered_at>=? AND answered_at<?",
                new String[]{
                        Long.toString(window.todayStartMillis()),
                        Long.toString(window.sevenDayStartMillis()),
                        Long.toString(window.tomorrowStartMillis())
                }
        );
        try {
            cursor.moveToFirst();
            return new StudyStatsStore.StudyTaskTimeStats(
                    cursor.getLong(0),
                    cursor.getLong(1),
                    cursor.getInt(2)
            );
        } finally {
            cursor.close();
        }
    }

    List<StudyStatsStore.RecentMistake> recentMistakes(int limit) {
        Cursor cursor = db().query(
                TABLE_REVIEW_LOG,
                new String[]{COLUMN_KANJI, COLUMN_RATING, COLUMN_REVIEWED_AT},
                "rating IN (?, ?)",
                RecentMistakePolicy.mistakeRatings(),
                null,
                null,
                "reviewed_at DESC, id DESC",
                Integer.toString(RecentMistakePolicy.boundedLimit(limit))
        );
        List<StudyStatsStore.RecentMistake> mistakes = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                mistakes.add(new StudyStatsStore.RecentMistake(
                        string(cursor, COLUMN_KANJI),
                        string(cursor, COLUMN_RATING),
                        longValue(cursor, COLUMN_REVIEWED_AT)
                ));
            }
        } finally {
            cursor.close();
        }
        return mistakes;
    }

    StudyStatsStore.StudyStreak studyStreak(long nowMillis) {
        long today = localDayStart(nowMillis);
        StudyDays studyDays = studyDays(today);
        StudyStreakPolicy.Streak streak = StudyStreakPolicy.summarize(
                studyDays.days,
                today,
                studyDays.reviewsToday,
                studyDays.lastStudyAt
        );
        return new StudyStatsStore.StudyStreak(
                streak.currentDays(),
                streak.bestDays(),
                streak.studiedToday(),
                streak.reviewsToday(),
                streak.lastStudyAtMillis()
        );
    }

    StudyStatsStore.StudyImpactStats studyImpactStats() {
        Cursor cursor = db().rawQuery(
                "SELECT "
                        + "COUNT(*) AS total_reviews, "
                        + "COUNT(DISTINCT kanji) AS distinct_kanji, "
                        + "COALESCE(SUM(CASE WHEN writing_required=1 THEN 1 ELSE 0 END), 0) AS writing_required_count, "
                        + "COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=1 THEN 1 ELSE 0 END), 0) AS writing_passed_count, "
                        + "COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=0 AND manual_override=0 THEN 1 ELSE 0 END), 0) AS writing_failed_count, "
                        + "COALESCE(SUM(CASE WHEN manual_override=1 THEN 1 ELSE 0 END), 0) AS manual_override_count "
                        + "FROM " + TABLE_REVIEW_LOG,
                null
        );
        try {
            cursor.moveToFirst();
            return new StudyStatsStore.StudyImpactStats(
                    cursor.getInt(0),
                    cursor.getInt(1),
                    cursor.getInt(2),
                    cursor.getInt(3),
                    cursor.getInt(4),
                    cursor.getInt(5)
            );
        } finally {
            cursor.close();
        }
    }

    StudyStatsStore.KaniOutcomeStats kaniOutcomeStats() {
        int promotionDays = Math.max(1, ladderPromotionIntervalDays());
        int failStreak = Math.max(1, ladderDemotionFailStreak());
        return StudyStatsStore.calculateKaniOutcomeStats(
                outcomeEvidence(db()),
                ladderItems(db()),
                promotionDays,
                failStreak
        );
    }

    private SQLiteDatabase db() {
        return store.getReadableDatabase();
    }

    private List<StudyStatsStore.OutcomeEvidence> outcomeEvidence(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery(
                "SELECT rw.kanji, "
                        + "(SELECT s.weakness_score FROM " + TABLE_SYNC_KANJI_SNAPSHOTS + " s WHERE s.kanji=rw.kanji AND s.finished_at<rw.first_reviewed_at ORDER BY s.finished_at DESC, s.sync_id DESC LIMIT 1) AS before_weakness, "
                        + "(SELECT s.mature_support_count FROM " + TABLE_SYNC_KANJI_SNAPSHOTS + " s WHERE s.kanji=rw.kanji AND s.finished_at<rw.first_reviewed_at ORDER BY s.finished_at DESC, s.sync_id DESC LIMIT 1) AS before_support, "
                        + "(SELECT s.weakness_score FROM " + TABLE_SYNC_KANJI_SNAPSHOTS + " s WHERE s.kanji=rw.kanji AND s.finished_at>rw.last_reviewed_at ORDER BY s.finished_at DESC, s.sync_id DESC LIMIT 1) AS after_weakness, "
                        + "(SELECT s.mature_support_count FROM " + TABLE_SYNC_KANJI_SNAPSHOTS + " s WHERE s.kanji=rw.kanji AND s.finished_at>rw.last_reviewed_at ORDER BY s.finished_at DESC, s.sync_id DESC LIMIT 1) AS after_support "
                        + "FROM (SELECT kanji, MIN(reviewed_at) AS first_reviewed_at, MAX(reviewed_at) AS last_reviewed_at "
                        + "FROM " + TABLE_REVIEW_LOG + " WHERE kanji<>'' GROUP BY kanji) rw",
                null
        );
        List<StudyStatsStore.OutcomeEvidence> out = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, COLUMN_KANJI);
                out.add(new StudyStatsStore.OutcomeEvidence(
                        kanji,
                        outcomeSnapshot(cursor, 1, 2),
                        outcomeSnapshot(cursor, 3, 4)
                ));
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    private List<StudyStatsStore.LadderItemEvidence> ladderItems(SQLiteDatabase db) {
        Cursor cursor = db.query(
                TABLE_STUDY_ITEMS,
                new String[]{LocalStoreBase.COLUMN_STATE, LocalStoreBase.COLUMN_RUNG, LocalStoreBase.COLUMN_PHASE, LocalStoreBase.COLUMN_REAL_PASS_STREAK, LocalStoreBase.COLUMN_REAL_AGAIN_STREAK, LocalStoreBase.COLUMN_MATURE_INTERVAL_DAYS},
                "state<>?",
                new String[]{STATE_RETIRED},
                null,
                null,
                null
        );
        List<StudyStatsStore.LadderItemEvidence> out = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                out.add(new StudyStatsStore.LadderItemEvidence(
                        string(cursor, LocalStoreBase.COLUMN_STATE),
                        RecordsBase.LadderRung.fromWireName(string(cursor, LocalStoreBase.COLUMN_RUNG)),
                        RecordsBase.SchedulerPhase.fromWireName(string(cursor, LocalStoreBase.COLUMN_PHASE)),
                        integer(cursor, LocalStoreBase.COLUMN_REAL_PASS_STREAK),
                        integer(cursor, LocalStoreBase.COLUMN_REAL_AGAIN_STREAK),
                        integer(cursor, LocalStoreBase.COLUMN_MATURE_INTERVAL_DAYS)
                ));
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    private StudyDays studyDays(long today) {
        Cursor cursor = db().rawQuery(
                "SELECT review_day_start, COUNT(*) AS review_count, MAX(reviewed_at) AS last_reviewed_at "
                        + "FROM " + TABLE_REVIEW_LOG + " WHERE review_day_start>0 "
                        + "GROUP BY review_day_start ORDER BY review_day_start DESC",
                null
        );
        List<Long> days = new ArrayList<>();
        int reviewsToday = 0;
        long lastStudyAt = 0L;
        try {
            while (cursor.moveToNext()) {
                long day = cursor.getLong(0);
                if (lastStudyAt == 0L) {
                    lastStudyAt = cursor.getLong(2);
                }
                if (day == today) {
                    reviewsToday = cursor.getInt(1);
                }
                days.add(day);
            }
        } finally {
            cursor.close();
        }
        return new StudyDays(days, reviewsToday, lastStudyAt);
    }

    private int ladderPromotionIntervalDays() {
        return store.getIntSetting(
                SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY,
                RecordsSyncModels.Settings.kikuDefaults().ladderPromotionIntervalDays
        );
    }

    private int ladderDemotionFailStreak() {
        return store.getIntSetting(
                SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY,
                realDueReviewsToMove()
        );
    }

    private int realDueReviewsToMove() {
        return store.getIntSetting(
                SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY,
                RecordsSyncModels.Settings.kikuDefaults().realDueReviewsToMove
        );
    }

    private static StudyStatsStore.OutcomeSnapshot outcomeSnapshot(Cursor cursor, int weaknessColumnIndex, int supportColumnIndex) {
        if (cursor.isNull(weaknessColumnIndex) || cursor.isNull(supportColumnIndex)) {
            return null;
        }
        return new StudyStatsStore.OutcomeSnapshot(cursor.getInt(weaknessColumnIndex), cursor.getInt(supportColumnIndex));
    }

    private static long localDayStart(long millis) {
        return LocalDayPolicy.localDayStart(millis);
    }

    private static String string(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    private static long longValue(Cursor cursor, String column) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(column));
    }

    private static int integer(Cursor cursor, String column) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(column));
    }

    private record StudyDays(List<Long> days, int reviewsToday, long lastStudyAt) {
    }
}
