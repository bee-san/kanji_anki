package dev.bee.kanjianki;

import android.app.AlertDialog;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.List;

import dev.bee.kanjianki.anki.CollectionGateway;
import dev.bee.kanjianki.core.AdaptiveFocusCopy;
import dev.bee.kanjianki.core.HomeTextCopy;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.sync.AutoSyncScheduler;
import dev.bee.kanjianki.sync.ManualSyncEngine;

final class MainActivityHomeSync {
    private final MainActivityHome home;

    MainActivityHomeSync(MainActivityHome home) {
        this.home = home;
    }

    void confirmSync() {
        RecordsSyncModels.Settings current = home.settings();
        new AlertDialog.Builder(home)
                .setTitle(HomeTextCopy.syncDialogTitle())
                .setMessage(HomeTextCopy.syncDialogMessage(current))
                .setPositiveButton(HomeTextCopy.syncDialogPositiveLabel(), (dialog, which) -> runSync())
                .setNegativeButton(HomeTextCopy.cancelLabel(), null)
                .show();
    }

    void runSync() {
        home.base("home");
        home.content.addView(home.text(HomeTextCopy.syncingTitle(), 34, home.INK, true));
        SyncProgressPanel progressView = new SyncProgressPanel(home);
        home.content.addView(progressView);
        CollectionGateway syncGateway = MainActivityHome.collectionGatewayForTests == null
                ? home.gateway
                : MainActivityHome.collectionGatewayForTests;
        ManualSyncCoordinator coordinator = new ManualSyncCoordinator(
                home.io,
                home.main::post,
                progress -> new ManualSyncEngine(
                        home,
                        home.store,
                        syncGateway,
                        home.settings(),
                        progress
                ).run(),
                () -> {
                    home.store.activateAutoSyncAfterFirstSuccess();
                    AutoSyncScheduler.schedule(home);
                },
                this::renderSyncResult
        );
        coordinator.start(update -> home.main.post(() -> progressView.render(update)));
    }

    void renderSyncResult(ManualSyncEngine.SyncResult result) {
        home.base("home");
        if (result.skipped) {
            renderSkippedSyncResult(result);
        } else if (result.success) {
            renderSuccessfulSyncResult(result);
        } else {
            renderFailedSyncResult(result);
        }
    }

    void renderSkippedSyncResult(ManualSyncEngine.SyncResult result) {
        home.content.addView(home.text(HomeTextCopy.syncAlreadyRunningTitle(), 34, home.INK, true));
        LinearLayout info = home.band(home.BLUE);
        info.addView(home.text(result.message == null || result.message.isEmpty() ? HomeTextCopy.syncAlreadyRunningFallback() : result.message, 17, android.graphics.Color.WHITE, false));
        home.content.addView(info);
        Button homeButton = home.secondaryButton(MainActivityBase.LABEL_BACK_HOME);
        homeButton.setOnClickListener(new RunnableClickListener(home::renderHome));
        home.content.addView(homeButton);
    }

    void renderSuccessfulSyncResult(ManualSyncEngine.SyncResult result) {
        home.content.addView(home.text(HomeTextCopy.syncCompleteTitle(), 34, home.INK, true));
        LinearLayout summary = home.band(home.TEAL);
        long now = System.currentTimeMillis();
        List<RecordsImportModels.DashboardRow> rows = home.store.activeDashboardRows();
        List<RecordsStudyModels.StudyItem> items = home.store.studyItems();
        RecordsSchedulerModels.AdaptiveLoadPlan plan = home.adaptivePlan(rows, items, now);
        List<MainActivityBase.QueueEntry> entries = home.queuedEntries(rows, items, now, plan);
        summary.addView(home.text(HomeTextCopy.syncReadyCountText(entries.size()), 24, android.graphics.Color.WHITE, true));
        summary.addView(home.text(HomeTextCopy.syncCandidateSummary(result.dashboardRows, AdaptiveFocusCopy.adaptiveFocusText(plan)), 16, android.graphics.Color.WHITE, false));
        if (!result.adaptiveSummary.isEmpty()) {
            summary.addView(home.text(result.adaptiveSummary, 15, android.graphics.Color.WHITE, false));
        }
        if (result.importedSuspendedKanji > 0) {
            summary.addView(home.text(HomeTextCopy.importedSuspendedKanjiText(result.importedSuspendedKanji), 15, android.graphics.Color.WHITE, false));
        }
        if (result.message != null && !result.message.isEmpty()) {
            summary.addView(home.text(result.message, 14, android.graphics.Color.WHITE, false));
        }
        home.content.addView(summary);
        if (result.dashboardRows > 0) {
            Button study = home.primaryButton(MainActivityBase.LABEL_STUDY_NOW, home.CORAL);
            study.setOnClickListener(new RunnableClickListener(home::startFocusedStudy));
            home.content.addView(study);
        }
        Button homeButton = home.secondaryButton(MainActivityBase.LABEL_BACK_HOME);
        homeButton.setOnClickListener(new RunnableClickListener(home::renderHome));
        home.content.addView(homeButton);
    }

    void renderFailedSyncResult(ManualSyncEngine.SyncResult result) {
        home.content.addView(home.text(HomeTextCopy.syncNeedsAttentionTitle(), 34, home.INK, true));
        LinearLayout error = home.band(home.CORAL);
        error.addView(home.text(HomeTextCopy.syncReadErrorTitle(), 24, android.graphics.Color.WHITE, true));
        error.addView(home.text(result.message == null || result.message.isEmpty() ? HomeTextCopy.syncFailureFallback() : result.message, 16, android.graphics.Color.WHITE, false));
        home.content.addView(error);
        Button retry = home.primaryButton(HomeTextCopy.trySyncAgainLabel(), home.TEAL);
        retry.setOnClickListener(new RunnableClickListener(this::confirmSync));
        home.content.addView(retry);
        Button homeButton = home.secondaryButton(MainActivityBase.LABEL_BACK_HOME);
        homeButton.setOnClickListener(new RunnableClickListener(home::renderHome));
        home.content.addView(homeButton);
    }
}
