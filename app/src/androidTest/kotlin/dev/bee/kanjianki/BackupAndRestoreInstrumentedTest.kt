package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.bee.kanjianki.backup.BackupExportOperations
import dev.bee.kanjianki.backup.BackupExportPreparation
import dev.bee.kanjianki.backup.BackupRestoreStager
import dev.bee.kanjianki.backup.StagedRestoreApplier
import dev.bee.kanjianki.backup.UriStreams
import dev.bee.kanjianki.backup.ValidatedBackup
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupAndRestoreInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        BackupRestoreStager.restoreDir(context.filesDir).deleteRecursively()
        File(context.cacheDir, "backup-export").deleteRecursively()
        File(context.cacheDir, "backup-export-test.db.gz").delete()
    }

    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun panelRendersAndExportProducesStandaloneSqliteGzip() {
        val panel = SettingsBackupPanelModel(
            title = "Backup & restore",
            body = "Export or restore Kani data.",
            lastBackupLine = "Last automatic backup: not yet",
            archiveCountLine = "0 automatic backups kept on this device",
            exportLabel = "Export now",
            onExport = Runnable {},
            restoreLabel = "Restore from backup…",
            onRestore = Runnable {},
        )
        composeRule.setContent {
            SettingsSubmenuScreen(
                SettingsSubmenuScreenModel(
                    "Home",
                    Runnable {},
                    "Back",
                    Runnable {},
                    "Automation",
                    "Manage automation.",
                    listOf(panel),
                ),
            )
        }
        composeRule.onNodeWithTag("settings-panel-backup").assertIsDisplayed()
        composeRule.onNodeWithText("Export now").assertIsDisplayed()

        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        val dbFile = context.getDatabasePath(LocalStoreSchema.DB_NAME)
        val preparation = LocalStore(context).use { store ->
            store.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO settings(key, value, updated_at) VALUES ('export_probe', 'yes', 1)",
            )
            BackupExportOperations.prepare(context.cacheDir, dbFile, 1_778_832_000_000L) { _, destination ->
                store.snapshotInto(destination)
            }
        }
        assertTrue(preparation is BackupExportPreparation.Ready)
        val prepared = (preparation as BackupExportPreparation.Ready).export
        val destination = File(context.cacheDir, "backup-export-test.db.gz")
        val copied = BackupExportOperations.copyToUri(
            prepared,
            Uri.fromFile(destination),
            UriStreams { FileOutputStream(destination) },
        )
        assertTrue(copied.success)
        val header = GZIPInputStream(destination.inputStream()).use { gzip ->
            ByteArray(16).also { bytes ->
                var offset = 0
                while (offset < bytes.size) {
                    val read = gzip.read(bytes, offset, bytes.size - offset)
                    assertTrue(read > 0)
                    offset += read
                }
            }
        }
        assertEquals("SQLite format 3\u0000", header.toString(Charsets.US_ASCII))
    }

    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun applicationStartupHookAppliesStagedOlderFixtureBeforeActivityOpen() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        val fixture = File(BackupRestoreStager.restoreDir(context.filesDir), "fixture.db")
        fixture.parentFile!!.mkdirs()
        // Build a complete current schema, plant a sentinel, then lower user_version so the
        // next LocalStore open must execute the v29 and v30 migration paths.
        val liveDatabase = context.getDatabasePath(LocalStoreSchema.DB_NAME)
        LocalStore(context).use { store ->
            store.writableDatabase.execSQL(
                "INSERT OR REPLACE INTO settings(key, value, updated_at) VALUES ('restore_probe', 'visible', 1)",
            )
            store.snapshotInto(fixture)
        }
        SQLiteDatabase.openDatabase(fixture.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("PRAGMA user_version = 28")
        }
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        assertTrue(
            BackupRestoreStager.stage(
                ValidatedBackup(fixture, "known-fixture.db.gz"),
                context.filesDir,
                Build.VERSION.SDK_INT,
            ),
        )
        val application = context.applicationContext as KaniApplication
        assertEquals(
            StagedRestoreApplier.Result.APPLIED,
            application.applyPendingRestoreAtStartup(),
        )
        assertFalse(BackupRestoreStager.stagedFile(context.filesDir).exists())
        assertFalse(BackupRestoreStager.markerFile(context.filesDir).exists())

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_SETTINGS_ROUTE)
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                activity.store.readableDatabase.rawQuery(
                    "SELECT value FROM settings WHERE key = 'restore_probe'",
                    null,
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("visible", cursor.getString(0))
                }
                activity.store.readableDatabase.rawQuery("PRAGMA user_version", null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(LocalStoreSchema.DB_VERSION, cursor.getInt(0))
                }
            }
        }
    }
}
