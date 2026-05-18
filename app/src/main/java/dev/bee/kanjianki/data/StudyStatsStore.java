package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.KaniOutcomePolicy;
import dev.bee.kanjianki.core.LocalDayPolicy;
import dev.bee.kanjianki.core.LadderHealthPolicy;
import dev.bee.kanjianki.core.RecentMistakePolicy;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.StudyStreakPolicy;
import dev.bee.kanjianki.core.StudyTaskTimingPolicy;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.sync.SyncSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class StudyStatsStore {
    private static final String TABLE_REVIEW_LOG = "review_log";
    private static final String TABLE_SYNC_KANJI_SNAPSHOTS = "sync_kanji_snapshots";
    private static final String TABLE_STUDY_ITEMS = "study_items";
    private static final String COLUMN_KANJI = "kanji";
    private static final String COLUMN_RATING = "rating";
    private static final String COLUMN_REVIEWED_AT = "reviewed_at";
    private static final String STATE_RETIRED = "retired";
    private final LocalStore store;

    public StudyStatsStore(LocalStore store) {
        this.store = store;
    }

    public StudyTaskTimeStats studyTaskTimeStats(long nowMillis) {
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
            return StudyTaskTimeStats.fromCore(StudyTaskTimingPolicy.summarize(
                    cursor.getLong(0),
                    cursor.getLong(1),
                    cursor.getInt(2)
            ));
        } finally {
            cursor.close();
        }
    }

    public List<RecentMistake> recentMistakes(int limit) {
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
        List<RecentMistake> mistakes = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                mistakes.add(RecentMistake.fromCore(RecentMistakePolicy.mistake(
                        string(cursor, COLUMN_KANJI),
                        string(cursor, COLUMN_RATING),
                        longValue(cursor, COLUMN_REVIEWED_AT)
                )));
            }
        } finally {
            cursor.close();
        }
        return mistakes;
    }

    public StudyStreak studyStreak(long nowMillis) {
        long today = localDayStart(nowMillis);
        StudyDays studyDays = studyDays(today);
        StudyStreakPolicy.Streak streak = StudyStreakPolicy.summarize(
                studyDays.days,
                today,
                studyDays.reviewsToday,
                studyDays.lastStudyAt
        );
        return new StudyStreak(
                streak.currentDays(),
                streak.bestDays(),
                streak.studiedToday(),
                streak.reviewsToday(),
                streak.lastStudyAtMillis()
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
        return toAppOutcomeStats(KaniOutcomePolicy.summarize(toCoreOutcomeEvidence(outcomeEvidence), toCoreMetric(ladderHealth)));
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
        return toAppMetric(LadderHealthPolicy.fromCounts(
                distribution,
                total,
                promotionDays,
                failStreak,
                promotionReady,
                demotionRisk,
                demotionReady
        ));
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
        return toAppMetric(LadderHealthPolicy.summarize(toCoreLadderItems(items), ladderPromotionIntervalDays, ladderDemotionFailStreak));
    }

    private static Map<RecordsBase.LadderRung, Integer> emptyRungDistribution() {
        return LadderHealthPolicy.emptyRungDistribution();
    }

    private static List<LadderHealthPolicy.ItemEvidence> toCoreLadderItems(List<LadderItemEvidence> items) {
        List<LadderHealthPolicy.ItemEvidence> out = new ArrayList<>();
        for (LadderItemEvidence item : safeList(items)) {
            out.add(item == null ? null : new LadderHealthPolicy.ItemEvidence(
                    item.state,
                    item.rung,
                    item.phase,
                    item.realPassStreak,
                    item.realAgainStreak,
                    item.matureIntervalDays
            ));
        }
        return out;
    }

    private static LadderHealthMetric toAppMetric(LadderHealthPolicy.Metric metric) {
        LadderHealthPolicy.Metric safeMetric = metric == null ? LadderHealthPolicy.Metric.empty() : metric;
        return new LadderHealthMetric(
                safeMetric.rungCounts(),
                safeMetric.totalActiveItems(),
                safeMetric.ladderPromotionIntervalDays(),
                safeMetric.ladderDemotionFailStreak(),
                safeMetric.promotionReadyCount(),
                safeMetric.demotionRiskCount(),
                safeMetric.demotionReadyCount()
        );
    }

    private static KaniOutcomeStats toAppOutcomeStats(KaniOutcomePolicy.OutcomeStats stats) {
        KaniOutcomePolicy.OutcomeStats safeStats = stats == null ? KaniOutcomePolicy.OutcomeStats.empty() : stats;
        return new KaniOutcomeStats(
                toAppWeakMetric(safeStats.weakKanjiImproved()),
                toAppSupportMetric(safeStats.matureSupportGained()),
                toAppMetric(safeStats.ladderHealth())
        );
    }

    private static WeakKanjiImprovedMetric toAppWeakMetric(KaniOutcomePolicy.WeakKanjiImprovedMetric metric) {
        KaniOutcomePolicy.WeakKanjiImprovedMetric safeMetric = metric == null
                ? KaniOutcomePolicy.WeakKanjiImprovedMetric.empty()
                : metric;
        List<KanjiImprovement> examples = new ArrayList<>();
        for (KaniOutcomePolicy.KanjiImprovement example : safeMetric.examples()) {
            examples.add(new KanjiImprovement(example.kanji(), example.beforeWeakness(), example.afterWeakness()));
        }
        return new WeakKanjiImprovedMetric(
                safeMetric.improvedCount(),
                safeMetric.averageBeforeWeakness(),
                safeMetric.averageAfterWeakness(),
                examples
        );
    }

    private static MatureSupportGainedMetric toAppSupportMetric(KaniOutcomePolicy.MatureSupportGainedMetric metric) {
        KaniOutcomePolicy.MatureSupportGainedMetric safeMetric = metric == null
                ? KaniOutcomePolicy.MatureSupportGainedMetric.empty()
                : metric;
        List<KanjiSupportGain> examples = new ArrayList<>();
        for (KaniOutcomePolicy.KanjiSupportGain example : safeMetric.examples()) {
            examples.add(new KanjiSupportGain(example.kanji(), example.beforeMatureSupport(), example.afterMatureSupport()));
        }
        return new MatureSupportGainedMetric(
                safeMetric.gainedSupportCount(),
                safeMetric.matureSupportGained(),
                safeMetric.firstSupportCount(),
                examples
        );
    }

    private static List<KaniOutcomePolicy.OutcomeEvidence> toCoreOutcomeEvidence(List<OutcomeEvidence> evidence) {
        List<KaniOutcomePolicy.OutcomeEvidence> out = new ArrayList<>();
        for (OutcomeEvidence item : safeList(evidence)) {
            out.add(item == null ? null : new KaniOutcomePolicy.OutcomeEvidence(
                    item.kanji,
                    toCoreSnapshot(item.before),
                    toCoreSnapshot(item.after)
            ));
        }
        return out;
    }

    private static KaniOutcomePolicy.OutcomeSnapshot toCoreSnapshot(OutcomeSnapshot snapshot) {
        return snapshot == null ? null : new KaniOutcomePolicy.OutcomeSnapshot(snapshot.weaknessScore, snapshot.matureSupportCount);
    }

    private static LadderHealthPolicy.Metric toCoreMetric(LadderHealthMetric metric) {
        if (metric == null) {
            return LadderHealthPolicy.Metric.empty();
        }
        return LadderHealthPolicy.fromCounts(
                metric.rungCounts,
                metric.totalActiveItems,
                metric.ladderPromotionIntervalDays,
                metric.ladderDemotionFailStreak,
                metric.promotionReadyCount,
                metric.demotionRiskCount,
                metric.demotionReadyCount
        );
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

    private static long localDayStart(long millis) {
        return LocalDayPolicy.localDayStart(millis);
    }

    private static long moveLocalDays(long localDayStart, int days) {
        return LocalDayPolicy.moveLocalDays(localDayStart, days);
    }

    private static String string(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
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
            this(StudyTaskTimingPolicy.summarize(todayMillis, lastSevenDaysMillis, answeredTasks));
        }

        private StudyTaskTimeStats(StudyTaskTimingPolicy.Summary summary) {
            StudyTaskTimingPolicy.Summary safeSummary = summary == null
                    ? StudyTaskTimingPolicy.summarize(0L, 0L, 0)
                    : summary;
            this.todayMillis = safeSummary.todayMillis();
            this.lastSevenDaysMillis = safeSummary.lastSevenDaysMillis();
            this.answeredTasks = safeSummary.answeredTasks();
        }

        private static StudyTaskTimeStats fromCore(StudyTaskTimingPolicy.Summary summary) {
            return new StudyTaskTimeStats(summary);
        }

        public long averageMillisPerTask() {
            return StudyTaskTimingPolicy.summarize(
                    todayMillis,
                    lastSevenDaysMillis,
                    answeredTasks
            ).averageMillisPerTask();
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

    public static final class RecentMistake {
        public final String kanji;
        public final String rating;
        public final long reviewedAtMillis;

        public RecentMistake(String kanji, String rating, long reviewedAtMillis) {
            this(RecentMistakePolicy.mistake(kanji, rating, reviewedAtMillis));
        }

        private RecentMistake(RecentMistakePolicy.RecentMistake mistake) {
            this.kanji = mistake.kanji();
            this.rating = mistake.rating();
            this.reviewedAtMillis = mistake.reviewedAtMillis();
        }

        private static RecentMistake fromCore(RecentMistakePolicy.RecentMistake mistake) {
            return new RecentMistake(mistake);
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

}
