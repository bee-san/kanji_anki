package dev.bee.kanjianki.backup;

import android.content.Context;
import android.content.ContextWrapper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class DatabaseBackupSchedulerInstrumentedTest {
    @Test
    public void cancelPublicMethodCancelsDailyBackupWork() throws Exception {
        Context appContext = ApplicationProvider.getApplicationContext();

        DatabaseBackupScheduler.schedule(appContext);
        DatabaseBackupScheduler.cancel(appContext);

        List<WorkInfo> workInfos = WorkManager.getInstance(appContext)
                .getWorkInfosForUniqueWork("kani_daily_db_backup")
                .get(5, TimeUnit.SECONDS);
        assertTrue(workInfos.isEmpty() || workInfos.get(0).getState() == WorkInfo.State.CANCELLED);
    }

    @Test
    public void cancelUsesApplicationContextFromWrappedContext() throws Exception {
        Context appContext = ApplicationProvider.getApplicationContext();
        Context outerContext = new ContextWrapper(appContext) {
            @Override
            public Context getApplicationContext() {
                return appContext;
            }
        };

        DatabaseBackupScheduler.schedule(outerContext);
        DatabaseBackupScheduler.cancel(outerContext);

        List<WorkInfo> workInfos = WorkManager.getInstance(appContext)
                .getWorkInfosForUniqueWork("kani_daily_db_backup")
                .get(5, TimeUnit.SECONDS);
        assertTrue(workInfos.isEmpty() || workInfos.get(0).getState() == WorkInfo.State.CANCELLED);
    }
}
