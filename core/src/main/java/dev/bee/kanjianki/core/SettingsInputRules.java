package dev.bee.kanjianki.core;

import java.util.List;

public final class SettingsInputRules {
    public static final int DEFAULT_STUDY_AHEAD_MINUTES = 0;
    public static final int MAX_STUDY_AHEAD_MINUTES = 1440;
    private static final int MAX_RANK_SLIDER_PROGRESS = FrequencyRetentionRanges.MAX_RANK - 1;

    private SettingsInputRules() {
    }

    public static boolean validImportThresholds(double difficulty, int lapseThreshold, int minCards) {
        boolean difficultyValid = difficulty >= 1.0 && difficulty <= 10.0;
        boolean lapsesValid = lapseThreshold >= 1 && lapseThreshold <= 100;
        boolean minCardsValid = minCards >= 1 && minCards <= 1000;
        return difficultyValid && lapsesValid && minCardsValid;
    }

    public static boolean hasSelectedImportSource(
            boolean activeCards,
            boolean suspendedCards,
            boolean taggedCards,
            boolean weakCards,
            boolean browserQueryCards,
            List<String> parsedTags,
            String queryText
    ) {
        if (activeCards || suspendedCards || weakCards) {
            return true;
        }
        if (taggedCards && !parsedTags.isEmpty()) {
            return true;
        }
        return browserQueryCards && !queryText.isEmpty();
    }

    public static int rankSliderProgress(int rank) {
        return Math.max(0, Math.min(MAX_RANK_SLIDER_PROGRESS, rank - 1));
    }

    public static int rankFromSliderProgress(int progress) {
        return Math.max(FrequencyRetentionRanges.MIN_RANK, Math.min(FrequencyRetentionRanges.MAX_RANK, progress + 1));
    }

    public static boolean validRank(int rank) {
        return rank >= FrequencyRetentionRanges.MIN_RANK && rank <= FrequencyRetentionRanges.MAX_RANK;
    }

    public static RankRange normalizedRankRange(int minRank, int maxRank) {
        int normalizedMin = clampRank(minRank);
        int normalizedMax = clampRank(maxRank);
        if (normalizedMin > normalizedMax) {
            int swap = normalizedMin;
            normalizedMin = normalizedMax;
            normalizedMax = swap;
        }
        return new RankRange(normalizedMin, normalizedMax);
    }

    public static int retentionPercent(double retention) {
        return Math.max(80, Math.min(97, (int) Math.round(retention * 100.0)));
    }

    public static int normalizeStudyAheadMinutes(int minutes) {
        if (minutes <= 0) {
            return 0;
        }
        return Math.min(minutes, MAX_STUDY_AHEAD_MINUTES);
    }

    private static int clampRank(int rank) {
        return Math.max(FrequencyRetentionRanges.MIN_RANK, Math.min(FrequencyRetentionRanges.MAX_RANK, rank));
    }

    public record RankRange(int minRank, int maxRank) {
    }
}
