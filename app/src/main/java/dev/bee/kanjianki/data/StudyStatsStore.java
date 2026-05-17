package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.sync.SyncSettings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StudyStatsStore {
    private static final String TABLE_REVIEW_LOG = "review_log";
    private static final String TABLE_SYNC_KANJI_SNAPSHOTS = "sync_kanji_snapshots";
    private static final String TABLE_STUDY_ITEMS = "study_items";
    private static final String COLUMN_KANJI = "kanji";
    private static final String COLUMN_RATING = "rating";
    private static final String COLUMN_REVIEWED_AT = "reviewed_at";
    private static final String COLUMN_STATE = "state";
    private static final String COLUMN_RUNG = "rung";
    private static final String COLUMN_PHASE = "phase";
    private static final String COLUMN_REAL_PASS_STREAK = "real_pass_streak";
    private static final String COLUMN_REAL_AGAIN_STREAK = "real_again_streak";
    private static final String COLUMN_MATURE_INTERVAL_DAYS = "mature_interval_days";
    private static final String RATING_AGAIN = "again";
    private static final String STATE_RETIRED = "retired";
    private final LocalStore store;

    public StudyStatsStore(LocalStore store) {
        this.store = store;
    }

    public StudyTaskTimeStats studyTaskTimeStats(long nowMillis) {
        long today = localDayStart(nowMillis);
        long tomorrow = moveLocalDays(today, 1);
        long sevenDayStart = moveLocalDays(today, -6);
        Cursor cursor = db().rawQuery(
                "SELECT "
                        + "COALESCE(SUM(CASE WHEN answered_at>=? THEN active_elapsed_ms ELSE 0 END), 0) AS today_elapsed, "
                        + "COALESCE(SUM(active_elapsed_ms), 0) AS week_elapsed, "
                        + "COUNT(*) AS week_tasks "
                        + "FROM study_task_log WHERE answered_at>=? AND answered_at<?",
                new String[]{Long.toString(today), Long.toString(sevenDayStart), Long.toString(tomorrow)}
        );
        try {
            cursor.moveToFirst();
            return new StudyTaskTimeStats(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2));
        } finally {
            cursor.close();
        }
    }

    public List<RecentMistake> recentMistakes(int limit) {
        int boundedLimit = Math.max(1, limit);
        Cursor cursor = db().query(
                TABLE_REVIEW_LOG,
                new String[]{COLUMN_KANJI, COLUMN_RATING, COLUMN_REVIEWED_AT},
                "rating IN (?, ?)",
                new String[]{RATING_AGAIN, "hard"},
                null,
                null,
                "reviewed_at DESC, id DESC",
                Integer.toString(boundedLimit)
        );
        List<RecentMistake> mistakes = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                mistakes.add(new RecentMistake(
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

    public StudyStreak studyStreak(long nowMillis) {
        long today = localDayStart(nowMillis);
        StudyDays studyDays = studyDays(today);
        if (studyDays.days.isEmpty()) {
            return new StudyStreak(0, 0, false, 0, 0L);
        }
        boolean studiedToday = studyDays.days.get(0) == today;
        return new StudyStreak(
                currentStreak(studyDays.days, today),
                bestStreak(studyDays.days),
                studiedToday,
                studyDays.reviewsToday,
                studyDays.lastStudyAt
        );
    }

    public StudyImpactStats studyImpactStats() {
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
            return new StudyImpactStats(
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

    public KaniOutcomeStats kaniOutcomeStats() {
        SQLiteDatabase db = db();
        int promotionDays = Math.max(1, ladderPromotionIntervalDays());
        int failStreak = Math.max(1, ladderDemotionFailStreak());
        return calculateKaniOutcomeStats(outcomeEvidence(db), ladderHealth(db, promotionDays, failStreak));
    }

    static KaniOutcomeStats calculateKaniOutcomeStats(List<OutcomeEvidence> outcomeEvidence, List<LadderItemEvidence> ladderItems, int realDueReviewsToMove) {
        return calculateKaniOutcomeStats(
                outcomeEvidence,
                ladderHealth(safeList(ladderItems), RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS, realDueReviewsToMove)
        );
    }

    static KaniOutcomeStats calculateKaniOutcomeStats(
            List<OutcomeEvidence> outcomeEvidence,
            List<LadderItemEvidence> ladderItems,
            int ladderPromotionIntervalDays,
            int ladderDemotionFailStreak
    ) {
        return calculateKaniOutcomeStats(outcomeEvidence, ladderHealth(safeList(ladderItems), ladderPromotionIntervalDays, ladderDemotionFailStreak));
    }

    private static KaniOutcomeStats calculateKaniOutcomeStats(List<OutcomeEvidence> outcomeEvidence, LadderHealthMetric ladderHealth) {
        OutcomeAccumulator accumulator = new OutcomeAccumulator();
        for (OutcomeEvidence evidence : safeList(outcomeEvidence)) {
            accumulator.add(evidence.kanji, evidence.before, evidence.after);
        }
        accumulator.improvements.sort((left, right) -> {
            int dropCompare = Double.compare(right.beforeWeakness - right.afterWeakness, left.beforeWeakness - left.afterWeakness);
            return dropCompare == 0 ? left.kanji.compareTo(right.kanji) : dropCompare;
        });
        accumulator.supportGains.sort((left, right) -> {
            int gainCompare = Integer.compare(right.afterMatureSupport - right.beforeMatureSupport, left.afterMatureSupport - left.beforeMatureSupport);
            return gainCompare == 0 ? left.kanji.compareTo(right.kanji) : gainCompare;
        });

        int improvedCount = accumulator.improvements.size();
        WeakKanjiImprovedMetric weakMetric = new WeakKanjiImprovedMetric(
                improvedCount,
                improvedCount == 0 ? 0.0 : accumulator.beforeWeaknessSum / improvedCount,
                improvedCount == 0 ? 0.0 : accumulator.afterWeaknessSum / improvedCount,
                topThreeImprovements(accumulator.improvements)
        );
        MatureSupportGainedMetric supportMetric = new MatureSupportGainedMetric(
                accumulator.supportGains.size(),
                accumulator.matureSupportGainSum,
                accumulator.firstSupportCount,
                topThreeSupportGains(accumulator.supportGains)
        );
        return new KaniOutcomeStats(weakMetric, supportMetric, ladderHealth);
    }

    private SQLiteDatabase db() {
        return store.getReadableDatabase();
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

    private int currentStreak(List<Long> days, long today) {
        long yesterday = moveLocalDays(today, -1);
        boolean studiedToday = days.get(0) == today;
        if (!studiedToday && days.get(0) != yesterday) {
            return 0;
        }
        long expected = studiedToday ? today : yesterday;
        int current = 0;
        for (long day : days) {
            if (day != expected) {
                break;
            }
            current++;
            expected = moveLocalDays(expected, -1);
        }
        return current;
    }

    private int bestStreak(List<Long> days) {
        int best = 0;
        int run = 0;
        long expectedPrevious = Long.MIN_VALUE;
        for (int i = days.size() - 1; i >= 0; i--) {
            long day = days.get(i);
            if (run == 0 || day == moveLocalDays(expectedPrevious, 1)) {
                run++;
            } else {
                run = 1;
            }
            best = Math.max(best, run);
            expectedPrevious = day;
        }
        return best;
    }

    private List<OutcomeEvidence> outcomeEvidence(SQLiteDatabase db) {
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
        List<OutcomeEvidence> out = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, COLUMN_KANJI);
                out.add(new OutcomeEvidence(
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

    private LadderHealthMetric ladderHealth(SQLiteDatabase db, int promotionDays, int failStreak) {
        Cursor cursor = db.rawQuery(
                "SELECT rung, "
                        + "COUNT(*) AS total_count, "
                        + "SUM(CASE WHEN phase='review' AND mature_interval_days>? THEN 1 ELSE 0 END) AS promotion_ready, "
                        + "SUM(CASE WHEN phase='review' AND real_again_streak>0 THEN 1 ELSE 0 END) AS demotion_risk, "
                        + "SUM(CASE WHEN phase='review' AND real_again_streak>=? THEN 1 ELSE 0 END) AS demotion_ready "
                        + "FROM " + TABLE_STUDY_ITEMS + " WHERE state<>? GROUP BY rung",
                new String[]{Integer.toString(Math.max(1, promotionDays)), Integer.toString(Math.max(1, failStreak)), STATE_RETIRED}
        );
        Map<RecordsBase.LadderRung, Integer> distribution = emptyRungDistribution();
        int total = 0;
        int promotionReady = 0;
        int demotionRisk = 0;
        int demotionReady = 0;
        try {
            while (cursor.moveToNext()) {
                RecordsBase.LadderRung rung = RecordsBase.LadderRung.fromWireName(cursor.getString(0));
                int count = cursor.getInt(1);
                distribution.put(rung, distribution.get(rung) + count);
                total += count;
                promotionReady += cursor.getInt(2);
                demotionRisk += cursor.getInt(3);
                demotionReady += cursor.getInt(4);
            }
        } finally {
            cursor.close();
        }
        return new LadderHealthMetric(distribution, total, promotionDays, failStreak, promotionReady, demotionRisk, demotionReady);
    }

    private List<LadderItemEvidence> ladderItemEvidence(SQLiteDatabase db) {
        Cursor cursor = db.query(
                TABLE_STUDY_ITEMS,
                new String[]{COLUMN_STATE, COLUMN_RUNG, COLUMN_PHASE, COLUMN_REAL_PASS_STREAK, COLUMN_REAL_AGAIN_STREAK, COLUMN_MATURE_INTERVAL_DAYS},
                null,
                null,
                null,
                null,
                null
        );
        List<LadderItemEvidence> out = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                out.add(new LadderItemEvidence(
                        string(cursor, COLUMN_STATE),
                        RecordsBase.LadderRung.fromWireName(string(cursor, COLUMN_RUNG)),
                        RecordsBase.SchedulerPhase.fromWireName(string(cursor, COLUMN_PHASE)),
                        integer(cursor, COLUMN_REAL_PASS_STREAK),
                        integer(cursor, COLUMN_REAL_AGAIN_STREAK),
                        integer(cursor, COLUMN_MATURE_INTERVAL_DAYS)
                ));
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    private int realDueReviewsToMove() {
        return store.getIntSetting(
                SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY,
                RecordsSyncModels.Settings.kikuDefaults().realDueReviewsToMove
        );
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

    private static LadderHealthMetric ladderHealth(List<LadderItemEvidence> items, int ladderPromotionIntervalDays, int ladderDemotionFailStreak) {
        int promotionDays = Math.max(1, ladderPromotionIntervalDays);
        int failStreak = Math.max(1, ladderDemotionFailStreak);
        LadderHealthAccumulator accumulator = new LadderHealthAccumulator();
        for (LadderItemEvidence item : items) {
            accumulator.addItem(item, promotionDays, failStreak);
        }
        return accumulator.metric(promotionDays, failStreak);
    }

    private static Map<RecordsBase.LadderRung, Integer> emptyRungDistribution() {
        Map<RecordsBase.LadderRung, Integer> out = new LinkedHashMap<>();
        for (RecordsBase.LadderRung rung : RecordsBase.LadderRung.values()) {
            out.put(rung, 0);
        }
        return out;
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? Collections.emptyList() : value;
    }

    private static OutcomeSnapshot outcomeSnapshot(Cursor cursor, int weaknessColumnIndex, int supportColumnIndex) {
        if (cursor.isNull(weaknessColumnIndex) || cursor.isNull(supportColumnIndex)) {
            return null;
        }
        return new OutcomeSnapshot(cursor.getInt(weaknessColumnIndex), cursor.getInt(supportColumnIndex));
    }

    private static List<KanjiImprovement> topThreeImprovements(List<KanjiImprovement> improvements) {
        return new ArrayList<>(improvements.subList(0, Math.min(3, improvements.size())));
    }

    private static List<KanjiSupportGain> topThreeSupportGains(List<KanjiSupportGain> supportGains) {
        return new ArrayList<>(supportGains.subList(0, Math.min(3, supportGains.size())));
    }

    private static long localDayStart(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static long moveLocalDays(long localDayStart, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(localDayStart);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static String string(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    private static int integer(Cursor cursor, String column) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(column));
    }

    private static long longValue(Cursor cursor, String column) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(column));
    }

    public static final class StudyStreak {
        public final int currentDays;
        public final int bestDays;
        public final boolean studiedToday;
        public final int reviewsToday;
        public final long lastStudyAtMillis;

        public StudyStreak(int currentDays, int bestDays, boolean studiedToday, int reviewsToday, long lastStudyAtMillis) {
            this.currentDays = currentDays;
            this.bestDays = bestDays;
            this.studiedToday = studiedToday;
            this.reviewsToday = reviewsToday;
            this.lastStudyAtMillis = lastStudyAtMillis;
        }
    }

    public static final class StudyImpactStats {
        public final int totalReviews;
        public final int distinctReviewedKanji;
        public final int writingRequired;
        public final int writingPassed;
        public final int writingFailed;
        public final int manualOverrides;

        public StudyImpactStats(int totalReviews, int distinctReviewedKanji, int writingRequired, int writingPassed, int writingFailed, int manualOverrides) {
            this.totalReviews = totalReviews;
            this.distinctReviewedKanji = distinctReviewedKanji;
            this.writingRequired = writingRequired;
            this.writingPassed = writingPassed;
            this.writingFailed = writingFailed;
            this.manualOverrides = manualOverrides;
        }
    }

    public static final class StudyTaskTimeStats {
        public final long todayMillis;
        public final long lastSevenDaysMillis;
        public final int answeredTasks;

        public StudyTaskTimeStats(long todayMillis, long lastSevenDaysMillis, int answeredTasks) {
            this.todayMillis = Math.max(0L, todayMillis);
            this.lastSevenDaysMillis = Math.max(0L, lastSevenDaysMillis);
            this.answeredTasks = Math.max(0, answeredTasks);
        }

        public long averageMillisPerTask() {
            if (answeredTasks == 0) {
                return 0L;
            }
            return lastSevenDaysMillis / answeredTasks;
        }
    }

    public static final class KaniOutcomeStats {
        public final WeakKanjiImprovedMetric weakKanjiImproved;
        public final MatureSupportGainedMetric matureSupportGained;
        public final LadderHealthMetric ladderHealth;

        public KaniOutcomeStats(WeakKanjiImprovedMetric weakKanjiImproved, MatureSupportGainedMetric matureSupportGained) {
            this(weakKanjiImproved, matureSupportGained, LadderHealthMetric.empty());
        }

        public KaniOutcomeStats(WeakKanjiImprovedMetric weakKanjiImproved, MatureSupportGainedMetric matureSupportGained, LadderHealthMetric ladderHealth) {
            this.weakKanjiImproved = weakKanjiImproved == null ? WeakKanjiImprovedMetric.empty() : weakKanjiImproved;
            this.matureSupportGained = matureSupportGained == null ? MatureSupportGainedMetric.empty() : matureSupportGained;
            this.ladderHealth = ladderHealth == null ? LadderHealthMetric.empty() : ladderHealth;
        }

        public static KaniOutcomeStats empty() {
            return new KaniOutcomeStats(WeakKanjiImprovedMetric.empty(), MatureSupportGainedMetric.empty(), LadderHealthMetric.empty());
        }
    }

    public static final class WeakKanjiImprovedMetric {
        public final int improvedCount;
        public final double averageBeforeWeakness;
        public final double averageAfterWeakness;
        public final List<KanjiImprovement> examples;

        public WeakKanjiImprovedMetric(int improvedCount, double averageBeforeWeakness, double averageAfterWeakness, List<KanjiImprovement> examples) {
            this.improvedCount = Math.max(0, improvedCount);
            this.averageBeforeWeakness = Math.max(0.0, averageBeforeWeakness);
            this.averageAfterWeakness = Math.max(0.0, averageAfterWeakness);
            this.examples = Collections.unmodifiableList(new ArrayList<>(examples == null ? Collections.emptyList() : examples));
        }

        public static WeakKanjiImprovedMetric empty() {
            return new WeakKanjiImprovedMetric(0, 0.0, 0.0, Collections.emptyList());
        }
    }

    public static final class KanjiImprovement {
        public final String kanji;
        public final double beforeWeakness;
        public final double afterWeakness;

        public KanjiImprovement(String kanji, double beforeWeakness, double afterWeakness) {
            this.kanji = kanji == null ? "" : kanji;
            this.beforeWeakness = Math.max(0.0, beforeWeakness);
            this.afterWeakness = Math.max(0.0, afterWeakness);
        }
    }

    public static final class MatureSupportGainedMetric {
        public final int gainedSupportCount;
        public final int matureSupportGained;
        public final int firstSupportCount;
        public final List<KanjiSupportGain> examples;

        public MatureSupportGainedMetric(int gainedSupportCount, int firstSupportCount, List<KanjiSupportGain> examples) {
            this(gainedSupportCount, gainedSupportCount, firstSupportCount, examples);
        }

        public MatureSupportGainedMetric(int gainedSupportCount, int matureSupportGained, int firstSupportCount, List<KanjiSupportGain> examples) {
            this.gainedSupportCount = Math.max(0, gainedSupportCount);
            this.matureSupportGained = Math.max(0, matureSupportGained);
            this.firstSupportCount = Math.max(0, firstSupportCount);
            this.examples = Collections.unmodifiableList(new ArrayList<>(examples == null ? Collections.emptyList() : examples));
        }

        public static MatureSupportGainedMetric empty() {
            return new MatureSupportGainedMetric(0, 0, 0, Collections.emptyList());
        }
    }

    public static final class KanjiSupportGain {
        public final String kanji;
        public final int beforeMatureSupport;
        public final int afterMatureSupport;

        public KanjiSupportGain(String kanji, int beforeMatureSupport, int afterMatureSupport) {
            this.kanji = kanji == null ? "" : kanji;
            this.beforeMatureSupport = Math.max(0, beforeMatureSupport);
            this.afterMatureSupport = Math.max(0, afterMatureSupport);
        }
    }

    public static final class LadderHealthMetric {
        public final Map<RecordsBase.LadderRung, Integer> rungCounts;
        public final int totalActiveItems;
        public final int realDueReviewsToMove;
        public final int ladderPromotionIntervalDays;
        public final int ladderDemotionFailStreak;
        public final int promotionReadyCount;
        public final int demotionRiskCount;
        public final int demotionReadyCount;

        public LadderHealthMetric(
                Map<RecordsBase.LadderRung, Integer> rungCounts,
                int totalActiveItems,
                int realDueReviewsToMove,
                int promotionReadyCount,
                int demotionRiskCount,
                int demotionReadyCount
        ) {
            this(
                    rungCounts,
                    totalActiveItems,
                    RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
                    realDueReviewsToMove,
                    promotionReadyCount,
                    demotionRiskCount,
                    demotionReadyCount
            );
        }

        public LadderHealthMetric(
                Map<RecordsBase.LadderRung, Integer> rungCounts,
                int totalActiveItems,
                int ladderPromotionIntervalDays,
                int ladderDemotionFailStreak,
                int promotionReadyCount,
                int demotionRiskCount,
                int demotionReadyCount
        ) {
            Map<RecordsBase.LadderRung, Integer> normalized = emptyRungDistribution();
            if (rungCounts != null) {
                for (Map.Entry<RecordsBase.LadderRung, Integer> entry : rungCounts.entrySet()) {
                    if (entry.getKey() != null) {
                        normalized.put(entry.getKey(), Math.max(0, entry.getValue() == null ? 0 : entry.getValue()));
                    }
                }
            }
            this.rungCounts = Collections.unmodifiableMap(normalized);
            this.totalActiveItems = Math.max(0, totalActiveItems);
            this.ladderPromotionIntervalDays = Math.max(1, ladderPromotionIntervalDays);
            this.ladderDemotionFailStreak = Math.max(1, ladderDemotionFailStreak);
            this.realDueReviewsToMove = this.ladderDemotionFailStreak;
            this.promotionReadyCount = Math.max(0, promotionReadyCount);
            this.demotionRiskCount = Math.max(0, demotionRiskCount);
            this.demotionReadyCount = Math.max(0, demotionReadyCount);
        }

        public int countFor(RecordsBase.LadderRung rung) {
            Integer count = rungCounts.get(rung);
            return count == null ? 0 : count;
        }

        public static LadderHealthMetric empty() {
            RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
            return new LadderHealthMetric(
                    emptyRungDistribution(),
                    0,
                    defaults.ladderPromotionIntervalDays,
                    defaults.ladderDemotionFailStreak,
                    0,
                    0,
                    0
            );
        }
    }

    private static final class LadderHealthAccumulator {
        private final Map<RecordsBase.LadderRung, Integer> distribution = emptyRungDistribution();
        private int total;
        private int promotionReady;
        private int demotionRisk;
        private int demotionReady;

        private void addItem(LadderItemEvidence item, int promotionDays, int failStreak) {
            if (item == null || STATE_RETIRED.equals(item.state)) {
                return;
            }
            distribution.put(item.rung, distribution.get(item.rung) + 1);
            total++;
            if (item.phase == RecordsBase.SchedulerPhase.REVIEW) {
                recordReviewEvidence(item, promotionDays, failStreak);
            }
        }

        private void recordReviewEvidence(LadderItemEvidence item, int promotionDays, int failStreak) {
            if (item.matureIntervalDays > promotionDays) {
                promotionReady++;
            }
            if (item.realAgainStreak > 0) {
                demotionRisk++;
            }
            if (item.realAgainStreak >= failStreak) {
                demotionReady++;
            }
        }

        private LadderHealthMetric metric(int promotionDays, int failStreak) {
            return new LadderHealthMetric(distribution, total, promotionDays, failStreak, promotionReady, demotionRisk, demotionReady);
        }
    }

    public static final class RecentMistake {
        public final String kanji;
        public final String rating;
        public final long reviewedAtMillis;

        public RecentMistake(String kanji, String rating, long reviewedAtMillis) {
            this.kanji = kanji == null ? "" : kanji;
            this.rating = rating == null ? "" : rating;
            this.reviewedAtMillis = reviewedAtMillis;
        }
    }

    private record StudyDays(List<Long> days, int reviewsToday, long lastStudyAt) {
    }

    record OutcomeSnapshot(int weaknessScore, int matureSupportCount) {
        OutcomeSnapshot {
            weaknessScore = Math.max(0, weaknessScore);
            matureSupportCount = Math.max(0, matureSupportCount);
        }
    }

    record OutcomeEvidence(String kanji, OutcomeSnapshot before, OutcomeSnapshot after) {
        OutcomeEvidence {
            kanji = kanji == null ? "" : kanji;
        }
    }

    record LadderItemEvidence(
            String state,
            RecordsBase.LadderRung rung,
            RecordsBase.SchedulerPhase phase,
            int realPassStreak,
            int realAgainStreak,
            int matureIntervalDays
    ) {
        LadderItemEvidence(
                String state,
                RecordsBase.LadderRung rung,
                RecordsBase.SchedulerPhase phase,
                int realPassStreak,
                int realAgainStreak
        ) {
            this(state, rung, phase, realPassStreak, realAgainStreak, 0);
        }

        LadderItemEvidence {
            state = state == null ? "" : state;
            rung = rung == null ? RecordsBase.LadderRung.KANJI_MEANING : rung;
            phase = phase == null ? RecordsBase.SchedulerPhase.NEW_LEARNING : phase;
            realPassStreak = Math.max(0, realPassStreak);
            realAgainStreak = Math.max(0, realAgainStreak);
            matureIntervalDays = Math.max(0, matureIntervalDays);
        }
    }

    private static final class OutcomeAccumulator {
        private final List<KanjiImprovement> improvements = new ArrayList<>();
        private final List<KanjiSupportGain> supportGains = new ArrayList<>();
        private double beforeWeaknessSum;
        private double afterWeaknessSum;
        private int matureSupportGainSum;
        private int firstSupportCount;

        private void add(String kanji, OutcomeSnapshot before, OutcomeSnapshot after) {
            if (before == null || after == null) {
                return;
            }
            addImprovement(kanji, before, after);
            addSupportGain(kanji, before, after);
        }

        private void addImprovement(String kanji, OutcomeSnapshot before, OutcomeSnapshot after) {
            int weaknessDrop = before.weaknessScore - after.weaknessScore;
            if (before.weaknessScore <= 0 || weaknessDrop < 5) {
                return;
            }
            double beforeWeakness = normalizedWeakness(before.weaknessScore);
            double afterWeakness = normalizedWeakness(after.weaknessScore);
            improvements.add(new KanjiImprovement(kanji, beforeWeakness, afterWeakness));
            beforeWeaknessSum += beforeWeakness;
            afterWeaknessSum += afterWeakness;
        }

        private static double normalizedWeakness(int weaknessScore) {
            return Math.max(0, weaknessScore) / 100.0;
        }

        private void addSupportGain(String kanji, OutcomeSnapshot before, OutcomeSnapshot after) {
            int supportGain = after.matureSupportCount - before.matureSupportCount;
            if (supportGain <= 0) {
                return;
            }
            supportGains.add(new KanjiSupportGain(kanji, before.matureSupportCount, after.matureSupportCount));
            matureSupportGainSum += supportGain;
            if (before.matureSupportCount == 0) {
                firstSupportCount++;
            }
        }
    }
}
