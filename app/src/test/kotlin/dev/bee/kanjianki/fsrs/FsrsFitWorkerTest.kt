package dev.bee.kanjianki.fsrs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestWorkerBuilder
import dev.bee.kanjianki.core.FsrsWeightFitter
import dev.bee.kanjianki.data.FsrsFitSummaryCodec
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FsrsFitWorkerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        FsrsFitExecutionGate.release()
    }

    @After
    fun tearDown() {
        FsrsFitExecutionGate.release()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun disabledWorkerSucceedsWithoutCreatingFitSummary() {
        LocalStore(context).use { it.saveFsrsPersonalizationEnabled(false) }

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        LocalStore(context).use { assertTrue(it.fsrsFitSummaryJson().isBlank()) }
    }

    @Test
    fun enabledWorkerWithNoHistoryRecordsNotEnoughHistorySummary() {
        LocalStore(context).use { it.saveFsrsPersonalizationEnabled(true) }

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        LocalStore(context).use { store ->
            val summary = FsrsFitSummaryCodec.decode(store.fsrsFitSummaryJson())
            assertEquals(FsrsWeightFitter.REASON_NOT_ENOUGH_HISTORY, summary?.reason)
            assertEquals(0, summary?.sampleCount)
        }
    }

    @Test
    fun concurrentWorkerRetriesAndReleasesNothingItDidNotAcquire() {
        assertTrue(FsrsFitExecutionGate.tryAcquire())

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(false, FsrsFitExecutionGate.tryAcquire())
    }

    private fun worker(): FsrsFitWorker = TestWorkerBuilder
        .from(context, FsrsFitWorker::class.java, SynchronousExecutor())
        .build()
}
