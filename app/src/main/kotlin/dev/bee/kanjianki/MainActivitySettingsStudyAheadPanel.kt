package dev.bee.kanjianki

import android.view.View
import android.widget.Toast
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.StudyAheadSettingsPolicy

internal class MainActivitySettingsStudyAheadPanel(private val activity: MainActivitySettings) {
    fun studyAheadSettingsPanel(): View {
        return studyAheadSettingsPanelView(activity, studyAheadSettingsPanelModel())
    }

    fun studyAheadSettingsPanelModel(): SettingsStudyAheadPanelModel {
        return SettingsStudyAheadPanelModel(
            title = SettingsTextCopy.studyAheadTitle(),
            body = SettingsTextCopy.studyAheadBody(),
            minutesLabel = SettingsTextCopy.studyAheadMinutesLabel(),
            initialMinutesText = activity.store.studyAheadMinutes().toString(),
            saveLabel = SettingsTextCopy.saveStudyAheadLabel(),
            onSave = SettingsStudyAheadSaver { minutesText -> saveStudyAhead(minutesText) }
        )
    }

    private fun saveStudyAhead(minutesText: String) {
        val request = StudyAheadSettingsPolicy.saveRequest(minutesText)
        if (!request.valid) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
            return
        }
        activity.store.saveStudyAheadMinutes(request.minutes)
        Toast.makeText(activity, SettingsTextCopy.studyAheadSavedToast(), Toast.LENGTH_SHORT).show()
        activity.renderSettings()
    }
}
