package dev.bee.kanjianki

import android.text.InputType
import android.widget.EditText
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import java.util.Locale

internal abstract class MainActivitySettings : MainActivityStudy() {
    private fun ankiSource(): MainActivitySettingsAnkiSource {
        return MainActivitySettingsAnkiSource(this)
    }

    private fun retentionPanel(): MainActivitySettingsRetentionPanel {
        return MainActivitySettingsRetentionPanel(this)
    }

    private fun studyAheadPanel(): MainActivitySettingsStudyAheadPanel {
        return MainActivitySettingsStudyAheadPanel(this)
    }

    private fun ladderThresholdPanel(): MainActivitySettingsLadderThresholdPanel {
        return MainActivitySettingsLadderThresholdPanel(this)
    }

    private fun studySortPanel(): MainActivitySettingsStudySortPanel {
        return MainActivitySettingsStudySortPanel(this)
    }

    private fun workloadPanel(): MainActivitySettingsWorkloadPanel {
        return MainActivitySettingsWorkloadPanel(this)
    }

    private fun learningPanel(): MainActivitySettingsLearningPanel {
        return MainActivitySettingsLearningPanel(this)
    }

    private fun studyLadderUi(): MainActivitySettingsStudyLadder {
        return MainActivitySettingsStudyLadder(this)
    }

    private fun referenceData(): MainActivitySettingsReferenceData {
        return MainActivitySettingsReferenceData(this)
    }

    private fun updatePage(): MainActivitySettingsUpdatePage {
        return MainActivitySettingsUpdatePage(this)
    }

    private fun settingsScreen(): MainActivitySettingsScreen {
        return MainActivitySettingsScreen(this)
    }

    private fun updateFlow(): MainActivitySettingsUpdateFlow {
        return MainActivitySettingsUpdateFlow(this)
    }

    override fun renderUpdate() {
        updatePage().renderUpdate()
    }

    override fun renderSettings() {
        renderSettings(false)
    }

    fun renderSettings(preserveScroll: Boolean) {
        settingsScreen().renderSettings(preserveScroll)
    }

    fun importFilterSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsImportFiltersPanelModel {
        return ankiSource().importFilterSettingsPanelModel(current)
    }

    fun frequencyRangeSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsFrequencyRangePanelModel {
        return ankiSource().frequencyRangeSettingsPanelModel(current)
    }

    fun dataLicenseSettingsPanelModel(): SettingsReferenceDataLinkModel {
        return referenceData().dataLicenseSettingsPanelModel()
    }

    fun renderDataSources() {
        referenceData().renderDataSources()
    }

    fun noteTypeSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNoteTypePanelModel {
        return ankiSource().noteTypeSettingsPanelModel(current)
    }

    fun newCardSortSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNewCardSortPanelModel {
        return studySortPanel().newCardSortSettingsPanelModel(current)
    }

    fun workloadSettingsPanelModel(): SettingsWorkloadPanelModel {
        return workloadPanel().workloadSettingsPanelModel()
    }

    fun learningStepsSettingsPanelModel(): SettingsLearningStepsPanelModel {
        return learningPanel().learningStepsSettingsPanelModel()
    }

    fun studyAheadSettingsPanelModel(): SettingsStudyAheadPanelModel {
        return studyAheadPanel().studyAheadSettingsPanelModel()
    }

    fun studyLadderSettingsPanelModel(): SettingsStudyLadderPanelModel {
        return studyLadderUi().studyLadderSettingsPanelModel()
    }

    fun toggleLadderRung(rung: RecordsBase.LadderRung) {
        studyLadderUi().toggleLadderRung(rung)
    }

    fun ladderThresholdSettingsPanelModel(): SettingsLadderThresholdPanelModel {
        return ladderThresholdPanel().ladderThresholdSettingsPanelModel()
    }

    override fun thresholdInput(value: Int): EditText {
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(String.format(Locale.ROOT, "%d", value.coerceAtLeast(1)))
            textSize = 20f
            setSingleLine(true)
            setSelectAllOnFocus(true)
        }
    }

    override fun parseThresholdInput(input: EditText): Int {
        return input.text.toString().trim().toInt()
    }

    fun retentionSettingsPanelModel(): SettingsRetentionPanelModel {
        return retentionPanel().retentionSettingsPanelModel()
    }

    fun reminderSettingsPanelModel(): SettingsReminderPanelModel {
        return MainActivitySettingsAutomationReminder(this).reminderSettingsPanelModel()
    }

    fun autoSyncSettingsPanelModel(): SettingsAutoSyncPanelModel {
        return MainActivitySettingsAutomationAutoSync(this).autoSyncSettingsPanelModel()
    }

    fun updateSettingsPanelModel(): SettingsUpdateOverviewPanelModel {
        return SettingsUpdateOverviewPanelModel(
            settingsUpdatePanelModel(
                activity = this,
                title = SettingsTextCopy.appUpdatesTitle(),
            ),
            SettingsTextCopy.openUpdaterLabel(),
        ) {
            renderUpdate()
        }
    }

    fun runUpdate(cachedPending: Boolean) {
        updateFlow().runUpdate(cachedPending)
    }
}
