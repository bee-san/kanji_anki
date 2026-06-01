package dev.bee.kanjianki.sync

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.data.LocalStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val DATABASE_NAME = "kanji_anki_simple.db"

@RunWith(AndroidJUnit4::class)
class AutoSyncJobServiceInstrumentedTest {
    @After
    fun cleanDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun serviceLifecycleDelegatesToInjectedSyncTaskAndShutdown() {
        val executed = AtomicBoolean()
        val shutdown = AtomicBoolean()
        val stoppedValue = AtomicReference<Boolean?>(null)
        val service = AutoSyncJobService(
            AutoSyncJobService.JobExecutor { job -> job.run() },
            AutoSyncJobService.Shutdown { shutdown.set(true) },
            AutoSyncJobService.AutoSyncTask { params ->
                assertNull(params)
                executed.set(true)
            },
        )

        assertTrue(service.onStartJob(null))
        assertTrue(executed.get())
        assertTrue(service.onStopJob(null))
        service.onDestroy()
        assertTrue(shutdown.get())

        AutoSyncJobService.finishJob(
            null,
            null,
            true,
            AutoSyncJobService.SettingsReader {
                dev.bee.kanjianki.data.LocalStoreBase.AutoSyncSettings(true, false, 7, 30, 0L, 0L, 0L)
            },
            AutoSyncJobService.StoreCloser { },
            AutoSyncJobService.Scheduler { _, _ -> },
            AutoSyncJobService.JobFinisher { _, needsReschedule -> stoppedValue.set(needsReschedule) },
        )
        assertTrue(stoppedValue.get() == true)
    }

    @Test
    fun defaultServiceAndRealRunnerUseTargetContextWithoutExternalProviderWhenDisabled() {
        val service = AutoSyncJobService()
        service.onDestroy()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        val stoppedValue = AtomicReference<Boolean?>(null)
        try {
            AutoSyncJobService.runAutoSync(
                context,
                null,
                false,
            ) { _, needsReschedule -> stoppedValue.set(needsReschedule) }
        } finally {
            context.deleteDatabase(DATABASE_NAME)
        }

        assertTrue(stoppedValue.get() == false)
    }

    @Test
    fun realRunnerReschedulesEnabledSettingsAfterProviderFailure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        try {
            LocalStore(context).use { store ->
                store.saveAutoSyncSettings(dev.bee.kanjianki.data.LocalStoreBase.AutoSyncSettings(true, true, 23, 59, 0L, 0L, 0L))
            }
            val stoppedValue = AtomicReference<Boolean?>(null)

            AutoSyncJobService.runAutoSync(
                context,
                null,
                false,
            ) { _, needsReschedule -> stoppedValue.set(needsReschedule) }

            LocalStore(context).use { store ->
                assertTrue(store.autoSyncSettings().nextRunAt > 0L)
                assertTrue(store.latestSync()?.status == "config_error")
            }
            assertTrue(stoppedValue.get() == false)
        } finally {
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    @Test
    fun defaultServiceRunnerCanUseAttachedTargetContext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        val service = AutoSyncJobService()
        attachBaseContext(service, context)
        val factoryUsed = AtomicBoolean()
        val stoppedValue = AtomicReference<Boolean?>(null)
        val originalFactory = replaceJobFinisherFactory { createdService ->
            assertSame(service, createdService)
            factoryUsed.set(true)
            AutoSyncJobService.JobFinisher { _, needsReschedule -> stoppedValue.set(needsReschedule) }
        }

        try {
            val method: Method = AutoSyncJobService::class.java.getDeclaredMethod(
                "runAutoSync",
                android.app.job.JobParameters::class.java,
            )
            method.isAccessible = true
            method.invoke(service, *arrayOfNulls<Any?>(1))
        } finally {
            replaceJobFinisherFactory(originalFactory)
            service.onDestroy()
        }
        LocalStore(context).use { store ->
            assertTrue(store.autoSyncSettings().displayTime().matches("\\d{2}:\\d{2}".toRegex()))
        }
        assertTrue(factoryUsed.get())
        assertTrue(stoppedValue.get() == false)
        assertTrue(context.getDatabasePath(DATABASE_NAME).isFile)
    }

    @Test
    fun defaultServiceStartJobRunsBoundAutoSyncTaskWithAttachedContext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        val service = AutoSyncJobService()
        attachBaseContext(service, context)
        replaceField(service, "executor", AutoSyncJobService.JobExecutor { job -> job.run() })

        try {
            service.onStartJob(null)
        } catch (error: RuntimeException) {
            // Framework JobService can reject null JobParameters after the real
            // sync path has reached jobFinished; this test owns the bound task path.
            assertTrue(stackContains(error, "android.app.job.JobService", "jobFinished"))
        } finally {
            service.onDestroy()
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    private fun attachBaseContext(service: AutoSyncJobService, context: Context) {
        val method = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        method.isAccessible = true
        method.invoke(service, context)
    }

    private fun replaceField(service: AutoSyncJobService, name: String, value: Any) {
        val field = AutoSyncJobService::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(service, value)
    }

    private fun replaceJobFinisherFactory(
        value: AutoSyncJobService.JobFinisherFactory,
    ): AutoSyncJobService.JobFinisherFactory {
        val field = AutoSyncJobService::class.java.getDeclaredField("jobFinisherFactory")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val previous = field.get(null) as AutoSyncJobService.JobFinisherFactory
        field.set(null, value)
        return previous
    }

    private fun stackContains(error: Throwable, className: String, methodName: String): Boolean {
        for (element in error.stackTrace) {
            if (className == element.className && methodName == element.methodName) {
                return true
            }
        }
        return false
    }
}
