package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsSyncModels;

final class MainActivitySettingsScreen {
    private final MainActivitySettings activity;

    MainActivitySettingsScreen(MainActivitySettings activity) {
        this.activity = activity;
    }

    void renderSettings(boolean preserveScroll) {
        int scrollY = preserveScroll && activity.contentScroll != null ? activity.contentScroll.getScrollY() : 0;
        activity.base(activity.NAV_SETTINGS_ROUTE);
        RecordsSyncModels.Settings current = activity.settings();
        activity.content.addView(activity.fullWidthHomeButton());
        activity.content.addView(activity.settingsHero(current, activity.store.reminderSettings(), activity.store.autoSyncSettings(), activity.store.autoUpdateStatus()));
        activity.addSpace(10);

        activity.content.addView(activity.settingsCategory(
                dev.bee.kanjianki.core.SettingsTextCopy.settingsAnkiSourceTitle(),
                dev.bee.kanjianki.core.SettingsTextCopy.settingsAnkiSourceBody(),
                R.drawable.ic_book_24,
                activity.settingsAnkiExpanded,
                () -> {
                    activity.settingsAnkiExpanded = !activity.settingsAnkiExpanded;
                    activity.renderSettings(true);
                },
                activity.noteTypeSettingsPanel(current),
                activity.importFilterSettingsPanel(current),
                activity.frequencyRangeSettingsPanel(current)
        ));
        activity.content.addView(activity.settingsCategory(
                dev.bee.kanjianki.core.SettingsTextCopy.settingsStudyBehaviorTitle(),
                dev.bee.kanjianki.core.SettingsTextCopy.settingsStudyBehaviorBody(),
                R.drawable.ic_study_24,
                activity.settingsStudyExpanded,
                () -> {
                    activity.settingsStudyExpanded = !activity.settingsStudyExpanded;
                    activity.renderSettings(true);
                },
                activity.newCardSortSettingsPanel(current),
                activity.workloadSettingsPanel(),
                activity.retentionSettingsPanel(),
                activity.learningStepsSettingsPanel(),
                activity.studyAheadSettingsPanel(),
                activity.studyLadderSettingsPanel(),
                activity.ladderThresholdSettingsPanel()
        ));
        activity.content.addView(activity.settingsCategory(
                dev.bee.kanjianki.core.SettingsTextCopy.settingsAutomationTitle(),
                dev.bee.kanjianki.core.SettingsTextCopy.settingsAutomationBody(),
                R.drawable.ic_sync_24,
                activity.settingsSyncExpanded,
                () -> {
                    activity.settingsSyncExpanded = !activity.settingsSyncExpanded;
                    activity.renderSettings(true);
                },
                activity.reminderSettingsPanel(),
                activity.autoSyncSettingsPanel(),
                activity.updateSettingsPanel()
        ));
        activity.content.addView(activity.settingsCategory(
                dev.bee.kanjianki.core.SettingsTextCopy.settingsReferenceDataTitle(),
                dev.bee.kanjianki.core.SettingsTextCopy.settingsReferenceDataBody(),
                R.drawable.ic_sparkle_24,
                activity.settingsAppExpanded,
                () -> {
                    activity.settingsAppExpanded = !activity.settingsAppExpanded;
                    activity.renderSettings(true);
                },
                activity.dataLicenseSettingsPanel()
        ));
        if (preserveScroll) {
            activity.contentScroll.post(() -> activity.contentScroll.scrollTo(0, scrollY));
        }
    }
}
