package dev.bee.kanjianki.sync

import androidx.work.ListenableWorker
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncRetryWorkerTest {
    @Test
    fun transientResultsRetryOnlyUntilTheThirdExecution() {
        val transient = AutoSyncRunner.Result.retryableFailure("provider locked")

        assertRetry(AutoSyncRetryWorker.workerResult(transient, 0))
        assertRetry(AutoSyncRetryWorker.workerResult(transient, 1))
        assertSuccess(AutoSyncRetryWorker.workerResult(transient, 2))
        assertSuccess(AutoSyncRetryWorker.workerResult(transient, 20))
    }

    @Test
    fun terminalSuccessfulAndIrrelevantResultsCompleteTheChain() {
        assertSuccess(
            AutoSyncRetryWorker.workerResult(
                AutoSyncRunner.Result.failed("permission missing"),
                0,
            ),
        )
        assertSuccess(
            AutoSyncRetryWorker.workerResult(
                AutoSyncRunner.Result.success("done"),
                0,
            ),
        )
        assertSuccess(
            AutoSyncRetryWorker.workerResult(
                AutoSyncRunner.Result.skipped("already synced"),
                0,
            ),
        )
    }

    @Test
    fun concurrentSyncDeferralUsesTheSameBoundedRetryPolicy() {
        val deferred = AutoSyncRunner.Result.deferred("Sync already running.")

        assertRetry(AutoSyncRetryWorker.workerResult(deferred, 0))
    }

    private fun assertSuccess(result: ListenableWorker.Result) {
        assertTrue(result is ListenableWorker.Result.Success)
    }

    private fun assertRetry(result: ListenableWorker.Result) {
        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
