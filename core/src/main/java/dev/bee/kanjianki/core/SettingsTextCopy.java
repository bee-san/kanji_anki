package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SettingsTextCopy {
    private static final String SOURCE_ACTIVE = "active";
    private static final String SOURCE_SUSPENDED = "suspended";

    private SettingsTextCopy() {
    }

    public static String settingsImportSummary(RecordsSyncModels.Settings settings) {
        RecordsSyncModels.Settings safeSettings = settings == null ? RecordsSyncModels.Settings.kikuDefaults() : settings;
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
        RecordsSyncModels.Settings safeSettings = settings == null ? RecordsSyncModels.Settings.kikuDefaults() : settings;
        int count = safeSettings.importMinMatchingCardsPerKanji;
        return count + (count == 1 ? " matching card per kanji" : " matching cards per kanji");
    }

    public static String settingsReminderSummary(boolean enabled, boolean blocked, String displayTime) {
        if (blocked) {
            return "Blocked";
        }
        return enabled ? displayTime : "Off";
    }

    public static String settingsAutoSyncSummary(boolean configured, boolean enabled, String displayTime) {
        if (!configured) {
            return "After first sync";
        }
        return enabled ? displayTime : "Off";
    }

    public static String settingsUpdateSummary(boolean hasPendingUpdate, boolean enabled) {
        if (hasPendingUpdate) {
            return "Verified APK ready";
        }
        return enabled ? "Automatic checks on" : "Manual checks";
    }

    public static String autoSyncStatus(boolean configured, boolean enabled, String displayTime) {
        if (!configured) {
            return "Starts after first successful sync";
        }
        if (enabled) {
            return "On around " + displayTime;
        }
        return "Off";
    }

    public static String workloadStatusText(int percent, int maxItems) {
        int snapped = AdaptiveLoadPlanner.snapWorkloadPercent(percent);
        int normalizedMax = AdaptiveLoadPlanner.normalizeMaxItems(maxItems);
        String label = AdaptiveLoadPlanner.workloadLabel(snapped);
        if (snapped >= 100) {
            return label + ": up to " + normalizedMax + " items";
        }
        return label + ": up to " + Math.min(AdaptiveLoadPlanner.targetCeiling(snapped), normalizedMax) + " items";
    }

    public static String maxItemsStatusText(int maxItems) {
        return "Maximum: " + StudyTextCopy.countText(AdaptiveLoadPlanner.normalizeMaxItems(maxItems), "item", "items");
    }

    public static String autoWorkloadStatusText(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        if (plan == null || plan.target <= 0) {
            return "Auto Pareto: waiting for problem kanji";
        }
        return "Auto Pareto: " + StudyTextCopy.countText(plan.target, "item", "items") + " today";
    }

    public static String reminderStatus(boolean enabled, boolean blocked, String displayTime) {
        if (blocked) {
            return "Blocked: notifications off";
        }
        if (enabled) {
            return "Daily around " + displayTime;
        }
        return "Off";
    }

    public static String reminderTime(int hour, int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    public static String reminderTimeButtonLabel(int hour, int minute) {
        return String.format(Locale.ROOT, "Reminder time: %02d:%02d", hour, minute);
    }
}
