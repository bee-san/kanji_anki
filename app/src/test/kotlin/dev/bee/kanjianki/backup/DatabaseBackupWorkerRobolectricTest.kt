package dev.bee.kanjianki.backup

import android.content.Context
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
    }

    @Test
    fun androidEnvironmentSnapshotsRealDatabaseThroughLocalStore() {
        // Create a real database so LocalStore.snapshotInto (VACUUM INTO / fallback)
        // runs against actual content via the Android backup environment.
        LocalStore(context).use { store ->
            store.writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS probe(id INTEGER PRIMARY KEY)")
            store.writableDatabase.execSQL("INSERT INTO probe(id) VALUES (1)")
        }

        val result = DatabaseBackupWorker.doWork(
            DatabaseBackupWorker.androidEnvironment(context),
            1_778_832_000_000L,
        )

        assertTrue(result is ListenableWorker.Result.Success)
        val backups = File(context.filesDir, "backups").listFiles { _, name ->
            name.startsWith("kanji_anki_simple_") && name.endsWith(".db.gz")
        }
        assertTrue(backups != null && backups.isNotEmpty())
        assertTrue(backups!![0].length() > 0)
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
