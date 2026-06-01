package dev.bee.kanjianki

import dev.bee.kanjianki.sync.ManualSyncEngine
import dev.bee.kanjianki.sync.SyncProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Constructor
import java.util.ArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ManualSyncCoordinatorTest {
    @Test
    fun successfulSyncRunsSuccessActionBeforeRenderingResult() {
        val result = syncResult(true, false, "ok")
        val events = ArrayList<String>()
        val rendered = AtomicReference<ManualSyncEngine.SyncResult>()

        val coordinator = ManualSyncCoordinator(
            directExecutor(),
            Runnable::run,
            { progress ->
                events.add("run")
                progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.READING_NOTES))
                result
            },
            ManualSyncCoordinator.SuccessAction { events.add("success") },
            { syncResult ->
                events.add("render")
                rendered.set(syncResult)
            },
        )

        val progressCalled = AtomicBoolean(false)
        coordinator.start { progressCalled.set(true) }

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
            directExecutor(),
            Runnable::run,
            { result },
            ManualSyncCoordinator.SuccessAction { success.set(true) },
            rendered::set,
        )

        coordinator.start(null)

        assertFalse(success.get())
        assertSame(result, rendered.get())
    }

    private fun directExecutor(): Executor = Executor(Runnable::run)

    private fun syncResult(success: Boolean, skipped: Boolean, message: String): ManualSyncEngine.SyncResult {
        val constructor: Constructor<ManualSyncEngine.SyncResult> =
            ManualSyncEngine.SyncResult::class.java.getDeclaredConstructor(
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
            )
        constructor.isAccessible = true
        return constructor.newInstance(success, skipped, 0, 0, message, "")
    }
}
