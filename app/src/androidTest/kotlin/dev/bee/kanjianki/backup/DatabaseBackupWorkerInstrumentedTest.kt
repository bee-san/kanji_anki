package dev.bee.kanjianki.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.futures.SettableFuture
import androidx.work.impl.utils.taskexecutor.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executor

private const val DATABASE_NAME = "kanji_anki_simple.db"

private fun completedVoidFuture(): SettableFuture<Void> {
    return SettableFuture.create<Void>().apply {
        set(null)
    }
}

@RunWith(AndroidJUnit4::class)
class DatabaseBackupWorkerInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        deleteDatabaseFiles()
        deleteRecursively(File(context.filesDir, "backups"))
        deleteRecursively(File(context.filesDir, "copy-failure"))
    }

    @After
    fun tearDown() {
        deleteDatabaseFiles()
        deleteRecursively(File(context.filesDir, "backups"))
        deleteRecursively(File(context.filesDir, "copy-failure"))
    }

    @Test
    fun androidEnvironmentUsesApplicationDatabaseAndFilesDirectories() {
        val environment = DatabaseBackupWorker.androidEnvironment(context)

        assertEquals(
            context.getDatabasePath(DATABASE_NAME).absolutePath,
            environment.databasePath(DATABASE_NAME).absolutePath,
        )
        assertEquals(context.filesDir.absolutePath, environment.filesDir().absolutePath)
    }

    @Test
    fun checkpointRunsWalCheckpointAndClosesValidDatabase() {
        val db = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null)
        db.execSQL("CREATE TABLE IF NOT EXISTS probe(id INTEGER PRIMARY KEY)")
        db.close()
        val dbFile = context.getDatabasePath(DATABASE_NAME)

        DatabaseBackupWorker.checkpoint(dbFile)

        val reopened = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        )
        try {
            reopened.execSQL("INSERT INTO probe(id) VALUES(1)")
            assertTrue(dbFile.isFile)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun workerConstructorAndDoWorkUseAndroidEnvironment() {
        val db = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null)
        db.execSQL("CREATE TABLE IF NOT EXISTS probe(id INTEGER PRIMARY KEY)")
        db.execSQL("INSERT INTO probe(id) VALUES(1)")
        db.close()

        val worker = DatabaseBackupWorker(context, workerParameters())
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val backupDir = File(context.filesDir, "backups")
        val backups = backupDir.listFiles { _, name ->
            name.startsWith("kanji_anki_simple_") && name.endsWith(".db")
        }
        assertTrue(backups != null && backups.size == 1)
        assertTrue(backups!![0].isFile)
    }

    @Test
    fun backupDatabaseLogsAndroidWarningsWhenFailedCopyCannotBeDeleted() {
        val source = context.getDatabasePath(DATABASE_NAME)
        FileOutputStream(source).use { output ->
            output.write(byteArrayOf(1, 2, 3))
        }
        val filesDir = File(context.filesDir, "copy-failure")
        assertTrue(filesDir.mkdirs())

        val result = DatabaseBackupWorker.backupDatabase(
            source,
            filesDir,
            1_778_832_000_000L,
            { dbFile -> DatabaseBackupWorker.checkpoint(dbFile) },
            { _, dst ->
                assertTrue(dst.mkdirs())
                FileOutputStream(File(dst, "partial")).use { output ->
                    output.write(byteArrayOf(4, 5, 6))
                }
                throw IOException("copy failed")
            },
        )

        assertTrue(result is ListenableWorker.Result.Failure)
        val incomplete = File(filesDir, "backups").listFiles { _, name ->
            name.startsWith("kanji_anki_simple_") && name.endsWith(".db")
        }
        assertTrue(incomplete != null && incomplete.size == 1)
        assertTrue(incomplete!![0].isDirectory)
    }

    private fun deleteDatabaseFiles() {
        context.deleteDatabase(DATABASE_NAME)
        deleteIfExists(context.getDatabasePath("$DATABASE_NAME-wal"))
        deleteIfExists(context.getDatabasePath("$DATABASE_NAME-shm"))
    }

    private fun deleteIfExists(file: File) {
        if (file.exists()) {
            assertTrue(file.delete())
        }
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) {
            return
        }
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> deleteRecursively(child) }
        }
        assertTrue(file.delete())
    }

    private fun workerParameters(): WorkerParameters {
        val directExecutor = Executor { command -> command.run() }
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
                ): ListenableWorker? {
                    return null
                }
            },
            NoOpProgressUpdater,
            NoOpForegroundUpdater,
        )
    }

    private fun taskExecutor(directExecutor: Executor): TaskExecutor {
        val serialExecutor = object : SerialExecutor {
            override fun execute(command: Runnable) {
                directExecutor.execute(command)
            }

            override fun hasPendingTasks(): Boolean {
                return false
            }
        }
        return object : TaskExecutor {
            override fun getMainThreadExecutor(): Executor {
                return directExecutor
            }

            override fun getSerialTaskExecutor(): SerialExecutor {
                return serialExecutor
            }
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
}
