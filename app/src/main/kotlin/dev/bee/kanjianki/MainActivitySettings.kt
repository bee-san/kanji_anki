package dev.bee.kanjianki

import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.LearningStepsSettingsPolicy
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.StudyAheadSettingsPolicy
import dev.bee.kanjianki.core.StudyLadderThresholdPolicy
import dev.bee.kanjianki.reminders.ReminderScheduler
import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.updatecore.UpdateRunScreenCopy
import java.util.Locale

internal abstract class MainActivitySettings : MainActivityStudy() {
    private fun ankiSource(): MainActivitySettingsAnkiSource {
        return MainActivitySettingsAnkiSource(this)
    }

    private fun retentionPanel(): MainActivitySettingsRetentionPanel {
        return MainActivitySettingsRetentionPanel(this)
    }

    private fun studySortPanel(): MainActivitySettingsStudySortPanel {
        return MainActivitySettingsStudySortPanel(this)
    }

    private fun workloadPanel(): MainActivitySettingsWorkloadPanel {
        return MainActivitySettingsWorkloadPanel(this)
    }

    private fun studyLadderUi(): MainActivitySettingsStudyLadder {
        return MainActivitySettingsStudyLadder(this)
    }

    private fun referenceData(): MainActivitySettingsReferenceData {
        return MainActivitySettingsReferenceData(this)
    }

    override fun renderUpdate() {
        val model = settingsUpdatePageModel(this)
        composeRoute(MainActivityBase.NAV_SETTINGS_ROUTE) {
            SettingsUpdatePage(model)
        }
    }

    override fun renderSettings() {
        renderSettings(false)
    }

    fun renderSettings(preserveScroll: Boolean) {
        val previousScroll = contentScroll
        val scrollY = if (preserveScroll && previousScroll != null) {
            previousScroll.scrollY
        } else {
            0
        }
        val current = settings()
        composeRoute(MainActivityBase.NAV_SETTINGS_ROUTE, scrollY) {
            SettingsScreen(settingsScreenModel(current))
        }
    }

    private fun settingsScreenModel(current: RecordsSyncModels.Settings): SettingsScreenModel {
        return settingsScreenModel(
            settingsAutomationHeroModel(
                current,
                store.reminderSettings(),
                store.autoSyncSettings(),
                store.autoUpdateStatus(),
                ReminderScheduler.notificationsAllowed(this)
            ),
            listOf(
                settingsAnkiSourceCategoryModel(
                    settingsAnkiExpanded,
                    Runnable {
                        settingsAnkiExpanded = !settingsAnkiExpanded
                        renderSettings(true)
                    },
                    noteTypeSettingsPanelModel(current),
                    importFilterSettingsPanelModel(current),
                    frequencyRangeSettingsPanelModel(current)
                ),
                settingsStudyBehaviorCategoryModel(
                    settingsStudyExpanded,
                    Runnable {
                        settingsStudyExpanded = !settingsStudyExpanded
                        renderSettings(true)
                    },
                    newCardSortSettingsPanelModel(current),
                    workloadSettingsPanelModel(),
                    retentionSettingsPanelModel(),
                    learningStepsSettingsPanelModel(),
                    studyAheadSettingsPanelModel(),
                    studyLadderSettingsPanelModel(),
                    ladderThresholdSettingsPanelModel()
                ),
                settingsAutomationCategoryModel(
                    settingsSyncExpanded,
                    Runnable {
                        settingsSyncExpanded = !settingsSyncExpanded
                        renderSettings(true)
                    },
                    reminderSettingsPanelModel(),
                    autoSyncSettingsPanelModel(),
                    updateSettingsPanelModel()
                ),
                settingsReferenceDataCategoryModel(
                    settingsAppExpanded,
                    Runnable {
                        settingsAppExpanded = !settingsAppExpanded
                        renderSettings(true)
                    },
                    dataLicenseSettingsPanelModel()
                )
            ),
            Runnable { renderHome() }
        )
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
        val current = store.learningStepSettings()
        val defaults = RecordsSchedulerModels.LearningStepSettings.defaults()
        return SettingsLearningStepsPanelModel(
            title = SettingsTextCopy.learningStepsTitle(),
            body = SettingsTextCopy.learningStepsBody(),
            newCardsLabel = MainActivityBase.LABEL_NEW_CARDS,
            initialNewStepsText = current.newStepsText(),
            reviewMissesLabel = SettingsTextCopy.reviewMissesLabel(),
            initialReviewStepsText = current.reviewStepsText(),
            defaultNewStepsText = defaults.newStepsText(),
            defaultReviewStepsText = defaults.reviewStepsText(),
            ankiDefaultLabel = SettingsTextCopy.ankiDefaultLabel(),
            sameStepsLabel = SettingsTextCopy.sameLearningStepsLabel(),
            saveLabel = SettingsTextCopy.saveLearningStepsLabel(),
            onSave = SettingsLearningStepsSaveAction { newStepsText, reviewStepsText ->
                saveLearningSteps(newStepsText, reviewStepsText)
            }
        )
    }

    private fun saveLearningSteps(newStepsText: String, reviewStepsText: String) {
        val request = LearningStepsSettingsPolicy.saveRequest(newStepsText, reviewStepsText)
        if (!request.valid) {
            Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show()
            return
        }
        SettingsWriteActions.saveLearningSteps(request, store::saveLearningStepSettings)
        Toast.makeText(this, SettingsTextCopy.learningStepsSavedToast(), Toast.LENGTH_SHORT).show()
        renderSettings()
    }

    fun studyAheadSettingsPanelModel(): SettingsStudyAheadPanelModel {
        return SettingsStudyAheadPanelModel(
            title = SettingsTextCopy.studyAheadTitle(),
            body = SettingsTextCopy.studyAheadBody(),
            minutesLabel = SettingsTextCopy.studyAheadMinutesLabel(),
            initialMinutesText = store.studyAheadMinutes().toString(),
            saveLabel = SettingsTextCopy.saveStudyAheadLabel(),
            onSave = SettingsStudyAheadSaver { minutesText -> saveStudyAhead(minutesText) }
        )
    }

    private fun saveStudyAhead(minutesText: String) {
        val request = StudyAheadSettingsPolicy.saveRequest(minutesText)
        if (!request.valid) {
            Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show()
            return
        }
        store.saveStudyAheadMinutes(request.minutes)
        Toast.makeText(this, SettingsTextCopy.studyAheadSavedToast(), Toast.LENGTH_SHORT).show()
        renderSettings()
    }

    fun studyLadderSettingsPanelModel(): SettingsStudyLadderPanelModel {
        return studyLadderUi().studyLadderSettingsPanelModel()
    }

    fun toggleLadderRung(rung: RecordsBase.LadderRung) {
        studyLadderUi().toggleLadderRung(rung)
    }

    fun ladderThresholdSettingsPanelModel(): SettingsLadderThresholdPanelModel {
        val current = settings()
        return SettingsLadderThresholdPanelModel(
            title = SettingsTextCopy.ladderThresholdsTitle(),
            body = SettingsTextCopy.ladderThresholdsBody(),
            promotionDaysLabel = SettingsTextCopy.fsrsDaysToGoUpLabel(),
            initialPromotionDaysText = thresholdText(current.ladderPromotionIntervalDays),
            failStreakLabel = SettingsTextCopy.failsToGoDownLabel(),
            initialFailStreakText = thresholdText(current.ladderDemotionFailStreak),
            defaultPromotionDaysText = RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS.toString(),
            defaultFailStreakText = RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK.toString(),
            defaultsLabel = SettingsTextCopy.useDefaultLadderThresholdsLabel(),
            saveLabel = SettingsTextCopy.saveLadderThresholdsLabel(),
            onSave = SettingsLadderThresholdSaveAction { promotionDaysText, failStreakText ->
                saveLadderThresholds(promotionDaysText, failStreakText)
            }
        )
    }

    private fun saveLadderThresholds(promotionDaysText: String, failStreakText: String) {
        val request = StudyLadderThresholdPolicy.saveRequest(promotionDaysText, failStreakText)
        if (!request.valid) {
            Toast.makeText(this, request.message, Toast.LENGTH_SHORT).show()
            return
        }
        SettingsWriteActions.saveLadderThresholds(request, store::putIntSetting)
        Toast.makeText(this, SettingsTextCopy.ladderThresholdsSavedToast(), Toast.LENGTH_SHORT).show()
        renderSettings()
    }

    private companion object {
        fun thresholdText(value: Int): String = value.coerceAtLeast(1).toString()
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
        val copy = UpdateRunScreenCopy.forRun(cachedPending)
        composeRoute(MainActivityBase.NAV_SETTINGS_ROUTE) {
            SettingsUpdateRunScreen(
                model = SettingsUpdateRunModel(
                    title = copy.title(),
                    body = copy.body(),
                    progressLabel = copy.progressLabel(),
                    onHome = ::renderHome,
                    onBack = { renderSettings(false) },
                )
            )
        }
        val updateUiRun = ++updateUiRunCounter
        activeUpdateUiRunToken = updateUiRun
        io.execute {
            val updater = GitHubUpdater(this)
            val result = if (cachedPending) {
                updater.installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)
            } else {
                updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)
            }
            main.post {
                if (activeUpdateUiRunToken != updateUiRun) {
                    return@post
                }
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                result.intent?.let(::startActivity)
                renderUpdate()
            }
        }
    }
}
