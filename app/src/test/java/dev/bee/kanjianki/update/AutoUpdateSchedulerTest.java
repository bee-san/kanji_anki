package dev.bee.kanjianki.update;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;

import dev.bee.kanjianki.updatecore.AutoUpdateSchedulePolicy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class AutoUpdateSchedulerTest {
    @Test
    public void disabledAutoUpdatesCancelUniqueWorkWithoutScheduling() {
        Backend backend = new Backend();

        AutoUpdateScheduler.schedule(false, backend);

        assertEquals(AutoUpdateSchedulePolicy.UNIQUE_WORK_NAME, backend.cancelledName);
        assertFalse(backend.enqueued);
    }

    @Test
    public void enabledAutoUpdatesScheduleDailyNetworkConstrainedWork() {
        Backend backend = new Backend();

        AutoUpdateScheduler.schedule(true, backend);

        assertTrue(backend.enqueued);
        assertEquals(AutoUpdateSchedulePolicy.UNIQUE_WORK_NAME, backend.enqueuedName);
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, backend.policy);
        assertNotNull(backend.request);
        assertEquals(NetworkType.CONNECTED, backend.request.getWorkSpec().constraints.getRequiredNetworkType());
        assertEquals(AutoUpdateSchedulePolicy.INTERVAL_MILLIS, backend.request.getWorkSpec().intervalDuration);
        assertEquals(AutoUpdateSchedulePolicy.FLEX_MILLIS, backend.request.getWorkSpec().flexDuration);
    }

    private static final class Backend implements AutoUpdateScheduler.SchedulerBackend {
        boolean enqueued;
        String enqueuedName;
        String cancelledName;
        ExistingPeriodicWorkPolicy policy;
        PeriodicWorkRequest request;

        @Override
        public void enqueueUniquePeriodicWork(String uniqueWorkName, ExistingPeriodicWorkPolicy policy, PeriodicWorkRequest request) {
            enqueued = true;
            enqueuedName = uniqueWorkName;
            this.policy = policy;
            this.request = request;
        }

        @Override
        public void cancelUniqueWork(String uniqueWorkName) {
            cancelledName = uniqueWorkName;
        }
    }
}
