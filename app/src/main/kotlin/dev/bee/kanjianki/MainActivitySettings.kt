package dev.bee.kanjianki

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.compose.runtime.Composable
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.updatecore.UpdateRunScreenCopy
import java.util.Locale

internal abstract class MainActivitySettings : MainActivityStudy() {
    internal var settingsScrollY = 0
    private val settingsRouteScrolls = mutableMapOf<String, Int>()
    internal var cachedNewCardSortPreviewRows: SettingsNewCardSortPreviewRowsSnapshot? = null
    internal var newCardSortPreviewRefreshPending = false
    internal var newCardSortPreviewRerenderOnResumePending = false

    private fun ankiSource(): MainActivitySettingsAnkiSource {
        return MainActivitySettingsAnkiSource(this)
    }

    override fun renderUpdate() {
        renderUpdate(false)
    }

    internal fun renderUpdate(preserveScroll: Boolean) {
        renderSettingsRoute(MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE, preserveScroll)
    }

    override fun renderSettings() {
        renderSettings(false)
    }

    override fun renderSettings(preserveScroll: Boolean) {
        renderSettingsRoute(MainActivityBase.NAV_SETTINGS_ROUTE, preserveScroll)
    }

    internal fun renderSettingsImportSync(preserveScroll: Boolean = false) {
        renderSettingsRoute(MainActivityBase.NAV_SETTINGS_IMPORT_SYNC_ROUTE, preserveScroll)
    }

    internal fun renderSettingsStudyBehavior(preserveScroll: Boolean = false) {
        renderSettingsRoute(MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE, preserveScroll)
    }

    override fun renderDeferredStudyBehaviorPreviewIfNeeded() {
        if (!newCardSortPreviewRerenderOnResumePending) {
            return
        }
        newCardSortPreviewRerenderOnResumePending = false
        if (currentRoute == MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE) {
            renderSettingsStudyBehavior(true)
        }
    }

    internal fun renderSettingsAutomation(preserveScroll: Boolean = false) {
        renderSettingsRoute(MainActivityBase.NAV_SETTINGS_AUTOMATION_ROUTE, preserveScroll)
    }

    internal fun renderSettingsAppearance(preserveScroll: Boolean = false) {
        renderSettingsRoute(MainActivityBase.NAV_SETTINGS_APPEARANCE_ROUTE, preserveScroll)
    }

    internal fun renderSettingsDisplayData(preserveScroll: Boolean = false) {
        renderSettingsRoute(MainActivityBase.NAV_SETTINGS_DISPLAY_DATA_ROUTE, preserveScroll)
    }

    internal fun renderTimingDiagnostics(preserveScroll: Boolean = false) {
        renderSettingsRoute(MainActivityBase.NAV_SETTINGS_TIMING_DIAGNOSTICS_ROUTE, preserveScroll)
    }

    internal fun renderReferenceDataDetails(preserveScroll: Boolean = false) {
        renderSettingsRoute(MainActivityBase.NAV_SETTINGS_LICENSES_ROUTE, preserveScroll)
    }

    fun copyTimingDiagnosticsReport() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(SettingsTextCopy.timingDiagnosticsCopyLabel(), AppTimingDiagnostics.snapshot().reportText()))
        Toast.makeText(this, SettingsTextCopy.timingDiagnosticsCopiedToast(), Toast.LENGTH_SHORT).show()
    }

    fun resetTimingDiagnostics() {
        AppTimingDiagnostics.reset()
        Toast.makeText(this, SettingsTextCopy.timingDiagnosticsResetToast(), Toast.LENGTH_SHORT).show()
        if (currentRoute == MainActivityBase.NAV_SETTINGS_TIMING_DIAGNOSTICS_ROUTE) {
            renderTimingDiagnostics(true)
        }
    }

    fun prewarmTimingDiagnosticsAssets() {
        io.execute {
            AppTimingDiagnostics.markStudyPrewarmStarted()
            runCatching { currentDictionaryLookup() }.onSuccess { AppTimingDiagnostics.markStudyPrewarmDictionary() }
            runCatching { strokeGuide("日") }.onSuccess { AppTimingDiagnostics.markStudyPrewarmStroke() }
            AppTimingDiagnostics.markStudyPrewarmFinished()
            main.post {
                Toast.makeText(this, SettingsTextCopy.timingDiagnosticsPrewarmToast(), Toast.LENGTH_SHORT).show()
                if (currentRoute == MainActivityBase.NAV_SETTINGS_TIMING_DIAGNOSTICS_ROUTE) {
                    renderTimingDiagnostics(true)
                }
            }
        }
    }

    private fun renderSettingsRoute(route: String, preserveScroll: Boolean = false) {
        cancelPendingHomeRouteLoads()
        activeUpdateUiRunToken = 0
        if (isScreenshotLaunchRequested()) {
            when (route) {
                MainActivityBase.NAV_SETTINGS_ROUTE -> {
                    renderScreenshotSettings()
                    return
                }
                MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE -> {
                    renderScreenshotUpdate()
                    return
                }
            }
        }
        val scrollY = if (preserveScroll) settingsScrollFor(route) else 0
        val onScrollY: (Int) -> Unit = { rememberSettingsScroll(route, it) }
        when (route) {
            MainActivityBase.NAV_SETTINGS_ROUTE -> {
                val model = MainActivitySettingsScreenCoordinator(this).settingsScreenModel()
                composeSettingsRoute(route, scrollY, onScrollY) {
                    SettingsScreen(model)
                }
                backAction = Runnable { renderHome() }
            }
            MainActivityBase.NAV_SETTINGS_IMPORT_SYNC_ROUTE -> {
                val model = MainActivitySettingsScreenCoordinator(this).settingsImportSyncScreenModel()
                composeSettingsRoute(route, scrollY, onScrollY) {
                    SettingsSubmenuScreen(model)
                }
                backAction = Runnable { renderSettings(true) }
            }
            MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE -> {
                val model = MainActivitySettingsScreenCoordinator(this).settingsStudyBehaviorScreenModel()
                composeSettingsRoute(route, scrollY, onScrollY) {
                    SettingsSubmenuScreen(model)
                }
                backAction = Runnable { renderSettings(true) }
            }
            MainActivityBase.NAV_SETTINGS_AUTOMATION_ROUTE -> {
                val model = MainActivitySettingsScreenCoordinator(this).settingsAutomationScreenModel()
                composeSettingsRoute(route, scrollY, onScrollY) {
                    SettingsSubmenuScreen(model)
                }
                backAction = Runnable { renderSettings(true) }
            }
            MainActivityBase.NAV_SETTINGS_APPEARANCE_ROUTE -> {
                val model = MainActivitySettingsScreenCoordinator(this).settingsAppearanceScreenModel()
                composeSettingsRoute(route, scrollY, onScrollY) {
                    SettingsSubmenuScreen(model)
                }
                backAction = Runnable { renderSettings(true) }
            }
            MainActivityBase.NAV_SETTINGS_DISPLAY_DATA_ROUTE -> {
                val model = MainActivitySettingsScreenCoordinator(this).settingsDisplayDataScreenModel()
                composeSettingsRoute(route, scrollY, onScrollY) {
                    SettingsSubmenuScreen(model)
                }
                backAction = Runnable { renderSettings(true) }
            }
            MainActivityBase.NAV_SETTINGS_TIMING_DIAGNOSTICS_ROUTE -> {
                val model = MainActivitySettingsScreenCoordinator(this).settingsTimingDiagnosticsScreenModel()
                composeSettingsRoute(route, scrollY, onScrollY) {
                    SettingsSubmenuScreen(model)
                }
                backAction = Runnable { renderSettings(true) }
            }
            MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE -> {
                composeSettingsRoute(route, scrollY, onScrollY) {
                    SettingsUpdatePage(
                        model = SettingsUpdatePageModel(
                            title = SettingsTextCopy.updatePageTitle(),
                            onHome = this@MainActivitySettings::renderHome,
                            onBack = { renderSettingsAutomation(true) },
                            onCheckForUpdate = { runUpdate(false) },
                            panel = settingsUpdatePanelModel(
                                activity = this@MainActivitySettings,
                                title = SettingsTextCopy.automaticUpdatesTitle(),
                            ),
                        ),
                    )
                }
                backAction = Runnable { renderSettingsAutomation(true) }
            }
            MainActivityBase.NAV_SETTINGS_LICENSES_ROUTE -> {
                val model = MainActivitySettingsReferenceData(this).referenceDataScreenModel()
                composeSettingsRoute(route, scrollY, onScrollY) {
                    ReferenceDataScreen(model)
                }
                backAction = Runnable { renderSettingsDisplayData(true) }
            }
            else -> {
                renderSettings()
            }
        }
    }

    private fun renderScreenshotSettings() {
        val model = screenshotSettingsScreenModel(this)
        composeRoute(
            MainActivityBase.NAV_SETTINGS_ROUTE,
            initialScrollY = screenshotScrollY(),
            scrollPositionLabel = screenshotScrollPositionLabel(),
        ) {
            SettingsScreen(model)
        }
    }

    private fun renderScreenshotUpdate() {
        val model = screenshotUpdatePageModel(this)
        composeRoute(
            MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE,
            initialScrollY = screenshotScrollY(),
            scrollPositionLabel = screenshotScrollPositionLabel(),
        ) {
            SettingsUpdatePage(model)
        }
    }

    private fun composeSettingsRoute(route: String, initialScrollY: Int, onScrollY: (Int) -> Unit, content: @Composable () -> Unit) {
        composeRoute(route, initialScrollY, onScrollY = onScrollY, content = content)
    }

    private fun rememberSettingsScroll(route: String, scrollY: Int) {
        settingsRouteScrolls[route] = scrollY
        if (route == MainActivityBase.NAV_SETTINGS_ROUTE) {
            settingsScrollY = scrollY
        }
    }

    private fun settingsScrollFor(route: String): Int {
        return settingsRouteScrolls[route] ?: if (route == MainActivityBase.NAV_SETTINGS_ROUTE) settingsScrollY else 0
    }

    fun runSettingsWrite(
        traceSection: String,
        write: () -> Unit,
        onComplete: () -> Unit,
    ) {
        io.execute {
            withUiTrace(traceSection) {
                write()
            }
            main.post(onComplete)
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

    internal fun themeSettingsPanelModel(): SettingsThemePanelModel {
        return MainActivitySettingsThemePanel(this).themeSettingsPanelModel()
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
        composeSettingsRoute(MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE, settingsScrollFor(MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE), { rememberSettingsScroll(MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE, it) }) {
            SettingsUpdateRunScreen(
                model = SettingsUpdateRunModel(
                    title = copy.title(),
                    progressLabel = copy.progressLabel(),
                    onHome = ::renderHome,
                    onBack = {
                        renderSettingsAutomation(true)
                    },
                )
            )
        }
        backAction = Runnable {
            renderSettingsAutomation(true)
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
                renderUpdate(true)
            }
        }
    }
}
