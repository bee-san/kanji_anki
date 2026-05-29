package dev.bee.kanjianki

import dev.bee.kanjianki.core.SettingsTextCopy

internal object SettingsStudyAheadPanelModels {
    @JvmStatic
    fun create(
        minutes: Int,
        onSave: SettingsStudyAheadSaver,
    ): SettingsStudyAheadPanelModel {
        return SettingsStudyAheadPanelModel(
            title = SettingsTextCopy.studyAheadTitle(),
            body = SettingsTextCopy.studyAheadBody(),
            minutesLabel = SettingsTextCopy.studyAheadMinutesLabel(),
            initialMinutesText = minutes.toString(),
            saveLabel = SettingsTextCopy.saveStudyAheadLabel(),
            onSave = onSave,
        )
    }
}
