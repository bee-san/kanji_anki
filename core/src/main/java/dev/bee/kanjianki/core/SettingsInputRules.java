package dev.bee.kanjianki.core;

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

    public static int rankSliderProgress(int rank) {
        return Math.max(0, Math.min(MAX_RANK_SLIDER_PROGRESS, rank - 1));
    }

    public static int rankFromSliderProgress(int progress) {
        return Math.max(FrequencyRetentionRanges.MIN_RANK, Math.min(FrequencyRetentionRanges.MAX_RANK, progress + 1));
    }
}
