package dev.bee.kanjianki

import dev.bee.kanjianki.sync.ManualSyncEngine
import dev.bee.kanjianki.sync.SyncProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ManualSyncCoordinatorTest {
    @Test
    fun successfulSyncRunsSuccessActionBeforeRenderingResult() {
        val result = syncResult(true, false, "ok")
        val events = mutableListOf<String>()
        val rendered = AtomicReference<ManualSyncEngine.SyncResult>()

        val coordinator = ManualSyncCoordinator(
            Executor { runnable -> runnable.run() },
            ManualSyncCoordinator.UiPoster { runnable -> runnable.run() },
            ManualSyncCoordinator.SyncRunner { progress ->
                events.add("run")
                progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.READING_NOTES))
                result
            },
            ManualSyncCoordinator.SuccessAction {
                events.add("success")
            },
            ManualSyncCoordinator.ResultRenderer { syncResult ->
                events.add("render")
                rendered.set(syncResult)
            },
        )

        val progressCalled = AtomicBoolean(false)
        coordinator.start(SyncProgress.Listener { progressCalled.set(true) })

        assertEquals(listOf("run", "success", "render"), events)
        assertTrue(progressCalled.get())
        assertSame(result, rendered.get())
    }

    @Test
    fun failedSyncRendersWithoutSuccessAction() {
        val result = syncResult(false, false, "failed")
        val success = AtomicBoolean(false)
        val rendered = AtomicReference<ManualSyncEngine.SyncResult>()

        val coordinator = ManualSyncCoordinator(
            Executor { runnable -> runnable.run() },
            ManualSyncCoordinator.UiPoster { runnable -> runnable.run() },
            ManualSyncCoordinator.SyncRunner { result },
            ManualSyncCoordinator.SuccessAction { success.set(true) },
            ManualSyncCoordinator.ResultRenderer { syncResult -> rendered.set(syncResult) },
        )

        coordinator.start(null)

        assertFalse(success.get())
        assertSame(result, rendered.get())
    }

    private fun syncResult(success: Boolean, skipped: Boolean, message: String): ManualSyncEngine.SyncResult {
        val constructor = ManualSyncEngine.SyncResult::class.java.getDeclaredConstructor(
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            String::class.java,
            String::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(success, skipped, 0, 0, message, "")
    }
}
