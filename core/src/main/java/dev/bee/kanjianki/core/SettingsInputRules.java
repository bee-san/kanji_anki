package dev.bee.kanjianki.core;

import java.util.List;

public final class SettingsInputRules {
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

    public static int retentionPercent(double retention) {
        return Math.max(80, Math.min(97, (int) Math.round(retention * 100.0)));
    }
}
