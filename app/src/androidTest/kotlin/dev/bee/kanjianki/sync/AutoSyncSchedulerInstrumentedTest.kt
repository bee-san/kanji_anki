package dev.bee.kanjianki.sync

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobWorkItem
import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.KaniTestDatabase
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.platform.AppClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class AutoSyncSchedulerInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        KaniTestDatabase.delete(context)
    }

    @After
    fun tearDown() {
        AutoSyncScheduler.cancel(context)
        KaniTestDatabase.delete(context)
    }

    @Test
    fun androidBackendRecordsNextRunWhenSystemSchedulerAcceptsJob() {
        LocalStore(context).use { store ->
            val now = fixedNow()
            AutoSyncScheduler.schedule(context, store, settings(), AppClock { now })

            assertEquals(nextRunAfter(now), store.autoSyncSettings().nextRunAt)
        }
    }

    @Test
    fun androidBackendClearsNextRunAndCancelNoopsWhenJobSchedulerIsUnavailable() {
        val noJobScheduler = NoJobSchedulerContext(context)
        LocalStore(noJobScheduler).use { store ->
            AutoSyncScheduler.schedule(noJobScheduler, store, settings())

            assertEquals(0L, store.autoSyncSettings().nextRunAt)
        }

        AutoSyncScheduler.cancel(noJobScheduler)
    }

    @Test
    fun androidBackendClearsNextRunWhenSystemSchedulerRejectsJob() {
        val rejectingJobScheduler = RejectingJobSchedulerContext(context)
        LocalStore(rejectingJobScheduler).use { store ->
            AutoSyncScheduler.schedule(rejectingJobScheduler, store, settings())

            assertEquals(0L, store.autoSyncSettings().nextRunAt)
        }

        AutoSyncScheduler.cancel(rejectingJobScheduler)
    }

    @Test
    fun scheduleAtClearsRecordedRunWhenBackendThrowsOnAndroidLogPath() {
        val recorder = RecordingRecorder()
        val now = fixedNow()

        AutoSyncScheduler.scheduleAt(
            recorder,
            ThrowingBackend(),
            now + 60_000L,
            now,
        )

        assertEquals(0L, recorder.nextRunAt)
    }

    private fun settings(): LocalStoreBase.AutoSyncSettings {
        return LocalStoreBase.AutoSyncSettings(true, true, 23, 59, 0L, 0L, 0L)
    }

    private fun fixedNow(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.MAY, 15, 12, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun nextRunAfter(now: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private class RecordingRecorder : AutoSyncScheduler.ScheduleRecorder {
        var nextRunAt: Long = -1L

        override fun markAutoSyncScheduled(nextRunAt: Long) {
            this.nextRunAt = nextRunAt
        }
    }

    private class ThrowingBackend : AutoSyncScheduler.SchedulerBackend {
        override fun schedule(minimumLatencyMillis: Long, overrideDeadlineMillis: Long): Boolean {
            throw IllegalStateException("job scheduler rejected the request")
        }

        override fun cancel() {
            // Throwing backend is only used to exercise schedule failures.
        }
    }

    private class NoJobSchedulerContext(base: Context) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getSystemService(name: String): Any? {
            if (Context.JOB_SCHEDULER_SERVICE == name) {
                return null
            }
            return super.getSystemService(name)
        }
    }

    private class RejectingJobSchedulerContext(base: Context) : ContextWrapper(base) {
        private val scheduler = RejectingJobScheduler()

        override fun getApplicationContext(): Context = this

        override fun getSystemService(name: String): Any? {
            if (Context.JOB_SCHEDULER_SERVICE == name) {
                return scheduler
            }
            return super.getSystemService(name)
        }
    }

    private class RejectingJobScheduler : JobScheduler() {
        override fun schedule(job: JobInfo): Int {
            return JobScheduler.RESULT_FAILURE
        }

        override fun enqueue(job: JobInfo, work: JobWorkItem): Int {
            return JobScheduler.RESULT_FAILURE
        }

        override fun cancel(jobId: Int) {
            // Rejecting scheduler exposes only schedule failure behavior.
        }

        override fun cancelAll() {
            // Rejecting scheduler exposes only schedule failure behavior.
        }

        override fun getAllPendingJobs(): MutableList<JobInfo> {
            return mutableListOf()
        }

        override fun getPendingJob(jobId: Int): JobInfo? {
            return null
        }
    }
}
