package dev.bee.kanjianki.update

import androidx.work.ListenableWorker
import dev.bee.kanjianki.updatecore.AutoUpdateRunPolicy
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdateWorkerTest {
    @Test
    fun autoUpdateDecisionSkipsDisabledAndPendingUpdates() {
        val checks = AtomicInteger()

        val disabled = AutoUpdateWorker.runAutoUpdate(
            false,
            false,
        ) {
            checks.incrementAndGet()
            result(false)
        }
        val pending = AutoUpdateWorker.runAutoUpdate(
            true,
            true,
        ) {
            checks.incrementAndGet()
            result(false)
        }

        assertSuccess(disabled)
        assertSuccess(pending)
        assertEquals(0, checks.get())
    }

    @Test
    fun autoUpdateDecisionMapsCheckerResultToWorkerOutcome() {
        assertSuccess(AutoUpdateWorker.runAutoUpdate(true, false) { result(false) })
        assertRetry(AutoUpdateWorker.runAutoUpdate(true, false) { result(true) })
        assertSuccess(AutoUpdateWorker.workerResult(AutoUpdateRunPolicy.WorkerOutcome.SUCCESS))
        assertRetry(AutoUpdateWorker.workerResult(AutoUpdateRunPolicy.WorkerOutcome.RETRY))
    }

    @Test
    fun automaticUpdateCheckerFactoryRunsInjectedAutomaticRunner() {
        val checks = AtomicInteger()

        val checker = AutoUpdateWorker.automaticUpdateCheckerFactory { _ ->
            checks.incrementAndGet()
            result(false)
        }.create(null)

        val result = checker.check()

        assertEquals(1, checks.get())
        assertEquals("done", result.message)
    }
}

private fun result(retryable: Boolean): GitHubUpdater.UpdateResult =
    GitHubUpdater.UpdateResult(!retryable, if (retryable) "retry later" else "done", null, false, retryable)

private fun assertSuccess(result: ListenableWorker.Result) {
    assertTrue(result is ListenableWorker.Result.Success)
}

private fun assertRetry(result: ListenableWorker.Result) {
    assertTrue(result is ListenableWorker.Result.Retry)
}
