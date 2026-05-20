package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsTextCopy;

import android.view.View;

import java.util.Arrays;
import java.util.List;

final class MainActivitySettingsScreen {
    private final MainActivitySettings activity;

    MainActivitySettingsScreen(MainActivitySettings activity) {
        this.activity = activity;
    }

    void renderSettings(boolean preserveScroll) {
        int scrollY = preserveScroll && activity.contentScroll != null ? activity.contentScroll.getScrollY() : 0;
        activity.base(activity.NAV_SETTINGS_ROUTE);
        RecordsSyncModels.Settings current = activity.settings();
        activity.content.addView(MainActivitySettingsScreenCompose.settingsScreenView(
                activity,
                settingsScreenModel(current)
        ));
        if (preserveScroll) {
            activity.contentScroll.post(() -> activity.contentScroll.scrollTo(0, scrollY));
        }
    }

    SettingsScreenModel settingsScreenModel(RecordsSyncModels.Settings current) {
        return MainActivitySettingsScreenCompose.settingsScreenModel(
                MainActivitySettingsAutomationHeroCompose.settingsAutomationHeroModel(
                        activity,
                        current,
                        activity.store.reminderSettings(),
                        activity.store.autoSyncSettings(),
                        activity.store.autoUpdateStatus()
                ),
                Arrays.asList(
                        settingsCategoryModel(
                                SettingsTextCopy.settingsAnkiSourceTitle(),
                                SettingsTextCopy.settingsAnkiSourceBody(),
                                R.drawable.ic_book_24,
                                activity.settingsAnkiExpanded,
                                () -> {
                                    activity.settingsAnkiExpanded = !activity.settingsAnkiExpanded;
                                    activity.renderSettings(true);
                                },
                                activity.noteTypeSettingsPanel(current),
                                activity.importFilterSettingsPanel(current),
                                activity.frequencyRangeSettingsPanel(current)
                        ),
                        settingsCategoryModel(
                                SettingsTextCopy.settingsStudyBehaviorTitle(),
                                SettingsTextCopy.settingsStudyBehaviorBody(),
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
                        ),
                        settingsCategoryModel(
                                SettingsTextCopy.settingsAutomationTitle(),
                                SettingsTextCopy.settingsAutomationBody(),
                                R.drawable.ic_sync_24,
                                activity.settingsSyncExpanded,
                                () -> {
                                    activity.settingsSyncExpanded = !activity.settingsSyncExpanded;
                                    activity.renderSettings(true);
                                },
                                activity.reminderSettingsPanel(),
                                activity.autoSyncSettingsPanel(),
                                activity.updateSettingsPanel()
                        ),
                        settingsCategoryModel(
                                SettingsTextCopy.settingsReferenceDataTitle(),
                                SettingsTextCopy.settingsReferenceDataBody(),
                                R.drawable.ic_sparkle_24,
                                activity.settingsAppExpanded,
                                () -> {
                                    activity.settingsAppExpanded = !activity.settingsAppExpanded;
                                    activity.renderSettings(true);
                                },
                                activity.dataLicenseSettingsPanel()
                        )
                ),
                activity::renderHome
        );
    }

    private SettingsCategorySectionModel settingsCategoryModel(
            String title,
            String summary,
            int iconRes,
            boolean expanded,
            Runnable toggle,
            View... panels
    ) {
        List<View> panelList = Arrays.asList(panels);
        return MainActivitySettingsScreenCompose.settingsCategorySectionModel(
                title,
                summary,
                iconRes,
                expanded,
                toggle,
                panelList
        );
    }
}
