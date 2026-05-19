package dev.bee.kanjianki.core;

import java.util.Locale;

public final class SettingsLadderThresholdTextCopy {
    private SettingsLadderThresholdTextCopy() {
    }

    public static String ladderThresholdsTitle() {
        return "Ladder thresholds";
    }

    public static String ladderThresholdsBody() {
        return "Recognition rungs climb when a real FSRS-due pass schedules the next review beyond the day threshold. Learning-step repeats stay practice-only.";
    }

    public static String fsrsDaysToGoUpLabel() {
        return "FSRS days to go up";
    }

    public static String failsToGoDownLabel() {
        return "Fails to go down";
    }

    public static String useDefaultLadderThresholdsLabel() {
        return String.format(
                Locale.ROOT,
                "Use %d and %d",
                RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
                RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK
        );
    }

    public static String saveLadderThresholdsLabel() {
        return "Save ladder thresholds";
    }

    public static String ladderThresholdsSavedToast() {
        return "Ladder thresholds saved.";
    }
}
