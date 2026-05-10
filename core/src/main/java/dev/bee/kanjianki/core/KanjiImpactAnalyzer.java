package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class KanjiImpactAnalyzer {
    public static final String BUCKET_HELPED = "helped";
    public static final String BUCKET_NOT_HELPING = "not_helping_yet";
    public static final String BUCKET_NEEDS_MORE_CARDS = "needs_more_cards";

    private static final double RETENTION_HELP_THRESHOLD = 0.08;
    private static final double DIFFICULTY_HELP_THRESHOLD = -0.30;

    public Report analyze(List<KanjiHistory> histories) {
        if (histories == null || histories.isEmpty()) {
            return new Report(0, 0, 0, Collections.emptyList());
        }
        List<Row> rows = new ArrayList<>();
        int helped = 0;
        int notHelping = 0;
        int needsMoreCards = 0;
        for (KanjiHistory history : histories) {
            if (history == null || history.kanji.isEmpty()) {
                continue;
            }
            Row row = rowFor(history);
            rows.add(row);
            if (BUCKET_HELPED.equals(row.bucket)) {
                helped++;
            } else if (BUCKET_NEEDS_MORE_CARDS.equals(row.bucket)) {
                needsMoreCards++;
            } else {
                notHelping++;
            }
        }
        rows.sort(Comparator
                .comparingInt((Row row) -> bucketRank(row.bucket))
                .thenComparing((Row row) -> -Math.abs(row.retentionDelta))
                .thenComparing(row -> row.kanji));
        return new Report(helped, notHelping, needsMoreCards, rows);
    }

    private Row rowFor(KanjiHistory history) {
        MetricSnapshot baseline = firstNonNull(history.sameCardBaseline, history.baseline);
        MetricSnapshot current = firstNonNull(history.sameCardCurrent, history.current);
        String bucket = bucketFor(history, baseline, current);
        double baselineDifficulty = baseline == null ? 0.0 : baseline.difficultyScore();
        double currentDifficulty = current == null ? 0.0 : current.difficultyScore();
        double baselineRetention = baseline == null ? 0.0 : baseline.retentionScore();
        double currentRetention = current == null ? 0.0 : current.retentionScore();
        int baselineMature = history.baseline == null ? 0 : history.baseline.matureCards;
        int currentMature = history.current == null ? 0 : history.current.matureCards;
        return new Row(
                history.kanji,
                bucket,
                new RowMetrics(
                        baselineDifficulty,
                        currentDifficulty,
                        baselineRetention,
                        currentRetention,
                        baselineMature,
                        currentMature,
                        Math.max(0, history.commonCards),
                        Math.max(0, history.newCards),
                        history.current == null ? 0 : history.current.totalCards(),
                        history.reviewCount
                ),
                adviceFor(bucket)
        );
    }

    private String bucketFor(KanjiHistory history, MetricSnapshot baseline, MetricSnapshot current) {
        if (history.current == null || history.current.totalCards() < 2 || history.commonCards <= 0 || baseline == null || current == null) {
            return BUCKET_NEEDS_MORE_CARDS;
        }
        double retentionDelta = current.retentionScore() - baseline.retentionScore();
        double difficultyDelta = current.difficultyScore() - baseline.difficultyScore();
        int sameMatureDelta = current.matureCards - baseline.matureCards;
        boolean helped = retentionDelta >= RETENTION_HELP_THRESHOLD
                || difficultyDelta <= DIFFICULTY_HELP_THRESHOLD
                || sameMatureDelta > 0;
        return helped ? BUCKET_HELPED : BUCKET_NOT_HELPING;
    }

    private static MetricSnapshot firstNonNull(MetricSnapshot first, MetricSnapshot second) {
        return first == null ? second : first;
    }

    private static int bucketRank(String bucket) {
        if (BUCKET_HELPED.equals(bucket)) {
            return 0;
        }
        if (BUCKET_NOT_HELPING.equals(bucket)) {
            return 1;
        }
        return 2;
    }

    private static String adviceFor(String bucket) {
        if (BUCKET_HELPED.equals(bucket)) {
            return "Kani appears to be helping this kanji.";
        }
        if (BUCKET_NOT_HELPING.equals(bucket)) {
            return "Kani is not moving the needle yet.";
        }
        return "Immerse and mine more flashcards for this kanji before judging Kani.";
    }

    public static final class Report {
        public final int helpedCount;
        public final int notHelpingCount;
        public final int needsMoreCardsCount;
        public final List<Row> rows;

        public Report(int helpedCount, int notHelpingCount, int needsMoreCardsCount, List<Row> rows) {
            this.helpedCount = Math.max(0, helpedCount);
            this.notHelpingCount = Math.max(0, notHelpingCount);
            this.needsMoreCardsCount = Math.max(0, needsMoreCardsCount);
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows == null ? Collections.emptyList() : rows));
        }

        public boolean empty() {
            return helpedCount == 0 && notHelpingCount == 0 && needsMoreCardsCount == 0;
        }
    }

    public static final class Row {
        public final String kanji;
        public final String bucket;
        public final double baselineDifficulty;
        public final double currentDifficulty;
        public final double baselineRetention;
        public final double currentRetention;
        public final double difficultyDelta;
        public final double retentionDelta;
        public final int baselineMatureCards;
        public final int currentMatureCards;
        public final int sameCardCount;
        public final int newCardCount;
        public final int currentCardCount;
        public final int reviewCount;
        public final String advice;

        private Row(
                String kanji,
                String bucket,
                RowMetrics metrics,
                String advice
        ) {
            this.kanji = kanji == null ? "" : kanji;
            this.bucket = bucket == null ? BUCKET_NEEDS_MORE_CARDS : bucket;
            this.baselineDifficulty = metrics.baselineDifficulty;
            this.currentDifficulty = metrics.currentDifficulty;
            this.baselineRetention = clamp(metrics.baselineRetention, 0.0, 1.0);
            this.currentRetention = clamp(metrics.currentRetention, 0.0, 1.0);
            this.difficultyDelta = metrics.currentDifficulty - metrics.baselineDifficulty;
            this.retentionDelta = metrics.currentRetention - metrics.baselineRetention;
            this.baselineMatureCards = Math.max(0, metrics.baselineMatureCards);
            this.currentMatureCards = Math.max(0, metrics.currentMatureCards);
            this.sameCardCount = Math.max(0, metrics.sameCardCount);
            this.newCardCount = Math.max(0, metrics.newCardCount);
            this.currentCardCount = Math.max(0, metrics.currentCardCount);
            this.reviewCount = Math.max(0, metrics.reviewCount);
            this.advice = advice == null ? "" : advice;
        }

        public String summary() {
            return String.format(
                    Locale.ROOT,
                    "%s: difficulty %.1f -> %.1f, retention %d%% -> %d%%, mature cards %d -> %d",
                    kanji,
                    baselineDifficulty,
                    currentDifficulty,
                    Math.round(baselineRetention * 100.0),
                    Math.round(currentRetention * 100.0),
                    baselineMatureCards,
                    currentMatureCards
            );
        }
    }

    public static final class KanjiHistory {
        public final String kanji;
        public final MetricSnapshot baseline;
        public final MetricSnapshot current;
        public final MetricSnapshot sameCardBaseline;
        public final MetricSnapshot sameCardCurrent;
        public final int commonCards;
        public final int newCards;
        public final int reviewCount;

        public KanjiHistory(
                String kanji,
                MetricSnapshot baseline,
                MetricSnapshot current,
                MetricSnapshot sameCardBaseline,
                MetricSnapshot sameCardCurrent,
                int... counts
        ) {
            this.kanji = kanji == null ? "" : kanji;
            this.baseline = baseline;
            this.current = current;
            this.sameCardBaseline = sameCardBaseline;
            this.sameCardCurrent = sameCardCurrent;
            this.commonCards = Math.max(0, countAt(counts, 0));
            this.newCards = Math.max(0, countAt(counts, 1));
            this.reviewCount = Math.max(0, countAt(counts, 2));
        }

        private static int countAt(int[] counts, int index) {
            return counts == null || counts.length <= index ? 0 : counts[index];
        }
    }

    public static final class MetricSnapshot {
        public final int activeCards;
        public final int suspendedCards;
        public final int matureCards;
        public final double averageIntervalDays;
        public final int reps;
        public final int lapses;
        public final Double fsrsStability;
        public final Double fsrsDifficulty;
        public final Double fsrsRetrievability;

        public MetricSnapshot(
                int activeCards,
                int suspendedCards,
                int matureCards,
                double averageIntervalDays,
                int reps,
                int lapses,
                Double... fsrsValues
        ) {
            this.activeCards = Math.max(0, activeCards);
            this.suspendedCards = Math.max(0, suspendedCards);
            this.matureCards = Math.max(0, matureCards);
            this.averageIntervalDays = Math.max(0.0, averageIntervalDays);
            this.reps = Math.max(0, reps);
            this.lapses = Math.max(0, lapses);
            this.fsrsStability = fsrsAt(fsrsValues, 0);
            this.fsrsDifficulty = fsrsAt(fsrsValues, 1);
            this.fsrsRetrievability = fsrsAt(fsrsValues, 2);
        }

        public int totalCards() {
            return activeCards + suspendedCards;
        }

        public double retentionScore() {
            if (fsrsRetrievability != null) {
                return normalizeRetention(fsrsRetrievability);
            }
            if (reps > 0) {
                return clamp((reps - Math.min(reps, lapses)) / (double) reps, 0.0, 1.0);
            }
            if (matureCards > 0) {
                return 0.88;
            }
            return 0.50;
        }

        public double difficultyScore() {
            if (fsrsDifficulty != null) {
                return clamp(fsrsDifficulty, 1.0, 10.0);
            }
            double lapseRate = reps == 0 ? 0.0 : lapses / (double) reps;
            double matureRatio = totalCards() == 0 ? 0.0 : matureCards / (double) totalCards();
            return clamp(5.0 + lapseRate * 5.0 - matureRatio * 1.5, 1.0, 10.0);
        }

        private static double normalizeRetention(double value) {
            double normalized = value > 1.0 ? value / 100.0 : value;
            return clamp(normalized, 0.0, 1.0);
        }

        private static Double fsrsAt(Double[] values, int index) {
            return values == null || values.length <= index ? null : values[index];
        }
    }

    private static final class RowMetrics {
        private final double baselineDifficulty;
        private final double currentDifficulty;
        private final double baselineRetention;
        private final double currentRetention;
        private final int baselineMatureCards;
        private final int currentMatureCards;
        private final int sameCardCount;
        private final int newCardCount;
        private final int currentCardCount;
        private final int reviewCount;

        private RowMetrics(
                double baselineDifficulty,
                double currentDifficulty,
                double baselineRetention,
                double currentRetention,
                int... counts
        ) {
            this.baselineDifficulty = baselineDifficulty;
            this.currentDifficulty = currentDifficulty;
            this.baselineRetention = baselineRetention;
            this.currentRetention = currentRetention;
            this.baselineMatureCards = countAt(counts, 0);
            this.currentMatureCards = countAt(counts, 1);
            this.sameCardCount = countAt(counts, 2);
            this.newCardCount = countAt(counts, 3);
            this.currentCardCount = countAt(counts, 4);
            this.reviewCount = countAt(counts, 5);
        }

        private static int countAt(int[] counts, int index) {
            return counts == null || counts.length <= index ? 0 : counts[index];
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
