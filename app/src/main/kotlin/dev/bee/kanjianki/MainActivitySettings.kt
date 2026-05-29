package dev.bee.kanjianki

import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
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
        val scrollY = if (preserveScroll) {
            contentScrollY
        } else {
            0
        }
        composeRoute(MainActivityBase.NAV_SETTINGS_ROUTE, scrollY) {
            SettingsScreen(
                MainActivitySettingsScreenCoordinator(this).settingsScreenModel()
            )
        }
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
        return MainActivitySettingsLearningPanel(this).learningStepsSettingsPanelModel()
    }

    fun studyLadderSettingsPanelModel(): SettingsStudyLadderPanelModel {
        return MainActivitySettingsStudyLadder(this).studyLadderSettingsPanelModel()
    }

    fun ladderThresholdSettingsPanelModel(): SettingsLadderThresholdPanelModel {
        return MainActivitySettingsLadderThresholdPanel(this).ladderThresholdSettingsPanelModel()
    }

    fun retentionSettingsPanelModel(): SettingsRetentionPanelModel {
        return MainActivitySettingsRetentionPanel(this).retentionSettingsPanelModel()
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
