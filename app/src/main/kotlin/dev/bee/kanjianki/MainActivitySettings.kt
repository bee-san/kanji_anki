package dev.bee.kanjianki

import android.net.Uri
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.compose.runtime.Composable
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.updatecore.UpdateRunScreenCopy
import java.util.Locale
import kotlin.system.exitProcess

internal abstract class MainActivitySettings : MainActivityStudy() {
    internal var settingsScrollY = 0
    private val settingsRouteScrolls = mutableMapOf<String, Int>()
    internal var cachedNewCardSortPreviewRows: SettingsNewCardSortPreviewRowsSnapshot? = null
    internal var newCardSortPreviewRefreshPending = false
    internal var newCardSortPreviewRerenderOnResumePending = false
    internal var pendingBackupRestoreDialog: BackupRestoreConfirmDialogModel? = null
    private val backupRestoreSettings by lazy { MainActivitySettingsAutomationBackup(this) }

    private fun ankiSource(): MainActivitySettingsAnkiSource {
        return MainActivitySettingsAnkiSource(this)
    }

    protected final override fun onBackupExportDocumentSelected(uri: Uri?) {
        backupRestoreSettings.onExportDocumentSelected(uri)
    }

    protected final override fun onBackupRestoreDocumentSelected(uri: Uri?) {
        backupRestoreSettings.onRestoreDocumentSelected(uri)
    }

    override fun renderUpdate() {
        disableStudyOrdinaryResume()
        renderUpdate(false)
    }

    internal fun renderUpdate(preserveScroll: Boolean) {
        renderSettingsRoute(MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE, preserveScroll)
    }

    override fun renderSettings() {
        disableStudyOrdinaryResume()
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

    internal fun renderReferenceDataDetails(preserveScroll: Boolean = false) {
        renderSettingsRoute(MainActivityBase.NAV_SETTINGS_LICENSES_ROUTE, preserveScroll)
    }

    internal fun renderHowItWorks(preserveScroll: Boolean = false) {
        renderSettingsRoute(MainActivityBase.NAV_SETTINGS_HOW_IT_WORKS_ROUTE, preserveScroll)
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
        val coordinator = MainActivitySettingsScreenCoordinator(this)
        when (route) {
            MainActivityBase.NAV_SETTINGS_ROUTE -> renderSettingsRouteAsync(
                route = route,
                scrollY = scrollY,
                onScrollY = onScrollY,
                loadingTitle = SettingsTextCopy.settingsTitle(),
                back = Runnable { renderHome() },
                load = { coordinator.settingsScreenModel() },
                render = { model ->
                    composeSettingsRoute(route, scrollY, onScrollY) { SettingsScreen(model) }
                },
            )
            MainActivityBase.NAV_SETTINGS_IMPORT_SYNC_ROUTE -> renderSettingsRouteAsync(
                route = route,
                scrollY = scrollY,
                onScrollY = onScrollY,
                loadingTitle = SettingsTextCopy.settingsAnkiSourceTitle(),
                back = Runnable { renderSettings(true) },
                load = { coordinator.settingsImportSyncScreenModel() },
                render = { model ->
                    composeSettingsRoute(route, scrollY, onScrollY) { SettingsSubmenuScreen(model) }
                },
            )
            MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE -> renderSettingsRouteAsync(
                route = route,
                scrollY = scrollY,
                onScrollY = onScrollY,
                loadingTitle = SettingsTextCopy.settingsStudyBehaviorTitle(),
                back = Runnable { renderSettings(true) },
                load = { coordinator.settingsStudyBehaviorScreenModel() },
                render = { model ->
                    composeSettingsRoute(route, scrollY, onScrollY) { SettingsSubmenuScreen(model) }
                },
            )
            MainActivityBase.NAV_SETTINGS_AUTOMATION_ROUTE -> renderSettingsRouteAsync(
                route = route,
                scrollY = scrollY,
                onScrollY = onScrollY,
                loadingTitle = SettingsTextCopy.settingsAutomationTitle(),
                back = Runnable { renderSettings(true) },
                load = { coordinator.settingsAutomationScreenModel() },
                render = { model ->
                    composeSettingsRoute(route, scrollY, onScrollY) { SettingsSubmenuScreen(model) }
                },
            )
            MainActivityBase.NAV_SETTINGS_APPEARANCE_ROUTE -> renderSettingsRouteAsync(
                route = route,
                scrollY = scrollY,
                onScrollY = onScrollY,
                loadingTitle = SettingsTextCopy.settingsAppearanceTitle(),
                back = Runnable { renderSettings(true) },
                load = { coordinator.settingsAppearanceScreenModel() },
                render = { model ->
                    composeSettingsRoute(route, scrollY, onScrollY) { SettingsSubmenuScreen(model) }
                },
            )
            MainActivityBase.NAV_SETTINGS_DISPLAY_DATA_ROUTE -> renderSettingsRouteAsync(
                route = route,
                scrollY = scrollY,
                onScrollY = onScrollY,
                loadingTitle = SettingsTextCopy.settingsReferenceDataTitle(),
                back = Runnable { renderSettings(true) },
                load = { coordinator.settingsDisplayDataScreenModel() },
                render = { model ->
                    composeSettingsRoute(route, scrollY, onScrollY) { SettingsSubmenuScreen(model) }
                },
            )
            MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE -> renderSettingsRouteAsync(
                route = route,
                scrollY = scrollY,
                onScrollY = onScrollY,
                loadingTitle = SettingsTextCopy.updatePageTitle(),
                back = Runnable { renderSettingsAutomation(true) },
                load = {
                    SettingsUpdatePageModel(
                        title = SettingsTextCopy.updatePageTitle(),
                        onHome = this@MainActivitySettings::renderHome,
                        onBack = { renderSettingsAutomation(true) },
                        onCheckForUpdate = { runUpdate(false) },
                        panel = settingsUpdatePanelModel(
                            activity = this@MainActivitySettings,
                            title = SettingsTextCopy.automaticUpdatesTitle(),
                        ),
                    )
                },
                render = { model ->
                    composeSettingsRoute(route, scrollY, onScrollY) { SettingsUpdatePage(model) }
                },
            )
            MainActivityBase.NAV_SETTINGS_LICENSES_ROUTE -> renderSettingsRouteAsync(
                route = route,
                scrollY = scrollY,
                onScrollY = onScrollY,
                loadingTitle = SettingsTextCopy.dataLicensesTitle(),
                back = Runnable { renderSettingsDisplayData(true) },
                load = { MainActivitySettingsReferenceData(this).referenceDataScreenModel() },
                render = { model ->
                    composeSettingsRoute(route, scrollY, onScrollY) { ReferenceDataScreen(model) }
                },
            )
            MainActivityBase.NAV_SETTINGS_HOW_IT_WORKS_ROUTE -> renderSettingsRouteAsync(
                route = route,
                scrollY = scrollY,
                onScrollY = onScrollY,
                loadingTitle = dev.bee.kanjianki.core.HowKaniWorksCopy.pageTitle(),
                back = Runnable { renderSettingsDisplayData(true) },
                load = { dev.bee.kanjianki.core.HowKaniWorksCopy.sections() },
                render = { sections ->
                    composeSettingsRoute(route, scrollY, onScrollY) { HowKaniWorksScreen(sections) }
                },
            )
            else -> {
                renderSettings()
            }
        }
    }

    /**
     * Renders a settings route without blocking the main thread. The screen model (which reads
     * many settings from the SQLite-backed store) is built on the background [io] executor and
     * rendered on the main thread when ready, so tapping the Settings button (or any settings
     * sub-card) responds well under the 1s latency budget instead of freezing while ~30+ store
     * reads run on the click path. A lightweight loading screen is shown only if the build runs
     * past ~120ms, so fast opens render directly with no loading flash. [back] is applied for the
     * loading state too, so system-back works before the model finishes building.
     */
    private fun <T> renderSettingsRouteAsync(
        route: String,
        scrollY: Int,
        onScrollY: (Int) -> Unit,
        loadingTitle: String,
        back: Runnable,
        load: () -> T,
        render: (T) -> Unit,
    ) {
        loadRouteAsync(
            showLoading = {
                composeSettingsRoute(route, scrollY, onScrollY) {
                    HomeRouteLoadingScreen(
                        title = loadingTitle,
                        homeLabel = HomeTextCopy.homeLabel(),
                        onHome = ::renderHome,
                    )
                }
                backAction = back
            },
            load = load,
            render = { model ->
                render(model)
                backAction = back
            },
            traceName = "settings-route",
        )
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
        composeRoute(route, initialScrollY, onScrollY = onScrollY) {
            content()
            BackupRestoreConfirmDialog(pendingBackupRestoreDialog)
        }
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
            postToMainIfActive(onComplete)
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
        // Empty or non-numeric input must not throw NumberFormatException; fall back to
        // 0 so the caller's own coercion (coerceAtLeast) picks the minimum.
        return input.text.toString().trim().toIntOrNull() ?: 0
    }

    fun reminderSettingsPanelModel(): SettingsReminderPanelModel {
        return MainActivitySettingsAutomationReminder(this).reminderSettingsPanelModel()
    }

    fun autoSyncSettingsPanelModel(): SettingsAutoSyncPanelModel {
        return MainActivitySettingsAutomationAutoSync(this).autoSyncSettingsPanelModel()
    }

    fun debugLogSettingsPanelModel(): SettingsDebugLogPanelModel {
        return MainActivitySettingsAutomationDebugLog(this).debugLogSettingsPanelModel()
    }

    fun backupSettingsPanelModel(): SettingsBackupPanelModel {
        return backupRestoreSettings.backupSettingsPanelModel()
    }

    internal fun closeForStagedRestore() {
        finishAffinity()
        exitProcess(0)
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
        // A manual release lookup/download may block until its bounded network
        // timeout expires. Do not queue it ahead of Home/Settings/Study route
        // loads on the user-facing executor.
        maintenance.execute {
            val updater = GitHubUpdater(this)
            val result = if (cachedPending) {
                updater.installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)
            } else {
                updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)
            }
            postToMainIfActive {
                if (activeUpdateUiRunToken != updateUiRun) {
                    return@postToMainIfActive
                }
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                result.intent?.let(::startActivity)
                renderUpdate(true)
            }
        }
    }
}
