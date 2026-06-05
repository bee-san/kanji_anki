package dev.bee.kanjianki

import dev.bee.kanjianki.core.SettingsTextCopy

private const val SETTINGS_SECTION_ANKI_SOURCE = "settings-anki-source"
private const val SETTINGS_SECTION_STUDY_BEHAVIOR = "settings-study-behavior"
private const val SETTINGS_SECTION_AUTOMATION = "settings-automation"
private const val SETTINGS_SECTION_REFERENCE_DATA = "settings-reference-data"

internal fun settingsAnkiSourceCategoryModel(
    expanded: Boolean,
    onToggle: Runnable,
    noteType: SettingsNoteTypePanelModel,
    importFilters: SettingsImportFiltersPanelModel,
    frequencyRange: SettingsFrequencyRangePanelModel,
    autoSync: SettingsAutoSyncPanelModel,
): SettingsCategorySectionModel {
    return settingsCategorySectionModel(
        sectionKey = SETTINGS_SECTION_ANKI_SOURCE,
        title = SettingsTextCopy.settingsAnkiSourceTitle(),
        summary = SettingsTextCopy.settingsAnkiSourceBody(),
        iconRes = R.drawable.ic_book_24,
        expanded = expanded,
        onToggle = onToggle,
        panels = listOf(noteType, importFilters, frequencyRange, autoSync),
    )
}

internal fun settingsStudyBehaviorCategoryModel(
    expanded: Boolean,
    onToggle: Runnable,
    newCardSort: SettingsNewCardSortPanelModel,
    deckLimits: SettingsDeckLimitsPanelModel,
    workload: SettingsWorkloadPanelModel,
    retention: SettingsRetentionPanelModel,
    learningSteps: SettingsLearningStepsPanelModel,
    studyAhead: SettingsStudyAheadPanelModel,
    studyLadder: SettingsStudyLadderPanelModel,
    ladderThreshold: SettingsLadderThresholdPanelModel,
): SettingsCategorySectionModel {
    return settingsCategorySectionModel(
        sectionKey = SETTINGS_SECTION_STUDY_BEHAVIOR,
        title = SettingsTextCopy.settingsStudyBehaviorTitle(),
        summary = SettingsTextCopy.settingsStudyBehaviorBody(),
        iconRes = R.drawable.ic_study_24,
        expanded = expanded,
        onToggle = onToggle,
        panels = listOf(
            newCardSort,
            deckLimits,
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
        sectionKey = SETTINGS_SECTION_AUTOMATION,
        title = SettingsTextCopy.settingsAutomationTitle(),
        summary = "",
        iconRes = R.drawable.ic_sync_24,
        expanded = expanded,
        onToggle = onToggle,
        panels = listOf(reminder, update),
    )
}

internal fun settingsReferenceDataCategoryModel(
    expanded: Boolean,
    onToggle: Runnable,
    dataLicense: SettingsReferenceDataLinkModel,
): SettingsCategorySectionModel {
    return settingsCategorySectionModel(
        sectionKey = SETTINGS_SECTION_REFERENCE_DATA,
        title = SettingsTextCopy.settingsReferenceDataTitle(),
        summary = "",
        iconRes = R.drawable.ic_sparkle_24,
        expanded = expanded,
        onToggle = onToggle,
        panels = listOf(dataLicense),
    )
}
