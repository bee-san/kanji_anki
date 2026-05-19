package dev.bee.kanjianki.core;

import java.util.Locale;

public final class SettingsStudyPlanTextCopy {
    private SettingsStudyPlanTextCopy() {
    }

    public static String dailyWorkloadTitle() {
        return "Daily workload";
    }

    public static String automaticWorkloadBody() {
        return "Kani automatically chooses where today's problem-kanji priority curve drops off. This changes how much it admits today, not Anki's schedule.";
    }

    public static String saveMaximumLabel() {
        return "Save maximum";
    }

    public static String manualWorkloadLabel() {
        return "Use manual workload";
    }

    public static String manualWorkloadBody() {
        return "Manual workload overrides the automatic Pareto drop-off. This changes how much Kani admits today, not Anki's schedule.";
    }

    public static String[] workloadScaleLabels() {
        return new String[]{"Very little", "Pareto", "Balanced", "More", "All kanji"};
    }

    public static String saveWorkloadLabel() {
        return "Save workload";
    }

    public static String automaticParetoLabel() {
        return "Use automatic Pareto";
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

    public static String studyLadderTitle() {
        return "Study ladder";
    }

    public static String studyLadderBody() {
        return "Turn rungs off or move them up and down. At least one always-available rung stays on.";
    }

    public static String ladderToggleLabel(boolean enabled) {
        return enabled ? "On" : "Off";
    }

    public static String moveUpLabel() {
        return "Up";
    }

    public static String moveDownLabel() {
        return "Down";
    }

    public static String restoreDefaultLadderLabel() {
        return "Restore default ladder";
    }

    public static String studyLadderRestoredToast() {
        return "Study ladder restored.";
    }

    public static String keepAlwaysAvailableRungToast() {
        return "Keep at least one always-available rung on.";
    }

    public static String ladderRungToggleToast(RecordsBase.LadderRung rung, boolean wasEnabled) {
        return settingsLadderRungLabel(rung) + (wasEnabled ? " off." : " on.");
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
}
