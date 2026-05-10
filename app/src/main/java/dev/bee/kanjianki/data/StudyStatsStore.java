package dev.bee.kanjianki.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StudyStatsStore {
    private static final String TABLE_REVIEW_LOG = "review_log";
    private static final String TABLE_SYNC_KANJI_SNAPSHOTS = "sync_kanji_snapshots";
    private static final String COLUMN_KANJI = "kanji";
    private static final String COLUMN_RATING = "rating";
    private static final String COLUMN_REVIEWED_AT = "reviewed_at";
    private static final String COLUMN_WRITING_REQUIRED = "writing_required";
    private static final String COLUMN_WRITING_PASSED = "writing_passed";
    private static final String COLUMN_MANUAL_OVERRIDE = "manual_override";
    private static final String COLUMN_LAST_REVIEWED_AT = "last_reviewed_at";
    private static final String COLUMN_WEAKNESS_SCORE = "weakness_score";
    private static final String COLUMN_MATURE_SUPPORT_COUNT = "mature_support_count";
    private static final String RATING_AGAIN = "again";
    private final LocalStore store;

    public StudyStatsStore(LocalStore store) {
        this.store = store;
    }

    public StudyTaskTimeStats studyTaskTimeStats(long nowMillis) {
        long today = localDayStart(nowMillis);
        long tomorrow = moveLocalDays(today, 1);
        long sevenDayStart = moveLocalDays(today, -6);
        long todayMillis = sumStudyTaskElapsed(today, tomorrow);
        StudyTaskAggregate week = studyTaskAggregate(sevenDayStart, tomorrow);
        return new StudyTaskTimeStats(todayMillis, week.elapsedMillis, week.taskCount);
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
        Cursor cursor = db().query(
                TABLE_REVIEW_LOG,
                new String[]{COLUMN_KANJI, COLUMN_WRITING_REQUIRED, COLUMN_WRITING_PASSED, COLUMN_MANUAL_OVERRIDE},
                null,
                null,
                null,
                null,
                null
        );
        Set<String> reviewedKanji = new HashSet<>();
        int total = 0;
        int writingRequired = 0;
        int writingPassed = 0;
        int writingFailed = 0;
        int manualOverrides = 0;
        try {
            while (cursor.moveToNext()) {
                total++;
                reviewedKanji.add(string(cursor, COLUMN_KANJI));
                boolean required = integer(cursor, COLUMN_WRITING_REQUIRED) == 1;
                boolean passed = integer(cursor, COLUMN_WRITING_PASSED) == 1;
                boolean override = integer(cursor, COLUMN_MANUAL_OVERRIDE) == 1;
                if (required) {
                    writingRequired++;
                    if (passed) {
                        writingPassed++;
                    } else if (!override) {
                        writingFailed++;
                    }
                }
                if (override) {
                    manualOverrides++;
                }
            }
        } finally {
            cursor.close();
        }
        return new StudyImpactStats(total, reviewedKanji.size(), writingRequired, writingPassed, writingFailed, manualOverrides);
    }

    public KaniOutcomeStats kaniOutcomeStats() {
        SQLiteDatabase db = db();
        Map<String, ReviewWindow> reviewWindows = reviewWindowsByKanji(db);
        if (reviewWindows.isEmpty()) {
            return KaniOutcomeStats.empty();
        }

        OutcomeAccumulator accumulator = new OutcomeAccumulator();
        for (ReviewWindow window : reviewWindows.values()) {
            OutcomeSnapshot before = latestOutcomeSnapshotBefore(db, window.kanji, window.firstReviewedAtMillis);
            OutcomeSnapshot after = latestOutcomeSnapshotAfter(db, window.kanji, window.lastReviewedAtMillis);
            accumulator.add(window.kanji, before, after);
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
                accumulator.firstSupportCount,
                topThreeSupportGains(accumulator.supportGains)
        );
        return new KaniOutcomeStats(weakMetric, supportMetric);
    }

    private SQLiteDatabase db() {
        return store.getReadableDatabase();
    }

    private long sumStudyTaskElapsed(long startMillis, long endMillis) {
        Cursor cursor = db().rawQuery(
                "SELECT COALESCE(SUM(active_elapsed_ms), 0) FROM study_task_log WHERE answered_at>=? AND answered_at<?",
                new String[]{Long.toString(startMillis), Long.toString(endMillis)}
        );
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        } finally {
            cursor.close();
        }
    }

    private StudyTaskAggregate studyTaskAggregate(long startMillis, long endMillis) {
        Cursor cursor = db().rawQuery(
                "SELECT COALESCE(SUM(active_elapsed_ms), 0), COUNT(*) FROM study_task_log WHERE answered_at>=? AND answered_at<?",
                new String[]{Long.toString(startMillis), Long.toString(endMillis)}
        );
        try {
            if (!cursor.moveToFirst()) {
                return new StudyTaskAggregate(0L, 0);
            }
            return new StudyTaskAggregate(cursor.getLong(0), cursor.getInt(1));
        } finally {
            cursor.close();
        }
    }

    private StudyDays studyDays(long today) {
        Cursor cursor = db().query(
                TABLE_REVIEW_LOG,
                new String[]{COLUMN_REVIEWED_AT},
                null,
                null,
                null,
                null,
                "reviewed_at DESC"
        );
        List<Long> days = new ArrayList<>();
        int reviewsToday = 0;
        long tomorrow = moveLocalDays(today, 1);
        long lastStudyAt = 0L;
        try {
            long lastAddedDay = Long.MIN_VALUE;
            while (cursor.moveToNext()) {
                long reviewedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_REVIEWED_AT));
                if (lastStudyAt == 0L) {
                    lastStudyAt = reviewedAt;
                }
                if (reviewedAt >= today && reviewedAt < tomorrow) {
                    reviewsToday++;
                }
                long day = localDayStart(reviewedAt);
                if (day != lastAddedDay) {
                    days.add(day);
                    lastAddedDay = day;
                }
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

    private Map<String, ReviewWindow> reviewWindowsByKanji(SQLiteDatabase db) {
        Map<String, ReviewWindow> out = new LinkedHashMap<>();
        Cursor cursor = db.rawQuery(
                "SELECT kanji, MIN(reviewed_at) AS first_reviewed_at, MAX(reviewed_at) AS last_reviewed_at FROM review_log GROUP BY kanji",
                null
        );
        try {
            while (cursor.moveToNext()) {
                String kanji = string(cursor, COLUMN_KANJI);
                if (!kanji.isEmpty()) {
                    out.put(kanji, new ReviewWindow(
                            kanji,
                            longValue(cursor, "first_reviewed_at"),
                            longValue(cursor, COLUMN_LAST_REVIEWED_AT)
                    ));
                }
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    private OutcomeSnapshot latestOutcomeSnapshotBefore(SQLiteDatabase db, String kanji, long reviewedAtMillis) {
        return outcomeSnapshot(db, kanji, "kanji=? AND finished_at<?", reviewedAtMillis);
    }

    private OutcomeSnapshot latestOutcomeSnapshotAfter(SQLiteDatabase db, String kanji, long reviewedAtMillis) {
        return outcomeSnapshot(db, kanji, "kanji=? AND finished_at>?", reviewedAtMillis);
    }

    private OutcomeSnapshot outcomeSnapshot(SQLiteDatabase db, String kanji, String selection, long reviewedAtMillis) {
        Cursor cursor = db.query(
                TABLE_SYNC_KANJI_SNAPSHOTS,
                new String[]{COLUMN_WEAKNESS_SCORE, COLUMN_MATURE_SUPPORT_COUNT},
                selection,
                new String[]{kanji, Long.toString(reviewedAtMillis)},
                null,
                null,
                "finished_at DESC, sync_id DESC",
                "1"
        );
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new OutcomeSnapshot(integer(cursor, COLUMN_WEAKNESS_SCORE), integer(cursor, COLUMN_MATURE_SUPPORT_COUNT));
        } finally {
            cursor.close();
        }
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

        public KaniOutcomeStats(WeakKanjiImprovedMetric weakKanjiImproved, MatureSupportGainedMetric matureSupportGained) {
            this.weakKanjiImproved = weakKanjiImproved == null ? WeakKanjiImprovedMetric.empty() : weakKanjiImproved;
            this.matureSupportGained = matureSupportGained == null ? MatureSupportGainedMetric.empty() : matureSupportGained;
        }

        public static KaniOutcomeStats empty() {
            return new KaniOutcomeStats(WeakKanjiImprovedMetric.empty(), MatureSupportGainedMetric.empty());
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
        public final int firstSupportCount;
        public final List<KanjiSupportGain> examples;

        public MatureSupportGainedMetric(int gainedSupportCount, int firstSupportCount, List<KanjiSupportGain> examples) {
            this.gainedSupportCount = Math.max(0, gainedSupportCount);
            this.firstSupportCount = Math.max(0, firstSupportCount);
            this.examples = Collections.unmodifiableList(new ArrayList<>(examples == null ? Collections.emptyList() : examples));
        }

        public static MatureSupportGainedMetric empty() {
            return new MatureSupportGainedMetric(0, 0, Collections.emptyList());
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

    private record StudyTaskAggregate(long elapsedMillis, int taskCount) {
    }

    private record ReviewWindow(String kanji, long firstReviewedAtMillis, long lastReviewedAtMillis) {
        private ReviewWindow {
            kanji = kanji == null ? "" : kanji;
            firstReviewedAtMillis = Math.max(0L, firstReviewedAtMillis);
            lastReviewedAtMillis = Math.max(0L, lastReviewedAtMillis);
        }
    }

    private record OutcomeSnapshot(int weaknessScore, int matureSupportCount) {
        private OutcomeSnapshot {
            weaknessScore = Math.max(0, weaknessScore);
            matureSupportCount = Math.max(0, matureSupportCount);
        }
    }

    private static final class OutcomeAccumulator {
        private final List<KanjiImprovement> improvements = new ArrayList<>();
        private final List<KanjiSupportGain> supportGains = new ArrayList<>();
        private double beforeWeaknessSum;
        private double afterWeaknessSum;
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
            if (after.matureSupportCount <= before.matureSupportCount) {
                return;
            }
            supportGains.add(new KanjiSupportGain(kanji, before.matureSupportCount, after.matureSupportCount));
            if (before.matureSupportCount == 0) {
                firstSupportCount++;
            }
        }
    }
}
