package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SettingsTextCopy {
    private static final String SOURCE_ACTIVE = "active";
    private static final String SOURCE_SUSPENDED = "suspended";

    private SettingsTextCopy() {
    }

    public static String settingsImportSummary(RecordsSyncModels.Settings settings) {
        RecordsSyncModels.Settings safeSettings = Objects.requireNonNull(settings, "settings");
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
        RecordsSyncModels.Settings safeSettings = Objects.requireNonNull(settings, "settings");
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

    public static String versionText(String version) {
        if (version == null || version.trim().isEmpty()) {
            return "unknown version";
        }
        return version.replaceFirst("^v", "");
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

    public static String autoSyncDetail(
            boolean configured,
            boolean enabled,
            String lastSuccessText,
            String lastAttemptText,
            String nextRunText
    ) {
        if (!configured) {
            return "Manual sync once, then Kani will keep itself refreshed once per day.";
        }
        List<String> details = new ArrayList<>();
        addDetail(details, "Last auto success ", lastSuccessText);
        addDetail(details, "Last auto attempt ", lastAttemptText);
        if (enabled) {
            addDetail(details, "Next scheduled ", nextRunText);
        }
        if (details.isEmpty()) {
            return enabled
                    ? "Scheduled once per local day. Android may batch the exact time."
                    : "Daily background sync is paused.";
        }
        return String.join(". ", details) + ".";
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

    public static String newCardSortStatusText(String mode) {
        return "Current: " + newCardSortLabel(mode);
    }

    public static String newCardSortLabel(String mode) {
        return switch (RecordsSyncModels.Settings.normalizeNewCardSortMode(mode)) {
            case RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY -> "Anki difficulty";
            case RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK -> "Retrievability risk";
            case RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS -> "Kani weakness";
            default -> "Frequency";
        };
    }

    public static String frequencyRangeStatusText(int minRank, int maxRank) {
        return String.format(Locale.ROOT, "Jiten ranks %d-%d", minRank, maxRank);
    }

    public static String retentionStatusText(int retentionPercent) {
        return "Desired retention: " + retentionPercent + "%";
    }

    public static String ladderRungSubtitle(RecordsBase.StudyLadderSettings ladder, RecordsBase.LadderRung rung) {
        String status = ladder.isEnabled(rung) ? "Enabled" : "Disabled";
        String kind = rung == RecordsBase.LadderRung.SIMILAR_KANJI ? "conditional" : "always available";
        return status + " " + kind + " rung";
    }

    public static String settingsLadderRungLabel(RecordsBase.LadderRung rung) {
        return switch (rung) {
            case WRITE_KANJI -> "Write kanji";
            case SIMILAR_KANJI -> "Similar kanji";
            case TYPE_MEANING -> "Type the meaning";
            case MEANING_KANJI -> "Meaning -> kanji";
            case KANJI_MEANING -> "Kanji -> meaning";
            case FONT_MEANING -> "Font -> meaning";
            case WORD_READING -> "Word -> reading";
        };
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

    private static void addDetail(List<String> details, String prefix, String value) {
        if (value != null && !value.isEmpty()) {
            details.add(prefix + value);
        }
    }
}
