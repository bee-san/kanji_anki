package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SettingsAutomationTextCopy {
    private SettingsAutomationTextCopy() {
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

    public static String updatePageTitle() {
        return "GitHub updater";
    }

    public static String updatePageBody(String versionName) {
        return "Current version " + String.valueOf(versionName)
                + ". Checks GitHub Releases, verifies the APK, and asks Android to install it.";
    }

    public static String automaticUpdatesTitle() {
        return "Automatic updates";
    }

    public static String checkForUpdateLabel() {
        return "Check for update";
    }

    public static String autoUpdatePanelStatus(boolean enabled) {
        return enabled ? "On: checks about once a day" : "Off";
    }

    public static String autoUpdateLastCheckLine(String lastCheckText) {
        return "Last check: " + String.valueOf(lastCheckText);
    }

    public static String autoUpdateLastResultLine(String lastResult) {
        return "Last result: " + String.valueOf(lastResult);
    }

    public static String installPermissionLine(boolean canInstall) {
        return "Install permission: " + (canInstall ? "Ready" : "Missing");
    }

    public static String verifiedApkReadyLine(String version) {
        return "Verified APK ready: " + versionText(version);
    }

    public static String pendingUpdateFallback() {
        return "Android needs confirmation before Kani can replace itself.";
    }

    public static String installVerifiedUpdateLabel() {
        return "Install verified update";
    }

    public static String setupAppInstallsLabel() {
        return "Set up app installs";
    }

    public static String automaticUpdatesToggleLabel(boolean enabled) {
        return enabled ? "Turn off automatic updates" : "Turn on automatic updates";
    }

    public static String backToSettingsLabel() {
        return "Back to settings";
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

    public static String dailyAnkiSyncTitle() {
        return "Daily Anki sync";
    }

    public static String turnOffDailySyncLabel() {
        return "Turn off daily sync";
    }

    public static String turnOnDailySyncLabel() {
        return "Turn on daily sync";
    }

    public static String appUpdatesTitle() {
        return "App updates";
    }

    public static String openUpdaterLabel() {
        return "Open updater";
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

    public static String dailyReminderTitle() {
        return "Daily reminder";
    }

    public static String dailyReminderBody() {
        return "Kani can nudge you once a day to study active problem kanji. Reminder timing is approximate because Android may batch background work.";
    }

    public static String morningReminderPresetLabel() {
        return "Morning";
    }

    public static String lunchReminderPresetLabel() {
        return "Lunch";
    }

    public static String eveningReminderPresetLabel() {
        return "Evening";
    }

    public static String nightReminderPresetLabel() {
        return "Night";
    }

    public static String saveReminderLabel() {
        return "Save reminder";
    }

    public static String enableReminderLabel() {
        return "Enable reminder";
    }

    public static String turnOffReminderLabel() {
        return "Turn off reminder";
    }

    public static String notificationsBlockedBody() {
        return "Android notifications are off for Kani, so this reminder cannot appear yet.";
    }

    public static String openNotificationSettingsLabel() {
        return "Open notification settings";
    }

    public static String notificationPermissionBody() {
        return "Android will ask for notification permission before turning this on.";
    }

    public static String reminderTime(int hour, int minute) {
        return TimeOfDaySettingsPolicy.displayTime(hour, minute);
    }

    public static String reminderTimeButtonLabel(int hour, int minute) {
        return "Reminder time: " + TimeOfDaySettingsPolicy.displayTime(hour, minute);
    }

    public static String reminderPresetButtonLabel(String label, int hour, int minute) {
        return label + " " + reminderTime(hour, minute);
    }

    public static String studyAheadMinutesLabel() {
        return String.format(Locale.ROOT, "Minutes (%s)", studyAheadMinutesRange());
    }

    public static String studyAheadMinutesRange() {
        return String.format(Locale.ROOT, "%d-%d", SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES, SettingsInputRules.MAX_STUDY_AHEAD_MINUTES);
    }

    public static String studyAheadWholeNumberErrorText() {
        return String.format(Locale.ROOT, "Use a whole number of minutes (%s).", studyAheadMinutesRange());
    }

    public static String studyAheadOutOfRangeErrorText() {
        return String.format(Locale.ROOT, "Use %d to disable, or up to %s.", SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES, studyAheadMaxDescription());
    }

    public static String studyAheadMaxDescription() {
        int maxMinutes = SettingsInputRules.MAX_STUDY_AHEAD_MINUTES;
        if (maxMinutes % 60 == 0) {
            return String.format(Locale.ROOT, "%d minutes (%dh)", maxMinutes, maxMinutes / 60);
        }
        return String.format(Locale.ROOT, "%d minutes", maxMinutes);
    }

    private static void addDetail(List<String> details, String prefix, String value) {
        if (value != null && !value.isEmpty()) {
            details.add(prefix + value);
        }
    }
}
