package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.List;

public final class SettingsSummaryTextCopy {
    private static final String SOURCE_ACTIVE = "active";
    private static final String SOURCE_SUSPENDED = "suspended";

    private SettingsSummaryTextCopy() {
    }

    public static String settingsImportSummary(RecordsSyncModels.Settings settings) {
        RecordsSyncModels.Settings safeSettings = java.util.Objects.requireNonNull(settings, "settings");
        List<String> sources = new ArrayList<>();
        if (safeSettings.importActiveCards) {
            sources.add(SOURCE_ACTIVE);
        }
        if (safeSettings.importSuspendedCards) {
            sources.add(SOURCE_SUSPENDED);
        }
        if (safeSettings.importTaggedCardsEnabled()) {
            sources.add("tagged");
        }
        if (safeSettings.importWeakCards) {
            sources.add("weak");
        }
        if (safeSettings.browserQueryImportEnabled()) {
            sources.add("query");
        }
        if (sources.isEmpty()) {
            return "No sources";
        }
        return String.join(" + ", sources) + "; " + matchingCardsSummary(safeSettings);
    }

    public static String matchingCardsSummary(RecordsSyncModels.Settings settings) {
        RecordsSyncModels.Settings safeSettings = java.util.Objects.requireNonNull(settings, "settings");
        int count = safeSettings.importMinMatchingCardsPerKanji;
        return count + (count == 1 ? " matching card per kanji" : " matching cards per kanji");
    }

    public static String syncStatusHeadline(boolean success, String errorMessage, int suspendedCards, int importedKanji) {
        if (!success) {
            return "Sync blocked: " + String.valueOf(errorMessage);
        }
        return String.format(java.util.Locale.ROOT, "%d suspended cards archived, %d rare kanji added; active cards optional", suspendedCards, importedKanji);
    }
}
