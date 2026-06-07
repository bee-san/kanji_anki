package dev.bee.kanjianki

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.HomeDeckOverviewPolicy
import dev.bee.kanjianki.core.HomeImportOnboardingPolicy
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StatsPrecomputeStore
import dev.bee.kanjianki.data.StudyStatsStore
import dev.bee.kanjianki.sync.ManualSyncEngine
import dev.bee.kanjianki.sync.AutoSyncScheduler

internal abstract class MainActivityHome : MainActivityBase() {
    @JvmField
    var activeBrowseQuery: String = ""

    private val focusQueue = MainActivityHomeFocusQueue(this)
    private val browseDetail = MainActivityHomeBrowseDetail(this)
    private val asyncHomeRouteLoader by lazy {
        AsyncHomeRouteLoader(io) { task -> main.post(task) }
    }
    private val statsPrecomputeScheduler by lazy {
        StatsPrecomputeScheduler(
            background = io,
            isFresh = { StatsCacheStore(store).hasFreshSnapshot() },
            refresh = { generatedAt -> StatsPrecomputeStore(store).refresh(generatedAtMillis = generatedAt) },
        )
    }
    private var latestHomeRouteContent: (@Composable () -> Unit)? = null
    internal var pendingHomeSyncDialog: HomeSyncConfirmDialogModel? = null
    private var cachedImportOnboardingPlan: HomeImportOnboardingPolicy.Plan? = null

    abstract fun renderStats()
    abstract fun renderGames()

    override fun renderHome() {
        asyncHomeRouteLoader.cancelPending()
        clearStudyModeOverrides()
        cachedImportOnboardingPlan = null
        if (isScreenshotRouteRequested()) {
            renderScreenshotHome()
            return
        }
        renderAsyncHomeRoute(
            loadingTitle = HomeTextCopy.appTitle(),
            load = { buildHomeScreenModel() },
            render = { model ->
                renderHomeScreen(model)
                scheduleStatsPrecomputeIfStaleAsync()
            },
        )
    }

    private fun buildHomeScreenModel(): HomeScreenModel {
        val now = System.currentTimeMillis()
        val sync = store.latestSync()
        val streak = store.studyStreak(now)
        val rows = store.activeDashboardRows()
        val studyItems = if (rows.isEmpty()) emptyList() else store.studyItemsForKanji(rows.map { it.kanji })
        val deckOverviewRows = if (rows.isEmpty()) {
            emptyList()
        } else {
            HomeDeckOverviewPolicy.from(
                studyItems = studyItems,
                dashboardRows = rows,
                nowMillis = now,
                locallySuspendedKanji = store.locallySuspendedKanji(),
            ).rows()
        }
        val homeItems = studyItems
        val homePlan = if (rows.isEmpty()) null else adaptivePlan(rows, homeItems, now)
        val entries = if (rows.isEmpty()) {
            emptyList()
        } else {
            queuedEntries(rows, homeItems, now, homePlan)
        }
        val provider = gateway.status()
        val matureSupportThreshold = settings().matureSupportThreshold

        return HomeScreenModel(
            title = HomeTextCopy.appTitle(),
            subtitle = HomeTextCopy.appSubtitle(),
            metrics = homeMetricModels(this, sync, provider, streak, homePlan),
            deckOverviewRows = deckOverviewRows,
            showSyncCta = rows.isEmpty(),
            syncLabel = HomeTextCopy.syncAnkiDroidLabel(),
            studyLabel = MainActivityBase.LABEL_STUDY_NOW,
            onSync = this::confirmSync,
            onStudy = this::startFocusedStudy,
            actions = homeActionModels(this),
            focusTitle = HomeTextCopy.focusQueueTitle(),
            focusActionLabel = if (rows.isEmpty()) null else HomeTextCopy.viewAllLabel(),
            onFocusAction = if (rows.isEmpty()) null else this::renderFocusQueue,
            emptyTitle = when {
                rows.isEmpty() -> HomeTextCopy.noKanjiQueuedTitle()
                entries.isEmpty() -> MainActivityBase.EMPTY_ACTIVE_PRACTICE_TITLE
                else -> null
            },
            emptyBody = when {
                rows.isEmpty() -> HomeTextCopy.homeNoKanjiQueuedBody()
                entries.isEmpty() -> MainActivityBase.EMPTY_ACTIVE_PRACTICE_BODY
                else -> null
            },
            previewCards = entries.take(HOME_PREVIEW_ROW_LIMIT).map { entry ->
                homeFocusQueueCardModel(this, entry, now, matureSupportThreshold)
            }
        )
    }

    private fun renderHomeScreen(model: HomeScreenModel) {
        renderHomeRoute {
            HomeScreen(model)
        }
    }

    private fun isScreenshotRouteRequested(): Boolean {
        return intent?.getStringExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE).isNullOrBlank().not()
    }

    private fun renderScreenshotHome() {
        val provider = AnkiDroidGateway.ProviderStatus.create(
            installed = false,
            permissionGranted = false,
            canSync = false,
            authority = null,
            permission = null,
            message = "Screenshot route"
        )
        val model = HomeScreenModel(
            title = HomeTextCopy.appTitle(),
            subtitle = HomeTextCopy.appSubtitle(),
            metrics = homeMetricModels(this, null, provider, null, null),
            deckOverviewRows = emptyList(),
            showSyncCta = true,
            syncLabel = HomeTextCopy.syncAnkiDroidLabel(),
            studyLabel = MainActivityBase.LABEL_STUDY_NOW,
            onSync = this::confirmSync,
            onStudy = this::startFocusedStudy,
            actions = homeActionModels(this),
            focusTitle = HomeTextCopy.focusQueueTitle(),
            focusActionLabel = null,
            onFocusAction = null,
            emptyTitle = HomeTextCopy.noKanjiQueuedTitle(),
            emptyBody = HomeTextCopy.homeNoKanjiQueuedBody(),
            previewCards = emptyList(),
        )
        renderHomeScreen(model)
    }

    fun renderFocusQueue() {
        focusQueue.renderFocusQueue()
    }

    fun renderRecentMistakes() {
        focusQueue.renderRecentMistakes()
    }

    fun streakAccent(streak: StudyStatsStore.StudyStreak?): Int {
        return focusQueue.streakAccent(streak)
    }

    fun confirmSync() {
        val plan = currentImportOnboardingPlan()
        pendingHomeSyncDialog = HomeSyncConfirmDialogModels.create(
            message = plan.body(),
            confirmLabel = plan.primaryActionLabel(),
            onConfirm = Runnable {
                pendingHomeSyncDialog = null
                handleImportOnboardingAction(plan.state())
            },
            onDismiss = Runnable {
                pendingHomeSyncDialog = null
                rerenderLatestHomeRoute()
            },
        )
        rerenderLatestHomeRoute()
    }

    protected open fun importOnboardingPlan(): HomeImportOnboardingPolicy.Plan {
        val current = settings()
        val provider = gateway.status()
        return HomeImportOnboardingPolicy.plan(
            provider.installed,
            provider.permissionGranted,
            provider.canSync,
            onboardingLastSync(),
            provider.permission,
            current,
        )
    }

    private fun currentImportOnboardingPlan(): HomeImportOnboardingPolicy.Plan {
        return cachedImportOnboardingPlan ?: importOnboardingPlan().also { cachedImportOnboardingPlan = it }
    }

    private fun onboardingLastSync(): HomeImportOnboardingPolicy.LastSync? {
        val sync = store.latestSync() ?: return null
        return HomeImportOnboardingPolicy.LastSync(sync.status, sync.importedKanji, sync.errorMessage)
    }

    private fun handleImportOnboardingAction(state: HomeImportOnboardingPolicy.State) {
        when (state) {
            HomeImportOnboardingPolicy.State.INSTALL_ANKIDROID -> openAnkiDroidInstallPage()
            HomeImportOnboardingPolicy.State.GRANT_PERMISSION,
            HomeImportOnboardingPolicy.State.RECOVER_PERMISSION -> requestAnkiPermissionIfNeeded()
            HomeImportOnboardingPolicy.State.CHOOSE_SOURCE -> renderSettings()
            HomeImportOnboardingPolicy.State.READY_FIRST_SYNC,
            HomeImportOnboardingPolicy.State.RECOVER_SYNC,
            HomeImportOnboardingPolicy.State.SYNCED -> runSync()
        }
    }

    private fun openAnkiDroidInstallPage() {
        startActivity(Intent(Intent.ACTION_VIEW, ANKIDROID_INSTALL_URL.toUri()))
    }

    internal fun rememberHomeRouteContent(content: @Composable () -> Unit) {
        latestHomeRouteContent = content
    }

    private fun rerenderLatestHomeRoute() {
        latestHomeRouteContent?.let { content ->
            renderHomeRoute(content)
        }
    }

    fun runSync() {
        val progressView = SyncProgressPanel()
        renderHomeRoute {
            SyncProgressScreen(
                title = HomeTextCopy.syncingTitle(),
                progressPanel = progressView,
            )
        }
        val syncGateway = MainActivityRuntimeOverrides.collectionGateway ?: gateway
        val coordinator = ManualSyncCoordinator(
            io,
            main::post,
            { progress ->
                ManualSyncEngine(
                    this,
                    store,
                    syncGateway,
                    settings(),
                    progress,
                ).run()
            },
            {
                store.activateAutoSyncAfterFirstSuccess()
                AutoSyncScheduler.schedule(this)
            },
            this::renderSyncResult,
        )
        coordinator.start { update -> main.post { progressView.render(update) } }
    }

    fun renderSyncResult(result: ManualSyncEngine.SyncResult) {
        if (result.skipped) {
            renderSkippedSyncResult(result)
        } else if (result.success) {
            renderSuccessfulSyncResult(result)
        } else {
            renderFailedSyncResult(result)
        }
    }

    fun renderSkippedSyncResult(result: ManualSyncEngine.SyncResult) {
        renderSyncResultScreen(
            SyncResultScreenModel(
                HomeTextCopy.syncAlreadyRunningTitle(),
                null,
                listOf(nonEmptyOr(result.message, HomeTextCopy.syncAlreadyRunningFallback())),
                BLUE,
                null,
                TEAL,
                null,
                LABEL_BACK_HOME,
                this::renderHome,
            )
        )
    }

    fun renderSuccessfulSyncResult(result: ManualSyncEngine.SyncResult) {
        val summaryLines = mutableListOf<String>()
        summaryLines.add(HomeTextCopy.syncCandidateSummary(result.dashboardRows, result.adaptiveFocusText))
        if (result.adaptiveSummary.isNotEmpty()) {
            summaryLines.add(result.adaptiveSummary)
        }
        if (result.importedSuspendedKanji > 0) {
            summaryLines.add(HomeTextCopy.importedSuspendedKanjiText(result.importedSuspendedKanji))
        }
        if (!result.message.isNullOrEmpty()) {
            summaryLines.add(result.message)
        }
        renderSyncResultScreen(
            SyncResultScreenModel(
                HomeTextCopy.syncCompleteTitle(),
                HomeTextCopy.syncReadyCountText(result.studyReadyCount),
                summaryLines,
                TEAL,
                if (result.studyReadyCount > 0) LABEL_STUDY_NOW else null,
                CORAL,
                if (result.studyReadyCount > 0) ::startFocusedStudy else null,
                LABEL_BACK_HOME,
                this::renderHome,
            )
        )
        scheduleStatsPrecomputeIfStale()
    }

    fun renderFailedSyncResult(result: ManualSyncEngine.SyncResult) {
        renderSyncResultScreen(
            SyncResultScreenModel(
                HomeTextCopy.syncNeedsAttentionTitle(),
                HomeTextCopy.syncReadErrorTitle(),
                listOf(nonEmptyOr(result.message, HomeTextCopy.syncFailureFallback())),
                CORAL,
                HomeTextCopy.trySyncAgainLabel(),
                TEAL,
                this::confirmSync,
                LABEL_BACK_HOME,
                this::renderHome,
            )
        )
    }

    private fun renderSyncResultScreen(model: SyncResultScreenModel) {
        renderHomeRoute {
            SyncResultScreen(model)
        }
    }

    fun studyAheadMillis(): Long {
        return focusQueue.studyAheadMillis()
    }

    fun nonEmptyOr(value: String?, fallback: String): String {
        return if (value.isNullOrEmpty()) fallback else value
    }

    fun studyQueue(
        rows: List<RecordsImportModels.DashboardRow>,
        now: Long,
        persist: Boolean,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        currentItems: List<RecordsStudyModels.StudyItem>? = null,
    ): List<RecordsStudyModels.StudyItem> {
        return focusQueue.studyQueue(rows, now, persist, plan, currentItems)
    }

    fun queuedEntries(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    ): List<QueueEntry> {
        return focusQueue.queuedEntries(rows, items, now, plan)
    }

    fun queuedEntries(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): List<QueueEntry> {
        return focusQueue.queuedEntries(rows, items, now, null)
    }

    fun rowColor(item: RecordsStudyModels.StudyItem, now: Long): Int {
        return focusQueue.rowColor(item, now)
    }

    private companion object {
        const val HOME_PREVIEW_ROW_LIMIT = 3
        const val ANKIDROID_INSTALL_URL = "https://ankidroid.org/#download"
    }

    fun renderBrowseKanji(query: String?) {
        browseDetail.renderBrowseKanji(query)
    }

    fun <T> renderAsyncHomeRoute(
        loadingTitle: String,
        load: () -> T,
        render: (T) -> Unit,
        traceName: String = "home-route",
    ) {
        asyncHomeRouteLoader.load(
            showLoading = {
                renderHomeRoute {
                    HomeRouteLoadingScreen(
                        title = loadingTitle,
                        homeLabel = HomeTextCopy.homeLabel(),
                        onHome = this::renderHome,
                    )
                }
            },
            load = load,
            render = render,
            traceLabel = traceName,
            showLoadingAfterMs = 120,
        )
    }

    fun cancelPendingHomeRouteLoads() {
        asyncHomeRouteLoader.cancelPending()
    }

    fun scheduleStatsPrecomputeIfStale(): Boolean {
        return statsPrecomputeScheduler.scheduleIfStale()
    }

    fun scheduleStatsPrecomputeIfStaleAsync() {
        io.execute {
            scheduleStatsPrecomputeIfStale()
        }
    }

    fun renderDetail(kanji: String, fromBrowse: Boolean, browseQuery: String?) {
        browseDetail.renderDetail(kanji, fromBrowse, browseQuery)
    }
}
