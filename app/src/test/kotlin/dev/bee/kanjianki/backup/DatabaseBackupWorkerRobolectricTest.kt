package dev.bee.kanjianki.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35])
class DatabaseBackupWorkerRobolectricTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        deleteRecursively(File(context.filesDir, "backups"))
    }

    @After
    fun tearDown() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        deleteRecursively(File(context.filesDir, "backups"))
        File(context.cacheDir, "backup-integrity.db").delete()
    }

    @Test
    fun androidEnvironmentSnapshotsCommittedContentWhileWriterRemainsOpen() {
        var result: ListenableWorker.Result? = null
        LocalStore(context).use { store ->
            assertTrue(store.writableDatabase.enableWriteAheadLogging())
            store.writableDatabase.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
            store.writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS probe(id INTEGER PRIMARY KEY)")
            store.writableDatabase.execSQL("INSERT INTO probe(id) VALUES (73421)")

            // Run through a second helper while the writer stays open. The snapshot must
            // include committed state regardless of whether SQLite has checkpointed it.
            result = DatabaseBackupWorker.doWork(
                DatabaseBackupWorker.androidEnvironment(context),
                1_778_832_000_000L,
            )
        }

        assertTrue(result is ListenableWorker.Result.Success)
        val backups = File(context.filesDir, "backups").listFiles { _, name ->
            name.startsWith("kanji_anki_simple_") && name.endsWith(".db.gz")
        }
        assertTrue(backups != null && backups.isNotEmpty())
        val restored = File(context.cacheDir, "backup-integrity.db")
        GZIPInputStream(backups!![0].inputStream()).use { gzip ->
            FileOutputStream(restored).use { gzip.copyTo(it) }
        }
        SQLiteDatabase.openDatabase(restored.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
            db.rawQuery("SELECT id FROM probe", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(73421, cursor.getInt(0))
            }
        }
    }

    @Test
    fun androidEnvironmentFailsCleanlyWhenDatabaseMissing() {
        // No database file exists -> backupDatabase returns failure without a backup.
        val result = DatabaseBackupWorker.doWork(
            DatabaseBackupWorker.androidEnvironment(context),
            1_778_832_000_000L,
        )

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }
}
