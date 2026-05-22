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

    override fun renderUpdate() {
        composeRoute(MainActivityBase.NAV_SETTINGS_ROUTE) {
            SettingsUpdatePage(
                SettingsUpdatePageModel(
                    title = SettingsTextCopy.updatePageTitle(),
                    body = SettingsTextCopy.updatePageBody(BuildConfig.VERSION_NAME),
                    onHome = this@MainActivitySettings::renderHome,
                    onBack = { renderSettings(false) },
                    onCheckForUpdate = { runUpdate(false) },
                    panel = settingsUpdatePanelModel(
                        activity = this@MainActivitySettings,
                        title = SettingsTextCopy.automaticUpdatesTitle()
                    )
                )
            )
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
                    MainActivitySettingsStudySortPanel(this).newCardSortSettingsPanelModel(current),
                    MainActivitySettingsWorkloadPanel(this).workloadSettingsPanelModel(),
                    MainActivitySettingsRetentionPanel(this).retentionSettingsPanelModel(),
                    learningStepsSettingsPanelModel(),
                    SettingsStudyAheadPanelModel(
                        title = SettingsTextCopy.studyAheadTitle(),
                        body = SettingsTextCopy.studyAheadBody(),
                        minutesLabel = SettingsTextCopy.studyAheadMinutesLabel(),
                        initialMinutesText = store.studyAheadMinutes().toString(),
                        saveLabel = SettingsTextCopy.saveStudyAheadLabel(),
                        onSave = SettingsStudyAheadSaver { minutesText -> saveStudyAhead(minutesText) }
                    ),
                    MainActivitySettingsStudyLadder(this).studyLadderSettingsPanelModel(),
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
                    SettingsUpdateOverviewPanelModel(
                        settingsUpdatePanelModel(
                            activity = this,
                            title = SettingsTextCopy.appUpdatesTitle(),
                        ),
                        SettingsTextCopy.openUpdaterLabel(),
                    ) {
                        renderUpdate()
                    }
                ),
                settingsReferenceDataCategoryModel(
                    settingsAppExpanded,
                    Runnable {
                        settingsAppExpanded = !settingsAppExpanded
                        renderSettings(true)
                    },
                    MainActivitySettingsReferenceData(this).dataLicenseSettingsPanelModel()
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

    fun noteTypeSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNoteTypePanelModel {
        return ankiSource().noteTypeSettingsPanelModel(current)
    }

    fun newCardSortSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNewCardSortPanelModel {
        return MainActivitySettingsStudySortPanel(this).newCardSortSettingsPanelModel(current)
    }

    fun workloadSettingsPanelModel(): SettingsWorkloadPanelModel {
        return MainActivitySettingsWorkloadPanel(this).workloadSettingsPanelModel()
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

    fun studyLadderSettingsPanelModel(): SettingsStudyLadderPanelModel {
        return MainActivitySettingsStudyLadder(this).studyLadderSettingsPanelModel()
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

    fun retentionSettingsPanelModel(): SettingsRetentionPanelModel {
        return MainActivitySettingsRetentionPanel(this).retentionSettingsPanelModel()
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

    fun reminderSettingsPanelModel(): SettingsReminderPanelModel {
        return MainActivitySettingsAutomationReminder(this).reminderSettingsPanelModel()
    }

    fun autoSyncSettingsPanelModel(): SettingsAutoSyncPanelModel {
        return MainActivitySettingsAutomationAutoSync(this).autoSyncSettingsPanelModel()
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
