package dev.bee.kanjianki.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DatabaseBackupSchedulerInstrumentedTest {
    @Test
    fun cancelPublicMethodCancelsDailyBackupWork() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()

        DatabaseBackupScheduler.schedule(appContext)
        DatabaseBackupScheduler.cancel(appContext)

        val workInfos = WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork("kani_daily_db_backup")
            .get(5, TimeUnit.SECONDS)
        assertTrue(workInfos.isEmpty() || workInfos[0].state == WorkInfo.State.CANCELLED)
    }

    @Test
    fun cancelUsesApplicationContextFromWrappedContext() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val outerContext = object : ContextWrapper(appContext) {
            override fun getApplicationContext(): Context = appContext
        }

        DatabaseBackupScheduler.schedule(outerContext)
        DatabaseBackupScheduler.cancel(outerContext)

        val workInfos = WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork("kani_daily_db_backup")
            .get(5, TimeUnit.SECONDS)
        assertTrue(workInfos.isEmpty() || workInfos[0].state == WorkInfo.State.CANCELLED)
    }
}
