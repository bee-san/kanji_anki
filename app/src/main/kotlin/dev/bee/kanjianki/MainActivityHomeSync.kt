package dev.bee.kanjianki

import android.app.AlertDialog
import dev.bee.kanjianki.core.AdaptiveFocusCopy
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.sync.AutoSyncScheduler
import dev.bee.kanjianki.sync.ManualSyncEngine

internal class MainActivityHomeSync(private val home: MainActivityHome) {
    fun confirmSync() {
        val current = home.settings()
        AlertDialog.Builder(home)
            .setTitle(HomeTextCopy.syncDialogTitle())
            .setMessage(HomeTextCopy.syncDialogMessage(current))
            .setPositiveButton(HomeTextCopy.syncDialogPositiveLabel()) { _, _ -> runSync() }
            .setNegativeButton(HomeTextCopy.cancelLabel(), null)
            .show()
    }

    fun runSync() {
        val progressView = SyncProgressPanel()
        home.renderHomeRoute {
            SyncProgressScreen(
                title = HomeTextCopy.syncingTitle(),
                progressPanel = progressView,
            )
        }
        val syncGateway = MainActivityBase.collectionGatewayForTests ?: home.gateway
        val coordinator = ManualSyncCoordinator(
            home.io,
            home.main::post,
            { progress ->
                ManualSyncEngine(
                    home,
                    home.store,
                    syncGateway,
                    home.settings(),
                    progress,
                ).run()
            },
            {
                home.store.activateAutoSyncAfterFirstSuccess()
                AutoSyncScheduler.schedule(home)
            },
            this::renderSyncResult,
        )
        coordinator.start { update -> home.main.post { progressView.render(update) } }
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
                listOf(home.nonEmptyOr(result.message, HomeTextCopy.syncAlreadyRunningFallback())),
                MainActivityBase.BLUE,
                null,
                MainActivityBase.TEAL,
                null,
                MainActivityBase.LABEL_BACK_HOME,
                home::renderHome,
            )
        )
    }

    fun renderSuccessfulSyncResult(result: ManualSyncEngine.SyncResult) {
        val now = System.currentTimeMillis()
        val rows = home.store.activeDashboardRows()
        val items = home.store.studyItems()
        val plan = home.adaptivePlan(rows, items, now)
        val entries = home.queuedEntries(rows, items, now, plan)
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
                MainActivityBase.TEAL,
                if (result.dashboardRows > 0) MainActivityBase.LABEL_STUDY_NOW else null,
                MainActivityBase.CORAL,
                if (result.dashboardRows > 0) home::startFocusedStudy else null,
                MainActivityBase.LABEL_BACK_HOME,
                home::renderHome,
            )
        )
    }

    fun renderFailedSyncResult(result: ManualSyncEngine.SyncResult) {
        renderSyncResultScreen(
            SyncResultScreenModel(
                HomeTextCopy.syncNeedsAttentionTitle(),
                HomeTextCopy.syncReadErrorTitle(),
                listOf(home.nonEmptyOr(result.message, HomeTextCopy.syncFailureFallback())),
                MainActivityBase.CORAL,
                HomeTextCopy.trySyncAgainLabel(),
                MainActivityBase.TEAL,
                this::confirmSync,
                MainActivityBase.LABEL_BACK_HOME,
                home::renderHome,
            )
        )
    }

    private fun renderSyncResultScreen(model: SyncResultScreenModel) {
        home.renderHomeRoute {
            SyncResultScreen(model)
        }
    }
}
