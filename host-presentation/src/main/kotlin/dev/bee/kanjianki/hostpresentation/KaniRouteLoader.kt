package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.GamesRender
import dev.bee.kanjianki.StudyRouteRender
import dev.bee.kanjianki.application.HomeUseCases
import dev.bee.kanjianki.application.SettingsUseCases
import dev.bee.kanjianki.application.StatsUseCases
import dev.bee.kanjianki.core.FocusQueuePolicy
import dev.bee.kanjianki.core.ReminderEligibilityPolicy
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.data.StudyQueueSnapshot
import dev.bee.kanjianki.platform.DeviceSettingKeys
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.presentation.BrowseResults
import dev.bee.kanjianki.presentation.HomeDashboard
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KanjiDetail
import dev.bee.kanjianki.presentation.KeyboardPlatform
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.ProviderReadiness
import dev.bee.kanjianki.presentation.SettingsScreen
import dev.bee.kanjianki.presentation.StatsDashboard
import dev.bee.kanjianki.presentation.StudyKeybindings
import dev.bee.kanjianki.presentation.StudyKeybindingsCodec
import dev.bee.kanjianki.progress.progressAnalyticsSnapshot

private const val MINUTE_MILLIS = 60_000L

/**
 * The provider facts a route load needs, as the portable projection both hosts share.
 *
 * Desktop derives it from an AnkiConnect handshake and Android from the AnkiDroid
 * gateway status, but the loader only needs three things from either: whether sync is
 * offerable ([isReady]), the onboarding [readiness]/[message], and the capability set.
 * Passing this rather than a host probe type is what lets one loader serve both.
 */
data class HostProviderStatus(
    val readiness: ProviderReadiness,
    val message: String,
    val isReady: Boolean,
    val capabilities: Set<PlatformCapability>,
)

/**
 * The Automation facts no store holds, which the host has to answer for itself.
 *
 * The device-settings store covers everything the user chose; these three are observed
 * rather than chosen. [notificationsBlocked] is what the OS says right now, and
 * [automaticBackupCount]/[lastAutomaticBackupAtMillis] come from counting files in the
 * host's own backup directory — neither is something Settings may write, and both are
 * stale the moment they are read, which is why they arrive per route load.
 *
 * Every field defaults to the honest "nothing observed" value, so a host that has not
 * wired its notifier or its backup directory yet reports no backups and unblocked
 * notifications rather than inventing either.
 */
data class HostAutomationRuntime(
    val notificationsBlocked: Boolean = false,
    val lastAutomaticBackupAtMillis: Long? = null,
    val automaticBackupCount: Int = 0,
)

/**
 * The Update facts no store holds, which the host has to answer for itself.
 *
 * [installedVersion] is the running build's own version — `BuildConfig.VERSION_NAME` on
 * Android, the packaged version on desktop — and is deliberately not stored: a persisted
 * copy would survive the upgrade that made it wrong and the panel would report the version
 * the user just replaced.
 *
 * [canInstall] is re-asked every load rather than cached, because it can change without
 * Kani running: on Android the user can revoke `REQUEST_INSTALL_PACKAGES` from system
 * settings, and on desktop the answer depends on the installation channel
 * `DesktopInstallationChannelPolicy` resolves from the launcher path each launch. A stale
 * `true` here is an install button that bounces off a permission the user cannot see.
 *
 * Both default to the honest "nothing observed" value, so a host that has not wired its
 * updater reports no version and no install ability rather than inventing either.
 */
data class HostUpdateRuntime(
    val installedVersion: String = "",
    val canInstall: Boolean = false,
)

/**
 * Builds one [KaniRouteContent] for a destination from the shared use-case graph.
 *
 * This is the orchestration that was `loadDesktopRoute`, lifted out of the desktop
 * scaffold so the Android host runs the identical assembly instead of a second one —
 * the whole point of Goal 199. It is pure of Compose, dispatchers, and clocks: the
 * caller supplies [nowMillis], the provider [status], and the current Study/Games
 * renders (owned by the host's runtimes), and gets back the portable content the shared
 * shell renders. One snapshot for every route; Browse and Detail do the one extra read
 * their destination parameterizes.
 */
class KaniRouteLoader(
    private val homeUseCases: HomeUseCases,
    private val statsUseCases: StatsUseCases,
    private val settingsUseCases: SettingsUseCases,
    private val deviceSettings: () -> DeviceSettingsReader,
    private val annotateCapabilities: (List<dev.bee.kanjianki.core.RecordsStudyModels.StudyItem>) -> List<dev.bee.kanjianki.core.RecordsStudyModels.StudyItem>,
    /**
     * The host's keyboard conventions, for labelling bindings and for the reserved-chord
     * list. Defaults to [KeyboardPlatform.LINUX], which is also the right answer for
     * Android: Ctrl-primary notation, and no OS chord set to avoid.
     */
    private val keyboardPlatform: KeyboardPlatform = KeyboardPlatform.LINUX,
) {
    suspend fun load(
        destination: KaniDestination,
        status: HostProviderStatus,
        nowMillis: Long,
        studyRender: StudyRouteRender?,
        gamesRender: GamesRender?,
        /**
         * Whether a sync is running right now, from the host's [HostSyncDriver].
         *
         * A parameter rather than something read out of the snapshot, because a sync in
         * flight is host state and not persisted data: the engine has not committed
         * anything yet, so no repository can answer this. Defaulted to false so a caller
         * with no driver — a test, or a host before its engine is wired — reads the
         * honest value rather than being forced to invent one.
         */
        syncing: Boolean = false,
        /**
         * The Automation facts the host observes rather than stores; see
         * [HostAutomationRuntime]. Defaulted so every caller that never opens Automation
         * — which is every caller on every other route — passes nothing.
         */
        automationRuntime: HostAutomationRuntime = HostAutomationRuntime(),
        /**
         * The Update facts the host observes rather than stores; see [HostUpdateRuntime].
         * Defaulted for the same reason [automationRuntime] is.
         */
        updateRuntime: HostUpdateRuntime = HostUpdateRuntime(),
    ): KaniRouteContent {
        val snapshot = homeUseCases.loadRoute(nowMillis)
        val study = snapshot.study
        val settings = snapshot.settings.sync
        val streak = streakOf(study)
        val due = ReminderEligibilityPolicy
            .eligibleDueTimes(study.studyItems, study.activeRows, study.studyLadder)
            .count { dueAt -> dueAt <= nowMillis }

        val dailyPlan = DesktopHomeModels.dailyPlan(
            study = study,
            streak = streak,
            latestSuccessfulSyncAtMillis = study.latestSuccessfulSyncAtMillis,
            consecutiveFailedSyncs = snapshot.home.consecutiveFailedSyncs,
            deviceSettings = deviceSettings(),
            nowMillis = nowMillis,
        )
        val adaptivePlan = DesktopHomeModels.adaptivePlan(study, settings, streak, nowMillis)
        val entries = if (study.activeRows.isEmpty()) {
            emptyList()
        } else {
            FocusQueuePolicy.queuedEntries(
                study.activeRows,
                study.studyItems,
                nowMillis,
                study.studyAheadMinutes * MINUTE_MILLIS,
                adaptivePlan,
                study.studyLadder,
            )
        }
        val repairedKanjiCount = snapshot.repairedWriteBackProposal?.repairedKanji?.size ?: 0

        val home = HomeDashboard(
            readiness = status.readiness,
            metrics = DesktopHomeModels.metrics(
                sync = snapshot.home.latestSync,
                streak = streak,
                canSync = status.isReady,
                dailyPlan = dailyPlan,
                plan = adaptivePlan,
            ),
            todayPlan = DesktopHomeModels.todayPlan(dailyPlan),
            deckOverview = DesktopHomeModels.deckOverview(study, nowMillis, study.locallySuspendedKanji),
            focus = DesktopHomeModels.focusQueue(
                rows = study.activeRows,
                entries = entries,
                plan = adaptivePlan,
                nowMillis = nowMillis,
                matureSupportThreshold = settings.matureSupportThreshold,
            ),
            repairedKanjiCount = repairedKanjiCount,
            studyRemainingCount = DesktopHomeModels.studyRemainingCount(
                study = study,
                settings = settings,
                plan = adaptivePlan,
                dueLegacyWritingRepairs = snapshot.home.dueLegacyWritingRepairs,
                annotate = annotateCapabilities,
                nowMillis = nowMillis,
            ),
            syncing = syncing,
        )

        return KaniRouteContent(
            providerMessage = status.message,
            studyItemCount = study.studyItems.size,
            dueCount = due,
            themeChoice = snapshot.settings.themeChoice,
            home = home,
            onboarding = DesktopHomeModels.onboarding(
                readiness = status.readiness,
                guidance = status.message,
                settings = settings,
                latestSync = snapshot.home.latestSync,
                repairedKanjiCount = repairedKanjiCount,
            ),
            browse = loadBrowse(destination),
            detail = loadDetail(destination, settings.matureSupportThreshold, nowMillis),
            study = studyRender?.let {
                DesktopStudyModel.session(it.session, it.routeSnapshot, it.undoable, it.choicePrompt)
            },
            stats = loadStats(destination, nowMillis),
            games = gamesRender?.let(DesktopGamesModel::screen),
            settings = (destination as? KaniDestination.Settings)?.let {
                DesktopSettingsModel.screen(
                    section = it.section,
                    snapshot = snapshot.settings,
                    bindings = studyKeybindings(),
                    platform = keyboardPlatform,
                    automation = automationState(automationRuntime),
                    update = updateState(updateRuntime),
                    capabilities = PlatformCapabilities(status.capabilities),
                )
            },
            studyKeybindings = studyKeybindings(),
        )
    }

    /**
     * The user's Study keybindings, or the reviewed defaults.
     *
     * Read on every route load rather than cached, because the Settings editor writes
     * them through the device-settings store and the very next load has to show — and
     * study with — what was just saved. Malformed stored state falls open to the whole
     * default set; see `StudyKeybindingsCodec.decode`.
     */
    private fun studyKeybindings(): StudyKeybindings =
        StudyKeybindingsCodec.decode(deviceSettings().read(DeviceSettingKeys.studyKeybindings))

    /**
     * The stored automation settings, plus the facts only the host can observe.
     *
     * Read on every load for the same reason keybindings are: the Automation editor writes
     * straight to the device store, so the next load is what has to show the new value.
     */
    private fun automationState(
        runtime: HostAutomationRuntime,
    ): DesktopSettingsModel.AutomationState =
        AutomationSettingsStore.read(deviceSettings()).copy(
            notificationsBlocked = runtime.notificationsBlocked,
            lastAutomaticBackupAtMillis = runtime.lastAutomaticBackupAtMillis,
            automaticBackupCount = runtime.automaticBackupCount,
        )

    /**
     * The stored update record, plus the two facts only the host can answer.
     *
     * Read on every load, which matters more here than anywhere else in this class: the
     * update notification deep-links straight to this section, so the load that renders it
     * is usually the first one after the background checker wrote its result.
     */
    private fun updateState(
        runtime: HostUpdateRuntime,
    ): DesktopSettingsModel.UpdateState =
        UpdateSettingsStore.read(deviceSettings()).copy(
            installedVersion = runtime.installedVersion,
            canInstall = runtime.canInstall,
        )

    private suspend fun loadBrowse(destination: KaniDestination): BrowseResults {
        val browse = destination as? KaniDestination.Browse ?: return BrowseResults()
        val items = homeUseCases.searchStudyInventory(
            query = browse.query,
            onlySimilarKanji = browse.onlySimilarKanji,
            includeLocallySuspended = browse.showSuspended,
        )
        return DesktopHomeModels.browse(
            items = items,
            query = browse.query,
            onlySimilarKanji = browse.onlySimilarKanji,
            allKanjiScope = browse.allKanjiScope,
            showSuspended = browse.showSuspended,
        )
    }

    private suspend fun loadDetail(
        destination: KaniDestination,
        matureSupportThreshold: Int,
        nowMillis: Long,
    ): KanjiDetail? {
        val detail = destination as? KaniDestination.Detail ?: return null
        val snapshot = homeUseCases.loadKanjiDetail(detail.kanji, nowMillis)
        return DesktopDetailModel.detail(detail.kanji, snapshot, matureSupportThreshold, nowMillis)
    }

    private suspend fun loadStats(destination: KaniDestination, nowMillis: Long): StatsDashboard? {
        if (destination != KaniDestination.Stats) return null
        val snapshot = statsUseCases.loadForDisplay(nowMillis)
        val ladder = settingsUseCases.load().studyLadder
        return DesktopStatsModel.dashboard(progressAnalyticsSnapshot(snapshot, nowMillis, ladder))
    }

    private fun streakOf(study: StudyQueueSnapshot): StudyStreakPolicy.Streak {
        val streak = study.studyStreak
        return StudyStreakPolicy.Streak(
            currentDays = streak.currentDays,
            bestDays = streak.bestDays,
            studiedToday = streak.studiedToday,
            reviewsToday = streak.reviewsToday,
            lastStudyAtMillis = streak.lastStudyAtMillis,
        )
    }
}
