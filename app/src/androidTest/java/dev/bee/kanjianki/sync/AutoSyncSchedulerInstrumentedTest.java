package dev.bee.kanjianki.sync;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobWorkItem;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.data.LocalStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class AutoSyncSchedulerInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
    }

    @After
    public void tearDown() {
        AutoSyncScheduler.cancel(context);
        context.deleteDatabase("kanji_anki_simple.db");
    }

    @Test
    public void androidBackendRecordsNextRunWhenSystemSchedulerAcceptsJob() {
        try (LocalStore store = new LocalStore(context)) {
            long now = fixedNow();
            AutoSyncScheduler.schedule(context, store, settings(), () -> now);

            assertEquals(nextRunAfter(now), store.autoSyncSettings().nextRunAt);
        }
    }

    @Test
    public void androidBackendClearsNextRunAndCancelNoopsWhenJobSchedulerIsUnavailable() {
        Context noJobScheduler = new NoJobSchedulerContext(context);
        try (LocalStore store = new LocalStore(noJobScheduler)) {
            AutoSyncScheduler.schedule(noJobScheduler, store, settings());

            assertEquals(0L, store.autoSyncSettings().nextRunAt);
        }

        AutoSyncScheduler.cancel(noJobScheduler);
    }

    @Test
    public void androidBackendClearsNextRunWhenSystemSchedulerRejectsJob() {
        Context rejectingJobScheduler = new RejectingJobSchedulerContext(context);
        try (LocalStore store = new LocalStore(rejectingJobScheduler)) {
            AutoSyncScheduler.schedule(rejectingJobScheduler, store, settings());

            assertEquals(0L, store.autoSyncSettings().nextRunAt);
        }

        AutoSyncScheduler.cancel(rejectingJobScheduler);
    }

    @Test
    public void scheduleAtClearsRecordedRunWhenBackendThrowsOnAndroidLogPath() {
        RecordingRecorder recorder = new RecordingRecorder();
        long now = fixedNow();

        AutoSyncScheduler.scheduleAt(
                recorder,
                new ThrowingBackend(),
                now + 60_000L,
                now
        );

        assertEquals(0L, recorder.nextRunAt);
    }

    private static LocalStore.AutoSyncSettings settings() {
        return new LocalStore.AutoSyncSettings(true, true, 23, 59, 0L, 0L, 0L);
    }

    private static long fixedNow() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.MAY, 15, 12, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static long nextRunAfter(long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static final class RecordingRecorder implements AutoSyncScheduler.ScheduleRecorder {
        long nextRunAt = -1L;

        @Override
        public void markAutoSyncScheduled(long nextRunAt) {
            this.nextRunAt = nextRunAt;
        }
    }

    private static final class ThrowingBackend implements AutoSyncScheduler.SchedulerBackend {
        @Override
        public boolean schedule(long minimumLatencyMillis, long overrideDeadlineMillis) {
            throw new IllegalStateException("job scheduler rejected the request");
        }

        @Override
        public void cancel() {
        }
    }

    private static final class NoJobSchedulerContext extends ContextWrapper {
        private NoJobSchedulerContext(Context base) {
            super(base);
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public Object getSystemService(String name) {
            if (Context.JOB_SCHEDULER_SERVICE.equals(name)) {
                return null;
            }
            return super.getSystemService(name);
        }
    }

    private static final class RejectingJobSchedulerContext extends ContextWrapper {
        private final JobScheduler scheduler = new RejectingJobScheduler();

        private RejectingJobSchedulerContext(Context base) {
            super(base);
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public Object getSystemService(String name) {
            if (Context.JOB_SCHEDULER_SERVICE.equals(name)) {
                return scheduler;
            }
            return super.getSystemService(name);
        }
    }

    private static final class RejectingJobScheduler extends JobScheduler {
        @Override
        public int schedule(JobInfo job) {
            return JobScheduler.RESULT_FAILURE;
        }

        @Override
        public int enqueue(JobInfo job, JobWorkItem work) {
            return JobScheduler.RESULT_FAILURE;
        }

        @Override
        public void cancel(int jobId) {
        }

        @Override
        public void cancelAll() {
        }

        @Override
        public List<JobInfo> getAllPendingJobs() {
            return Collections.emptyList();
        }

        @Override
        public JobInfo getPendingJob(int jobId) {
            return null;
        }
    }
}
