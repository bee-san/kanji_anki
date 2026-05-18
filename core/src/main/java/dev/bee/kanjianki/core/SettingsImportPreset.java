package dev.bee.kanjianki.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public record SettingsImportPreset(
        String label,
        boolean activeCards,
        boolean suspendedCards,
        boolean taggedCards,
        String tags,
        boolean weakCards,
        double weakDifficulty,
        int weakLapses,
        int minMatchingCards,
        boolean browserQueryCards,
        String browserQuery
) {
    private static final List<SettingsImportPreset> DEFAULTS = Collections.unmodifiableList(Arrays.asList(
            new SettingsImportPreset("Suspended only", false, true, false, "", false, 7.0, 2, 1, false, ""),
            new SettingsImportPreset("Kani tag", false, false, true, "kani", false, 7.0, 2, 1, false, ""),
            new SettingsImportPreset("Leech tag", false, false, true, "leech", false, 7.0, 2, 1, false, ""),
            new SettingsImportPreset("Mining deck", false, false, false, "", false, 7.0, 2, 1, true, "deck:Mining"),
            new SettingsImportPreset("Recent fails", false, false, false, "", false, 7.0, 2, 1, true, "rated:30:1")
    ));

    public static List<SettingsImportPreset> defaults() {
        return DEFAULTS;
    }

    public static int boolFlag(boolean value) {
        return value ? 1 : 0;
    }
}
