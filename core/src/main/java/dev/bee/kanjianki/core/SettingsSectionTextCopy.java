package dev.bee.kanjianki.core;

public final class SettingsSectionTextCopy {
    private SettingsSectionTextCopy() {
    }

    public static String settingsAnkiSourceTitle() {
        return "Anki source";
    }

    public static String settingsAnkiSourceBody() {
        return "What Kani reads from AnkiDroid, and which cards become practice.";
    }

    public static String settingsStudyBehaviorTitle() {
        return "Study behavior";
    }

    public static String settingsStudyBehaviorBody() {
        return "How much appears today, how quickly repeats return, and when cards move rungs.";
    }

    public static String settingsAutomationTitle() {
        return "Automation";
    }

    public static String settingsAutomationBody() {
        return "Background nudges, daily AnkiDroid refreshes, and app update checks.";
    }

    public static String settingsReferenceDataTitle() {
        return "Reference data";
    }

    public static String settingsReferenceDataBody() {
        return "Offline dictionaries, frequency ranks, stroke data, fonts, and attribution.";
    }

    public static String settingsCockpitLabel() {
        return "Settings cockpit";
    }

    public static String settingsHeroBody() {
        return "Grouped by outcome: source data, study behavior, automation, and offline references. Each setting appears once, next to the thing it changes.";
    }

    public static String noteTypeStatusLabel() {
        return "Note type";
    }

    public static String importFiltersStatusLabel() {
        return "Import filters";
    }

    public static String importRanksStatusLabel() {
        return "Import ranks";
    }

    public static String reminderStatusLabel() {
        return "Reminder";
    }

    public static String dailySyncStatusLabel() {
        return "Daily sync";
    }

    public static String updatesStatusLabel() {
        return "Updates";
    }

    public static String matchingCardsStatusLabel() {
        return "Matching cards";
    }

    public static String statusPillDescription(String label, String value) {
        return label + ": " + value;
    }

    public static String categoryToggleDescription(boolean expanded, String title) {
        return (expanded ? "Collapse " : "Expand ") + title;
    }

    public static String settingsCategoryPanelCount(int panels) {
        return panels + (panels == 1 ? " card" : " cards");
    }
}
