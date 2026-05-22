package dev.bee.kanjianki

import android.app.AlertDialog
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import dev.bee.kanjianki.core.AdaptiveFocusCopy
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.StudyStatsStore
import dev.bee.kanjianki.sync.ManualSyncEngine
import dev.bee.kanjianki.sync.AutoSyncScheduler

internal abstract class MainActivityHome : MainActivityBase() {
    @JvmField
    var activeBrowseQuery: String = ""

    private val focusQueue = MainActivityHomeFocusQueue(this)
    private val browseDetail = MainActivityHomeBrowseDetail(this)

    abstract fun renderStats()
    abstract fun renderGames()

    override fun renderHome() {
        clearStudyModeOverrides()

        val now = System.currentTimeMillis()
        val sync = store.latestSync()
        val streak = store.studyStreak(now)
        val rows = store.activeDashboardRows()
        val homeItems = studyQueue(rows, now, false, null)
        val homePlan = if (rows.isEmpty()) null else adaptivePlan(rows, homeItems, now)
        val entries = if (rows.isEmpty()) {
            emptyList()
        } else {
            queuedEntries(rows, homeItems, now, homePlan)
        }
        val provider = gateway.status()

        val model = HomeScreenModel(
            title = HomeTextCopy.appTitle(),
            subtitle = HomeTextCopy.appSubtitle(),
            metrics = homeMetricModels(this, sync, provider, streak, homePlan),
            showSyncCta = rows.isEmpty(),
            syncLabel = HomeTextCopy.syncAnkiDroidLabel(),
            studyLabel = MainActivityBase.LABEL_STUDY_NOW,
            studySubtitle = HomeTextCopy.studySupportText(),
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
                homeFocusQueueCardModel(this, entry, now)
            }
        )
        renderHomeRoute {
            HomeScreen(model)
        }
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
        val current = settings()
        AlertDialog.Builder(this)
            .setTitle(HomeTextCopy.syncDialogTitle())
            .setMessage(HomeTextCopy.syncDialogMessage(current))
            .setPositiveButton(HomeTextCopy.syncDialogPositiveLabel()) { _, _ -> runSync() }
            .setNegativeButton(HomeTextCopy.cancelLabel(), null)
            .show()
    }

    fun runSync() {
        val progressView = SyncProgressPanel()
        renderHomeRoute {
            SyncProgressScreen(
                title = HomeTextCopy.syncingTitle(),
                progressPanel = progressView,
            )
        }
        val syncGateway = MainActivityBase.collectionGatewayForTests ?: gateway
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
        val now = System.currentTimeMillis()
        val rows = store.activeDashboardRows()
        val items = store.studyItems()
        val plan = adaptivePlan(rows, items, now)
        val entries = queuedEntries(rows, items, now, plan)
        val summaryLines = mutableListOf<String>()
        summaryLines.add(HomeTextCopy.syncCandidateSummary(result.dashboardRows, AdaptiveFocusCopy.adaptiveFocusText(plan)))
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
                HomeTextCopy.syncReadyCountText(entries.size),
                summaryLines,
                TEAL,
                if (result.dashboardRows > 0) LABEL_STUDY_NOW else null,
                CORAL,
                if (result.dashboardRows > 0) ::startFocusedStudy else null,
                LABEL_BACK_HOME,
                this::renderHome,
            )
        )
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
    ): List<RecordsStudyModels.StudyItem> {
        return focusQueue.studyQueue(rows, now, persist, plan)
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

    fun kanjiTile(value: String, sizePx: Int, textSp: Int): TextView {
        return text(value, textSp, INK, true).apply {
            gravity = Gravity.CENTER
            setTypeface(fontResource(R.font.kaisei_tokumin_regular, Typeface.SERIF), Typeface.BOLD)
            background = panel(BLUSH, BLUSH, dp(10))
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        }
    }

    private companion object {
        const val HOME_PREVIEW_ROW_LIMIT = 3
    }

    fun renderBrowseKanji(query: String?) {
        browseDetail.renderBrowseKanji(query)
    }

    fun renderDetail(kanji: String, fromBrowse: Boolean, browseQuery: String?) {
        browseDetail.renderDetail(kanji, fromBrowse, browseQuery)
    }
}
