package dev.bee.kanjianki

import dev.bee.kanjianki.core.SettingsTextCopy

internal fun settingsAnkiSourceCategoryModel(
    expanded: Boolean,
    onToggle: Runnable,
    noteType: SettingsNoteTypePanelModel,
    importFilters: SettingsImportFiltersPanelModel,
    frequencyRange: SettingsFrequencyRangePanelModel,
    autoSync: SettingsAutoSyncPanelModel,
): SettingsCategorySectionModel {
    return settingsCategorySectionModel(
        SettingsTextCopy.settingsAnkiSourceTitle(),
        SettingsTextCopy.settingsAnkiSourceBody(),
        R.drawable.ic_book_24,
        expanded,
        onToggle,
        listOf(noteType, importFilters, frequencyRange, autoSync),
    )
}

internal fun settingsStudyBehaviorCategoryModel(
    expanded: Boolean,
    onToggle: Runnable,
    newCardSort: SettingsNewCardSortPanelModel,
    workload: SettingsWorkloadPanelModel,
    retention: SettingsRetentionPanelModel,
    learningSteps: SettingsLearningStepsPanelModel,
    studyAhead: SettingsStudyAheadPanelModel,
    studyLadder: SettingsStudyLadderPanelModel,
    ladderThreshold: SettingsLadderThresholdPanelModel,
): SettingsCategorySectionModel {
    return settingsCategorySectionModel(
        SettingsTextCopy.settingsStudyBehaviorTitle(),
        SettingsTextCopy.settingsStudyBehaviorBody(),
        R.drawable.ic_study_24,
        expanded,
        onToggle,
        listOf(
            newCardSort,
            workload,
            retention,
            learningSteps,
            studyAhead,
            studyLadder,
            ladderThreshold,
        ),
    )
}

internal fun settingsAutomationCategoryModel(
    expanded: Boolean,
    onToggle: Runnable,
    reminder: SettingsReminderPanelModel,
    update: SettingsUpdateOverviewPanelModel,
): SettingsCategorySectionModel {
    return settingsCategorySectionModel(
        SettingsTextCopy.settingsAutomationTitle(),
        SettingsTextCopy.settingsAutomationBody(),
        R.drawable.ic_sync_24,
        expanded,
        onToggle,
        listOf(reminder, update),
    )
}

internal fun settingsReferenceDataCategoryModel(
    expanded: Boolean,
    onToggle: Runnable,
    dataLicense: SettingsReferenceDataLinkModel,
): SettingsCategorySectionModel {
    return settingsCategorySectionModel(
        SettingsTextCopy.settingsReferenceDataTitle(),
        SettingsTextCopy.settingsReferenceDataBody(),
        R.drawable.ic_sparkle_24,
        expanded,
        onToggle,
        listOf(dataLicense),
    )
}
