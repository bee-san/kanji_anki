package dev.bee.kanjianki

import android.content.Intent
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.HomeDeckOverviewPolicy
import dev.bee.kanjianki.core.HomeImportOnboardingPolicy
import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RepairedHandoffPolicy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.StudyNowCountPolicy
import dev.bee.kanjianki.core.StudySessionProgressTracker
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StatsPrecomputeStore
import dev.bee.kanjianki.data.StudyStatsStore
import dev.bee.kanjianki.data.dismissRepairedHandoff
import dev.bee.kanjianki.data.pendingRepairedHandoffKanji
import dev.bee.kanjianki.data.repairedWriteBackPreview
import dev.bee.kanjianki.sync.ManualSyncEngine
import dev.bee.kanjianki.sync.AutoSyncScheduler
import dev.bee.kanjianki.sync.SyncSettings
import java.util.Locale
import java.util.concurrent.RejectedExecutionException

internal abstract class MainActivityHome : MainActivityBase() {
    // Written on the main thread and read from a background route-load lambda, so it is
    // volatile to publish the latest query across threads.
    @JvmField
    @Volatile
    var activeBrowseQuery: String = ""

    @JvmField
    var activeBrowseSimilarOnly: Boolean = false

    @JvmField
    var activeBrowseAllKanji: Boolean = false

    private val focusQueue by lazy { MainActivityHomeFocusQueue(this) }
    private val browseDetail by lazy { MainActivityHomeBrowseDetail(this) }
    private val homeStudyPlanProvider by lazy { MainActivityStudyPlanProvider(this) }
    private val asyncHomeRouteLoader by lazy {
        AsyncHomeRouteLoader(
            background = io,
            postToMain = { task -> main.post(task) },
            onRouteRequested = ::onAsyncRouteRequested,
            onRouteCanceled = ::onAsyncRouteCanceled,
            onRouteSettled = ::onAsyncRouteSettled,
        )
    }
    private val statsPrecomputeScheduler by lazy {
        StatsPrecomputeScheduler(
            background = maintenance,
            // The maintenance executor can still be unwinding when Activity teardown closes
            // the route store. Give stats work its own helper so an interrupted refresh never
            // races that close and crashes the process with an already-closed database.
            isFresh = {
                LocalStore(applicationContext).use { statsStore ->
                    StatsCacheStore(statsStore).hasFreshSnapshot()
                }
            },
            refresh = { generatedAt ->
                LocalStore(applicationContext).use { statsStore ->
                    StatsPrecomputeStore(statsStore).refresh(generatedAtMillis = generatedAt)
                }
            },
            onError = ::reportStatsPrecomputeError,
        )
    }
    private var latestHomeRouteContent: (@Composable () -> Unit)? = null
    private var latestHomeRouteBackAction: Runnable? = null
    internal var pendingHomeSyncDialog: HomeSyncConfirmDialogModel? = null
    internal var pendingUpdatePermissionDialog: HomeUpdatePermissionDialogModel? = null
    private var confirmedRepairedNoteIds: Set<Long> = emptySet()

    abstract fun renderGames()

    override fun renderHome() {
        asyncHomeRouteLoader.cancelPending()
        activeUpdateUiRunToken = 0
        clearStudyModeOverrides()
        if (isScreenshotRouteRequested()) {
            renderScreenshotHome()
            return
        }
        renderAsyncHomeRoute(
            loadingTitle = HomeTextCopy.appTitle(),
            load = {
                val downgradeVersion = store.consumeDowngradeNotice()
                buildHomeScreenModel() to downgradeVersion
            },
            render = { (model, downgradeVersion) ->
                renderHomeScreen(
                    model,
                    initialScrollY = screenshotScrollY(),
                    scrollPositionLabel = screenshotScrollPositionLabel(),
                )
                scheduleStatsPrecomputeIfStaleAsync()
                maybeShowUpdatePermissionPrompt(model.updatePermissionPrompt)
                if (downgradeVersion != null) {
                    showDowngradeToast()
                }
            },
        )
    }

    private fun triggerManualUpdateCheck() {
        io.execute {
            val updater = dev.bee.kanjianki.update.GitHubUpdater(this)
            updater.checkDownloadAndInstall(dev.bee.kanjianki.update.GitHubUpdater.UpdateSource.MANUAL)
            postToMainIfActive(::renderHome)
        }
    }

    private fun showDowngradeToast() {
        android.widget.Toast.makeText(
            this,
            HomeTextCopy.databaseDowngradeNotice(),
            android.widget.Toast.LENGTH_LONG,
        ).show()
    }

    private fun buildHomeScreenModel(): HomeScreenModel = homeLoadPhase(
        phase = "total",
        details = { model -> "preview_cards=${model.previewCards.size}" },
    ) {
        val now = System.currentTimeMillis()
        val sync = homeLoadPhase("latest-sync") { store.latestSync() }
        val latestSuccessfulSyncAt = homeLoadPhase("latest-successful-sync") {
            store.latestSuccessfulSyncFinishedAt()
        }
        val streak = homeLoadPhase("study-streak") { store.studyStreak(now) }
        val settingsSnapshot = homeLoadPhase("settings") { settings() }
        val rows = homeLoadPhase(
            phase = "dashboard",
            details = { loaded -> "rows=${loaded.size}" },
        ) {
            store.activeDashboardRows()
        }
        val studyItems = homeLoadPhase(
            phase = "study-items",
            details = { loaded -> "rows=${loaded.size}" },
        ) {
            if (rows.isEmpty()) emptyList() else store.studyItemsForKanji(rows.map { it.kanji })
        }
        val deckOverviewRows = homeLoadPhase(
            phase = "deck-overview",
            details = { loaded -> "rows=${loaded.size}" },
        ) {
            if (rows.isEmpty()) {
                emptyList()
            } else {
                HomeDeckOverviewPolicy.from(
                    studyItems = studyItems,
                    dashboardRows = rows,
                    nowMillis = now,
                    locallySuspendedKanji = store.locallySuspendedKanji(),
                ).rows()
            }
        }
        val homeItems = studyItems
        val dailyPlan = homeLoadPhase("daily-plan") {
            homeStudyPlanProvider.dailyStudyPlan(rows, homeItems, now, streak, latestSuccessfulSyncAt)
        }
        val homePlan = homeLoadPhase("adaptive-plan") {
            if (rows.isEmpty()) {
                null
            } else {
                homeStudyPlanProvider.adaptivePlan(rows, homeItems, now, streak.currentDays, settingsSnapshot)
            }
        }
        val studyNowCount = homeLoadPhase("study-now-count") {
            val ladder = studyLadderSettings()
            val studyItemCount = if (homePlan == null || rows.isEmpty()) {
                0
            } else {
                StudyNowCountCoordinator.count(
                    StudyNowCountCoordinator.Request(
                        queue = StudyNowCountCoordinator.QueueInput(rows, homeItems, settingsSnapshot, ladder),
                        timing = StudyNowCountCoordinator.Timing(now, startOfDay(now), studyAheadMillis()),
                        mode = StudyNowCountCoordinator.Mode(homePlan, false),
                        pipeline = StudyNowCountCoordinator.Pipeline(
                            scheduler = BridgeScheduler.withWeights(store.schedulerFsrsWeights()),
                            annotator = store::annotateSimilarKanjiAvailability,
                            replanner = { seeded ->
                                homeStudyPlanProvider.adaptivePlan(
                                    rows,
                                    seeded,
                                    now,
                                    streak.currentDays,
                                    settingsSnapshot,
                                )
                            },
                        ),
                    ),
                ).studyItemCount
            }
            val repairTaskKeys = if (ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI)) {
                store.dueSimilarWritingRepairs(now).map(StudySessionProgressTracker::similarRepairProgressKey)
            } else {
                emptyList()
            }
            StudyNowCountPolicy.includingAdditionalTaskKeys(studyItemCount, repairTaskKeys)
        }
        val entries = homeLoadPhase(
            phase = "queue-entries",
            details = { loaded -> "rows=${loaded.size}" },
        ) {
            if (rows.isEmpty()) emptyList() else queuedEntries(rows, homeItems, now, homePlan)
        }
        val provider = homeLoadPhase("provider-status") { gateway.status() }
        val matureSupportThreshold = settingsSnapshot.matureSupportThreshold
        val repairedHandoff = homeLoadPhase("repaired-handoff") {
            RepairedHandoffPolicy.card(store.pendingRepairedHandoffKanji())?.let { card ->
                HomeRepairedHandoffCardModel(
                    card = card,
                    onCopySearch = { copyRepairedAnkiSearch(card.search) },
                    onDismiss = ::dismissRepairedHandoffCard,
                )
            }
        }
        val updatePermissionPrompt = homeLoadPhase("update-permission-prompt") {
            loadUpdatePermissionPromptSnapshot()
        }
        val updateCheckFailedLine = homeLoadPhase("update-check-failed") {
            val failedAt = store.updateCheckFailedAt()
            if (failedAt > 0L && (now - failedAt) < UPDATE_CHECK_FAILURE_EXPIRY_MS) {
                HomeTextCopy.updateCheckFailedLine()
            } else {
                null
            }
        }

        homeLoadPhase("model-assembly") {
            studySessionBadgeCount = studyNowCount
            HomeScreenModel(
                title = HomeTextCopy.appTitle(),
                subtitle = HomeTextCopy.appSubtitle(),
                metrics = homeMetricModels(this, sync, provider, streak, homePlan, dailyPlan),
                todayPlan = homeTodayPlanModel(dailyPlan, this::startFocusedStudy, this::confirmSync),
                deckOverviewRows = deckOverviewRows,
                showSyncCta = rows.isEmpty(),
                syncLabel = HomeTextCopy.syncAnkiDroidLabel(),
                studyLabel = HomeTextCopy.studyNowLabel(),
                onSync = this::confirmSync,
                onStudy = this::startFocusedStudy,
                actions = homeActionModels(this),
                focusTitle = HomeTextCopy.focusQueueTitle(),
                focusActionLabel = if (rows.isEmpty()) null else HomeTextCopy.viewAllLabel(),
                onFocusAction = if (rows.isEmpty()) null else this::renderFocusQueue,
                emptyTitle = when {
                    rows.isEmpty() -> HomeTextCopy.noKanjiQueuedTitle()
                    entries.isEmpty() -> HomeTextCopy.activePracticeEmptyTitle()
                    else -> null
                },
                emptyBody = when {
                    rows.isEmpty() -> HomeTextCopy.homeNoKanjiQueuedBody()
                    entries.isEmpty() -> HomeTextCopy.activePracticeEmptyBody()
                    else -> null
                },
                previewCards = entries.take(HOME_PREVIEW_ROW_LIMIT).map { entry ->
                    homeFocusQueueCardModel(this, entry, now, matureSupportThreshold)
                },
                studyRemainingCount = studyNowCount,
                repairedHandoff = repairedHandoff,
                updatePermissionPrompt = updatePermissionPrompt,
                updateCheckFailedLine = updateCheckFailedLine,
                onRetryUpdateCheck = if (updateCheckFailedLine != null) this::triggerManualUpdateCheck else null,
            )
        }
    }

    private fun copyRepairedAnkiSearch(search: String) {
        copyRepairedAnkiSearch(this, search)
    }

    private fun dismissRepairedHandoffCard() {
        // Coroutine-based async pattern (Goal 135): same io thread as the executor
        // version, but lifecycle-aware — cancelled automatically on destroy instead
        // of relying on postToMainIfActive's isFinishing guard.
        lifecycleScope.launch {
            withContext(dispatchers.io) {
                store.dismissRepairedHandoff()
            }
            renderHome()
        }
    }

    private fun renderHomeScreen(
        model: HomeScreenModel,
        initialScrollY: Int = 0,
        scrollPositionLabel: String? = null,
    ) {
        renderHomeRoute(initialScrollY = initialScrollY, scrollPositionLabel = scrollPositionLabel) {
            HomeScreen(model)
        }
    }

    private fun isScreenshotRouteRequested(): Boolean {
        return isScreenshotLaunchRequested()
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
            todayPlan = homeTodayPlanModel(DailyStudyPlanPolicy.plan(null), this::startFocusedStudy, this::confirmSync),
            deckOverviewRows = emptyList(),
            showSyncCta = true,
            syncLabel = HomeTextCopy.syncAnkiDroidLabel(),
            studyLabel = HomeTextCopy.studyNowLabel(),
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
        renderHomeScreen(
            model,
            initialScrollY = screenshotScrollY(),
            scrollPositionLabel = screenshotScrollPositionLabel(),
        )
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
        confirmedRepairedNoteIds = emptySet()
        val consent = currentSyncConsent()
        val plan = consent.plan
        pendingHomeSyncDialog = HomeSyncConfirmDialogModels.create(
            message = plan.body(),
            confirmLabel = plan.primaryActionLabel(),
            onConfirm = Runnable {
                pendingHomeSyncDialog = null
                confirmedRepairedNoteIds = consent.repairedNoteIds
                handleImportOnboardingAction(plan.state())
            },
            onDismiss = Runnable {
                pendingHomeSyncDialog = null
                confirmedRepairedNoteIds = emptySet()
                rerenderLatestHomeRoute()
            },
        )
        rerenderLatestHomeRoute()
    }

    protected open fun importOnboardingPlan(): HomeImportOnboardingPolicy.Plan {
        return currentSyncConsent().plan
    }

    private fun currentSyncConsent(): SyncConsent {
        val current = settings()
        val provider = gateway.status()
        val tagRepaired = SyncSettings.tagRepairedCards(store)
        val repairedProposal = if (tagRepaired) {
            store.repairedWriteBackPreview(current.matureSupportThreshold)
        } else {
            null
        }
        return SyncConsent(
            plan = HomeImportOnboardingPolicy.plan(
                provider.installed,
                provider.permissionGranted,
                provider.canSync,
                onboardingLastSync(),
                provider.permission,
                current,
                tagRepaired,
                repairedProposal?.repairedKanji?.size ?: 0,
            ),
            repairedNoteIds = repairedProposal?.noteIdsToTag.orEmpty(),
        )
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

    internal fun rememberHomeRouteContent(backAction: Runnable?, content: @Composable () -> Unit) {
        latestHomeRouteContent = content
        latestHomeRouteBackAction = backAction
    }

    internal fun rerenderLatestHomeRoute() {
        latestHomeRouteContent?.let { content ->
            renderHomeRoute(latestHomeRouteBackAction) {
                content()
            }
        }
    }

    fun runSync() {
        val repairedNoteIds = confirmedRepairedNoteIds
        confirmedRepairedNoteIds = emptySet()
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
            { task -> postToMainIfActive { task.run() } },
            { progress ->
                ManualSyncEngine(
                    this,
                    store,
                    syncGateway,
                    settings(),
                    progress,
                    dev.bee.kanjianki.time.AppClock.systemClock(),
                    repairedWriteBackAuthorized = true,
                    confirmedRepairedNoteIds = repairedNoteIds,
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

    private data class SyncConsent(
        val plan: HomeImportOnboardingPolicy.Plan,
        val repairedNoteIds: Set<Long>,
    )

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
                StudyTextCopy.backHomeLabel(),
                this::renderHome,
            )
        )
    }

    fun renderSuccessfulSyncResult(result: ManualSyncEngine.SyncResult) {
        studySessionBadgeCount = result.studyReadyCount.coerceAtLeast(0)
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
                if (result.studyReadyCount > 0) HomeTextCopy.studyNowLabel() else null,
                CORAL,
                if (result.studyReadyCount > 0) ::startFocusedStudy else null,
                StudyTextCopy.backHomeLabel(),
                this::renderHome,
            )
        )
        scheduleStatsPrecomputeIfStale()
    }

    fun renderFailedSyncResult(result: ManualSyncEngine.SyncResult) {
        val classification = dev.bee.kanjianki.core.SyncFailureClassification.classify(
            result.message,
            permanentFailure = !result.retryable,
            retryable = result.retryable,
        )
        val guidance = dev.bee.kanjianki.core.SyncFailureClassification.userMessage(classification)
        renderSyncResultScreen(
            SyncResultScreenModel(
                HomeTextCopy.syncNeedsAttentionTitle(),
                HomeTextCopy.syncReadErrorTitle(),
                listOf(guidance, nonEmptyOr(result.message, HomeTextCopy.syncFailureFallback())),
                CORAL,
                HomeTextCopy.trySyncAgainLabel(),
                TEAL,
                this::confirmSync,
                StudyTextCopy.backHomeLabel(),
                this::renderHome,
            )
        )
    }

    private fun renderSyncResultScreen(model: SyncResultScreenModel) {
        renderHomeRoute(backAction = Runnable { renderHome() }) {
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
        const val UPDATE_CHECK_FAILURE_EXPIRY_MS = 24L * 60 * 60 * 1000
    }

    fun renderBrowseKanji(query: String?) {
        browseDetail.renderBrowseKanji(query, false)
    }

    fun renderBrowseKanji(query: String?, onlySimilarKanji: Boolean) {
        browseDetail.renderBrowseKanji(query, onlySimilarKanji, false)
    }

    fun renderBrowseKanji(query: String?, onlySimilarKanji: Boolean, allKanjiScope: Boolean) {
        browseDetail.renderBrowseKanji(query, onlySimilarKanji, allKanjiScope)
    }

    fun renderReadOnlyDetail(kanji: String, browseQuery: String?) {
        browseDetail.renderReadOnlyDetail(kanji, browseQuery)
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
            load = warmThemeThen(load),
            render = render,
            renderError = { error ->
                renderRouteLoadError(error) { renderAsyncHomeRoute(loadingTitle, load, render, traceName) }
            },
            traceLabel = traceName,
            showLoadingAfterMs = 120,
        )
    }

    /**
     * Generic async route loader shared by non-home routes (e.g. Settings). Mirrors
     * [renderAsyncHomeRoute] but lets the caller own the loading + rendered content so it can
     * compose on its own route (correct test tag, back action, scroll). Keeps the heavy
     * store/model work off the main thread so the triggering button responds well under the
     * 1s latency budget instead of blocking the UI thread while the screen model is built.
     */
    fun <T> loadRouteAsync(
        showLoading: () -> Unit,
        load: () -> T,
        render: (T) -> Unit,
        traceName: String = "route",
        showLoadingAfterMs: Long = 120,
    ) {
        asyncHomeRouteLoader.load(
            showLoading = showLoading,
            load = warmThemeThen(load),
            render = render,
            renderError = { error ->
                renderRouteLoadError(error) { loadRouteAsync(showLoading, load, render, traceName, showLoadingAfterMs) }
            },
            traceLabel = traceName,
            showLoadingAfterMs = showLoadingAfterMs,
        )
    }

    /**
     * Route composition reads the theme choice on the main thread through an in-memory
     * cache. Populate that cache here on the background executor so the main thread
     * never has to fall back to a synchronous SQLite read that could block behind a
     * cold-boot database open or migration (historical cold-boot ANR source).
     */
    private fun <T> warmThemeThen(load: () -> T): () -> T {
        return {
            if (isStoreInitialized()) {
                runCatching { store.appThemeChoice() }
            }
            load()
        }
    }

    /**
     * Renders a recoverable error screen for a failed background route load. Route
     * loads used to rethrow on the main thread, which crashed the app during cold
     * boot whenever the model build failed; keep the shell alive instead.
     */
    private fun renderRouteLoadError(error: Throwable, retry: () -> Unit) {
        android.util.Log.e("Kani", "Background route load failed", error)
        AppDebugLog.logError("route load failed route=$currentRoute", error)
        composeRoute(selected = currentRoute) {
            HomeRouteErrorScreen(
                title = HomeTextCopy.routeLoadErrorTitle(),
                retryLabel = HomeTextCopy.retryLabel(),
                onRetry = retry,
                homeLabel = HomeTextCopy.homeLabel(),
                onHome = this::renderHome,
            )
        }
    }

    fun cancelPendingHomeRouteLoads() {
        asyncHomeRouteLoader.cancelPending()
    }

    fun scheduleStatsPrecomputeIfStale(): Boolean {
        return statsPrecomputeScheduler.scheduleIfStale()
    }

    fun scheduleStatsPrecomputeIfStaleAsync() {
        try {
            maintenance.execute {
                scheduleStatsPrecomputeIfStale()
            }
        } catch (error: RejectedExecutionException) {
            reportStatsPrecomputeError(error)
        }
    }

    private fun reportStatsPrecomputeError(error: Throwable) {
        try {
            android.util.Log.e("Kani", "Stats precompute failed", error)
        } catch (_: RuntimeException) {
            // Optional maintenance must stay isolated even if the platform logger fails.
        }
        try {
            AppDebugLog.logError("stats precompute failed", error)
        } catch (_: RuntimeException) {
            // The debug-log executor can reject work during process teardown.
        }
    }

    fun renderDetail(kanji: String, fromBrowse: Boolean, browseQuery: String?) {
        browseDetail.renderDetail(kanji, fromBrowse, browseQuery)
    }

    fun renderDetail(kanji: String, fromBrowse: Boolean, browseQuery: String?, customBackAction: Runnable?) {
        browseDetail.renderDetail(kanji, fromBrowse, browseQuery, customBackAction)
    }
}

/** Capture-gated release timing for cold Home phases; the disabled path only invokes [action]. */
internal fun <T> homeLoadPhase(
    phase: String,
    details: (T) -> String = { "" },
    action: () -> T,
): T {
    if (!AppDebugLog.isCapturing()) {
        return action()
    }
    val startedAtNanos = homeLoadMonotonicNanos()
    return try {
        val result = action()
        val detail = details(result).trim()
        AppDebugLog.log(
            String.format(
                Locale.US,
                "home phase=%s duration_ms=%.2f%s",
                traceToken(phase),
                (homeLoadMonotonicNanos() - startedAtNanos) / 1_000_000.0,
                if (detail.isEmpty()) "" else " $detail",
            ),
        )
        result
    } catch (error: Throwable) {
        AppDebugLog.logError(
            String.format(
                Locale.US,
                "home phase=%s failed duration_ms=%.2f",
                traceToken(phase),
                (homeLoadMonotonicNanos() - startedAtNanos) / 1_000_000.0,
            ),
            error,
        )
        throw error
    }
}

private fun homeLoadMonotonicNanos(): Long {
    return runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime())
}
