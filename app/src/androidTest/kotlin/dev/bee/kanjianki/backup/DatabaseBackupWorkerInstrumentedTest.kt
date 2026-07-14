package dev.bee.kanjianki.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
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
import java.util.zip.GZIPInputStream

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
        deleteRecursively(File(context.cacheDir, "instrumented-backup.db"))
    }

    @After
    fun tearDown() {
        deleteDatabaseFiles()
        deleteRecursively(File(context.filesDir, "backups"))
        deleteRecursively(File(context.filesDir, "copy-failure"))
        deleteRecursively(File(context.cacheDir, "instrumented-backup.db"))
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
    @SdkSuppress(minSdkVersion = 30)
    fun workerProducesIntegralBackupThatIncludesCommittedWalContent() {
        // Keep the writer open so the worker must capture committed live-WAL state.
        val db = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null)
        assertTrue(db.enableWriteAheadLogging())
        db.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS probe(id INTEGER PRIMARY KEY)")
        for (id in 1..25) {
            db.execSQL("INSERT INTO probe(id) VALUES(?)", arrayOf<Any>(id))
        }

        val result = try {
            DatabaseBackupWorker(context, workerParameters()).doWork()
        } finally {
            db.close()
        }

        assertTrue(result is ListenableWorker.Result.Success)
        val backupDir = File(context.filesDir, "backups")
        val backups = backupDir.listFiles { _, name ->
            name.startsWith("kanji_anki_simple_") && name.endsWith(".db.gz")
        }
        assertTrue(backups != null && backups.size == 1)
        val backup = backups!![0]
        assertTrue(backup.isFile)

        val restoredFile = File(context.cacheDir, "instrumented-backup.db")
        GZIPInputStream(backup.inputStream()).use { gzip ->
            FileOutputStream(restoredFile).use { output -> gzip.copyTo(output) }
        }
        val restored = SQLiteDatabase.openDatabase(restoredFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            restored.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
            restored.rawQuery("SELECT COUNT(*) FROM probe", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(25, cursor.getInt(0))
            }
        } finally {
            restored.close()
        }
    }

    @Test
    fun backupDatabaseLogsAndroidWarningsWhenFailedSnapshotCannotBeDeleted() {
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
        ) { _, dst ->
            assertTrue(dst.mkdirs())
            FileOutputStream(File(dst, "partial")).use { output ->
                output.write(byteArrayOf(4, 5, 6))
            }
            throw IOException("snapshot failed")
        }

        assertTrue(result is ListenableWorker.Result.Failure)
        val incomplete = File(filesDir, "backups").listFiles { _, name ->
            name.startsWith("kanji_anki_simple_") && name.endsWith(".db.gz.tmp")
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
