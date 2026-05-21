package dev.bee.kanjianki

import dev.bee.kanjianki.sync.ManualSyncEngine
import dev.bee.kanjianki.sync.SyncProgress
import java.util.concurrent.Executor

internal class ManualSyncCoordinator(
    private val background: Executor,
    private val uiPoster: UiPoster,
    private val syncRunner: SyncRunner,
    private val successAction: SuccessAction,
    private val resultRenderer: ResultRenderer,
) {
    fun start(progress: SyncProgress.Listener?) {
        val listener = progress ?: SyncProgress.NONE
        background.execute {
            val result = syncRunner.run(listener)
            if (result.success) {
                successAction.afterSuccessfulSync()
            }
            uiPoster.post {
                resultRenderer.render(result)
            }
        }
    }

    fun interface UiPoster {
        fun post(runnable: Runnable)
    }

    fun interface SyncRunner {
        fun run(progress: SyncProgress.Listener): ManualSyncEngine.SyncResult
    }

    fun interface SuccessAction {
        fun afterSuccessfulSync()
    }

    fun interface ResultRenderer {
        fun render(result: ManualSyncEngine.SyncResult)
    }
}
