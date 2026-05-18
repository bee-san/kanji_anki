package dev.bee.kanjianki;

import dev.bee.kanjianki.sync.ManualSyncEngine;
import dev.bee.kanjianki.sync.SyncProgress;

import java.util.concurrent.Executor;

final class ManualSyncCoordinator {
    private final Executor background;
    private final UiPoster uiPoster;
    private final SyncRunner syncRunner;
    private final SuccessAction successAction;
    private final ResultRenderer resultRenderer;

    ManualSyncCoordinator(
            Executor background,
            UiPoster uiPoster,
            SyncRunner syncRunner,
            SuccessAction successAction,
            ResultRenderer resultRenderer
    ) {
        this.background = background;
        this.uiPoster = uiPoster;
        this.syncRunner = syncRunner;
        this.successAction = successAction;
        this.resultRenderer = resultRenderer;
    }

    void start(SyncProgress.Listener progress) {
        SyncProgress.Listener listener = progress == null ? SyncProgress.NONE : progress;
        background.execute(() -> {
            ManualSyncEngine.SyncResult result = syncRunner.run(listener);
            if (result.success) {
                successAction.afterSuccessfulSync();
            }
            uiPoster.post(() -> resultRenderer.render(result));
        });
    }

    interface UiPoster {
        void post(Runnable runnable);
    }

    interface SyncRunner {
        ManualSyncEngine.SyncResult run(SyncProgress.Listener progress);
    }

    interface SuccessAction {
        void afterSuccessfulSync();
    }

    interface ResultRenderer {
        void render(ManualSyncEngine.SyncResult result);
    }
}
