package dev.bee.kanjianki

import android.text.InputType
import android.view.View
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

    fun importFilterSettingsPanel(current: RecordsSyncModels.Settings): View {
        return ankiSource().importFilterSettingsPanel(current)
    }

    fun importFilterSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsImportFiltersPanelModel {
        return ankiSource().importFilterSettingsPanelModel(current)
    }

    fun frequencyRangeSettingsPanel(current: RecordsSyncModels.Settings): View {
        return ankiSource().frequencyRangeSettingsPanel(current)
    }

    fun frequencyRangeSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsFrequencyRangePanelModel {
        return ankiSource().frequencyRangeSettingsPanelModel(current)
    }

    fun dataLicenseSettingsPanel(): View {
        return referenceData().dataLicenseSettingsPanel()
    }

    fun dataLicenseSettingsPanelModel(): SettingsReferenceDataLinkModel {
        return referenceData().dataLicenseSettingsPanelModel()
    }

    fun renderDataSources() {
        referenceData().renderDataSources()
    }

    fun noteTypeSettingsPanel(current: RecordsSyncModels.Settings): View {
        return ankiSource().noteTypeSettingsPanel(current)
    }

    fun noteTypeSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNoteTypePanelModel {
        return ankiSource().noteTypeSettingsPanelModel(current)
    }

    fun newCardSortSettingsPanel(current: RecordsSyncModels.Settings): View {
        return studySortPanel().newCardSortSettingsPanel(current)
    }

    fun newCardSortSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNewCardSortPanelModel {
        return studySortPanel().newCardSortSettingsPanelModel(current)
    }

    fun workloadSettingsPanel(): View {
        return workloadPanel().workloadSettingsPanel()
    }

    fun workloadSettingsPanelModel(): SettingsWorkloadPanelModel {
        return workloadPanel().workloadSettingsPanelModel()
    }

    fun learningStepsSettingsPanel(): View {
        return learningPanel().learningStepsSettingsPanel()
    }

    fun learningStepsSettingsPanelModel(): SettingsLearningStepsPanelModel {
        return learningPanel().learningStepsSettingsPanelModel()
    }

    fun studyAheadSettingsPanel(): View {
        return studyAheadPanel().studyAheadSettingsPanel()
    }

    fun studyAheadSettingsPanelModel(): SettingsStudyAheadPanelModel {
        return studyAheadPanel().studyAheadSettingsPanelModel()
    }

    fun studyLadderSettingsPanel(): View {
        return studyLadderUi().studyLadderSettingsPanel()
    }

    fun studyLadderSettingsPanelModel(): SettingsStudyLadderPanelModel {
        return studyLadderUi().studyLadderSettingsPanelModel()
    }

    fun toggleLadderRung(rung: RecordsBase.LadderRung) {
        studyLadderUi().toggleLadderRung(rung)
    }

    fun ladderThresholdSettingsPanel(): View {
        return ladderThresholdPanel().ladderThresholdSettingsPanel()
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

    fun retentionSettingsPanel(): View {
        return retentionPanel().retentionSettingsPanel()
    }

    fun retentionSettingsPanelModel(): SettingsRetentionPanelModel {
        return retentionPanel().retentionSettingsPanelModel()
    }

    fun reminderSettingsPanel(): View {
        return MainActivitySettingsAutomationReminder(this).reminderSettingsPanel()
    }

    fun reminderSettingsPanelModel(): SettingsReminderPanelModel {
        return MainActivitySettingsAutomationReminder(this).reminderSettingsPanelModel()
    }

    fun autoSyncSettingsPanel(): View {
        return MainActivitySettingsAutomationAutoSync(this).autoSyncSettingsPanel()
    }

    fun autoSyncSettingsPanelModel(): SettingsAutoSyncPanelModel {
        return MainActivitySettingsAutomationAutoSync(this).autoSyncSettingsPanelModel()
    }

    fun updateSettingsPanel(): View {
        return settingsUpdateOverviewPanelView(this, updateSettingsPanelModel())
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
