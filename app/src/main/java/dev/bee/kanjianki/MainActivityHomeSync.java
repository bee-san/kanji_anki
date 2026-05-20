package dev.bee.kanjianki;

import android.app.AlertDialog;

import java.util.ArrayList;
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
        home.content.addView(SyncProgressPanelKt.syncProgressTitleView(home, HomeTextCopy.syncingTitle()));
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
        home.content.addView(MainActivityHomeSyncCompose.syncResultScreenView(
                home,
                new SyncResultScreenModel(
                        HomeTextCopy.syncAlreadyRunningTitle(),
                        null,
                        List.of(home.nonEmptyOr(result.message, HomeTextCopy.syncAlreadyRunningFallback())),
                        home.BLUE,
                        null,
                        home.TEAL,
                        null,
                        MainActivityBase.LABEL_BACK_HOME,
                        home::renderHome
                )
        ));
    }

    void renderSuccessfulSyncResult(ManualSyncEngine.SyncResult result) {
        long now = System.currentTimeMillis();
        List<RecordsImportModels.DashboardRow> rows = home.store.activeDashboardRows();
        List<RecordsStudyModels.StudyItem> items = home.store.studyItems();
        RecordsSchedulerModels.AdaptiveLoadPlan plan = home.adaptivePlan(rows, items, now);
        List<MainActivityBase.QueueEntry> entries = home.queuedEntries(rows, items, now, plan);
        List<String> summaryLines = new ArrayList<>();
        summaryLines.add(HomeTextCopy.syncCandidateSummary(result.dashboardRows, AdaptiveFocusCopy.adaptiveFocusText(plan)));
        if (!result.adaptiveSummary.isEmpty()) {
            summaryLines.add(result.adaptiveSummary);
        }
        if (result.importedSuspendedKanji > 0) {
            summaryLines.add(HomeTextCopy.importedSuspendedKanjiText(result.importedSuspendedKanji));
        }
        if (result.message != null && !result.message.isEmpty()) {
            summaryLines.add(result.message);
        }
        home.content.addView(MainActivityHomeSyncCompose.syncResultScreenView(
                home,
                new SyncResultScreenModel(
                        HomeTextCopy.syncCompleteTitle(),
                        HomeTextCopy.syncReadyCountText(entries.size()),
                        summaryLines,
                        home.TEAL,
                        result.dashboardRows > 0 ? MainActivityBase.LABEL_STUDY_NOW : null,
                        home.CORAL,
                        result.dashboardRows > 0 ? home::startFocusedStudy : null,
                        MainActivityBase.LABEL_BACK_HOME,
                        home::renderHome
                )
        ));
    }

    void renderFailedSyncResult(ManualSyncEngine.SyncResult result) {
        home.content.addView(MainActivityHomeSyncCompose.syncResultScreenView(
                home,
                new SyncResultScreenModel(
                        HomeTextCopy.syncNeedsAttentionTitle(),
                        HomeTextCopy.syncReadErrorTitle(),
                        List.of(home.nonEmptyOr(result.message, HomeTextCopy.syncFailureFallback())),
                        home.CORAL,
                        HomeTextCopy.trySyncAgainLabel(),
                        home.TEAL,
                        this::confirmSync,
                        MainActivityBase.LABEL_BACK_HOME,
                        home::renderHome
                )
        ));
    }
}
