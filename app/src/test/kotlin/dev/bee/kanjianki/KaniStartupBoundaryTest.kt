package dev.bee.kanjianki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.futures.SettableFuture
import androidx.work.impl.utils.taskexecutor.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import dev.bee.kanjianki.backup.DatabaseBackupWorker
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.fsrs.FsrsFitExecutionGate
import dev.bee.kanjianki.fsrs.FsrsFitWorker
import dev.bee.kanjianki.sync.AutoSyncRetryWorker
import dev.bee.kanjianki.update.AutoUpdateWorker
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniStartupBoundaryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        FsrsFitExecutionGate.release()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun workManagerConfigurationDoesNotConstructTheProcessContainer() {
        val application = KaniApplication()

        val configuration = application.workManagerConfiguration

        assertTrue(configuration.workerFactory is KaniWorkerFactory)
        assertThrows(IllegalStateException::class.java) { application.container }
    }

    @Test
    fun workerConstructionDoesNotResolveContainerOrOpenSqlite() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        val providerCalls = AtomicInteger()
        val factory = KaniWorkerFactory(
            AndroidContainerProvider {
                providerCalls.incrementAndGet()
                error("restore gate has not opened")
            },
        )
        val parameters = workerParameters()

        val workers = listOf(
            factory.createWorker(context, DatabaseBackupWorker::class.java.name, parameters),
            factory.createWorker(context, FsrsFitWorker::class.java.name, parameters),
            factory.createWorker(context, AutoSyncRetryWorker::class.java.name, parameters),
            factory.createWorker(context, AutoUpdateWorker::class.java.name, parameters),
        )

        assertTrue(workers[0] is DatabaseBackupWorker)
        assertTrue(workers[1] is FsrsFitWorker)
        assertTrue(workers[2] is AutoSyncRetryWorker)
        assertTrue(workers[3] is AutoUpdateWorker)
        assertEquals(0, providerCalls.get())
        assertFalse(context.getDatabasePath(LocalStoreSchema.DB_NAME).exists())

        assertThrows(IllegalStateException::class.java) {
            (workers[1] as FsrsFitWorker).doWork()
        }
        assertEquals(1, providerCalls.get())
        assertFalse(context.getDatabasePath(LocalStoreSchema.DB_NAME).exists())
    }

    @Test
    fun unknownWorkersFallBackWithoutResolvingTheContainer() {
        val providerCalls = AtomicInteger()
        val factory = KaniWorkerFactory(
            AndroidContainerProvider {
                providerCalls.incrementAndGet()
                error("must stay lazy")
            },
        )

        assertNull(factory.createWorker(context, "example.UnknownWorker", workerParameters()))
        assertEquals(0, providerCalls.get())
    }

    private fun workerParameters(): WorkerParameters {
        val directExecutor = Executor(Runnable::run)
        return WorkerParameters(
            UUID.randomUUID(),
            Data.EMPTY,
            emptySet(),
            WorkerParameters.RuntimeExtras(),
            0,
            0,
            directExecutor,
            EmptyCoroutineContext,
            taskExecutor(directExecutor),
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? = null
            },
            NoOpProgressUpdater,
            NoOpForegroundUpdater,
        )
    }

    private fun taskExecutor(directExecutor: Executor): TaskExecutor {
        val serialExecutor = object : SerialExecutor {
            override fun execute(command: Runnable) = directExecutor.execute(command)

            override fun hasPendingTasks(): Boolean = false
        }
        return object : TaskExecutor {
            override fun getMainThreadExecutor(): Executor = directExecutor

            override fun getSerialTaskExecutor(): SerialExecutor = serialExecutor
        }
    }

    private object NoOpProgressUpdater : ProgressUpdater {
        override fun updateProgress(
            context: Context,
            id: UUID,
            data: Data,
        ) = completedVoidFuture()
    }

    private object NoOpForegroundUpdater : ForegroundUpdater {
        override fun setForegroundAsync(
            context: Context,
            id: UUID,
            foregroundInfo: androidx.work.ForegroundInfo,
        ) = completedVoidFuture()
    }

    private companion object {
        fun completedVoidFuture(): SettableFuture<Void> =
            SettableFuture.create<Void>().apply { set(null) }
    }
}
