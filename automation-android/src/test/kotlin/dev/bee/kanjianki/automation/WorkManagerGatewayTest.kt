package dev.bee.kanjianki.automation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkManagerGatewayTest {
    private lateinit var context: Context
    private lateinit var gateway: AndroidWorkManagerGateway

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        gateway = AndroidWorkManagerGateway(context)
    }

    @Test
    fun forwardsUniqueWorkAndPersistsCancellation() {
        gateway.enqueueUniqueWork(
            "one-time",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequest.Builder(NoopWorker::class.java).build(),
        ).await()
        gateway.cancelUniqueWork("one-time").await()

        val workManager = androidx.work.WorkManager.getInstance(context)
        assertEquals(1, workManager.getWorkInfosForUniqueWork("one-time").get().size)
    }

    @Test
    fun forwardsUniquePeriodicWork() {
        gateway.enqueueUniquePeriodicWork(
            "periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequest.Builder(NoopWorker::class.java, 1L, TimeUnit.DAYS).build(),
        ).await()

        val workManager = androidx.work.WorkManager.getInstance(context)
        assertEquals(1, workManager.getWorkInfosForUniqueWork("periodic").get().size)
    }

    @Test
    fun rejectsNonPositivePersistenceTimeout() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidWorkManagerGateway(context, 0L)
        }
    }

    class NoopWorker(
        context: Context,
        parameters: WorkerParameters,
    ) : Worker(context, parameters) {
        override fun doWork(): ListenableWorker.Result = Result.success()
    }
}
