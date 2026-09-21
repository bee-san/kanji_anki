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
import dev.bee.kanjianki.host.KaniLaunchIntents
import dev.bee.kanjianki.platform.PlatformFileAccess
import dev.bee.kanjianki.platform.PlatformFileReference
import dev.bee.kanjianki.backup.ValidatedBackup
import dev.bee.kanjianki.core.AnkiKanjiInventory
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.core.MissingKanjiExportReceipt
import dev.bee.kanjianki.core.MissingKanjiPreferences
import dev.bee.kanjianki.testing.DeviceRisk
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
@DeviceRisk
class BackupAndRestoreInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() {
        KaniTestDatabase.delete(context)
        BackupRestoreStager.restoreDir(context.filesDir).deleteRecursively()
        File(context.cacheDir, "backup-export").deleteRecursively()
        File(context.cacheDir, "backup-export-test.db.gz").delete()
    }

    // `panelRendersAndExportProducesStandaloneSqliteGzip` was removed with the
    // `MainActivity*` chain: it rendered the old Settings backup panel, which no longer
    // exists. Export is covered without a screen by `AndroidBackupExportTest`, and the two
    // restore tests below are store-level, which is where the atomic-publication contract
    // actually lives.
    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun applicationStartupHookAppliesStagedOlderFixtureBeforeActivityOpen() {
        KaniTestDatabase.delete(context)
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
        KaniTestDatabase.delete(context)
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

        // Read the published database directly rather than through an activity's `store`.
        // The restore's contract is about what startup published, not about which screen
        // happens to open afterwards, and a fresh `LocalStore` opens exactly the file the
        // atomic replacement left behind. This also drops the last reason for this test to
        // know an Activity type at all.
        LocalStore(context).use { store ->
            store.readableDatabase.rawQuery(
                "SELECT value FROM settings WHERE key = 'restore_probe'",
                null,
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("visible", cursor.getString(0))
            }
            store.readableDatabase.rawQuery("PRAGMA user_version", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(LocalStoreSchema.DB_VERSION, cursor.getInt(0))
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 30)
    fun missingKanjiStateSurvivesSnapshotAndRestoreRoundTrip() {
        KaniTestDatabase.delete(context)
        val fixture = File(BackupRestoreStager.restoreDir(context.filesDir), "missing-kanji.db")
        fixture.parentFile!!.mkdirs()
        LocalStore(context).use { store ->
            val missing = store.missingKanjiStore()
            missing.publishInventory(
                AnkiKanjiInventory(
                    literals = setOf("水", "火"),
                    notesScanned = 12,
                    fieldsScanned = 40,
                    skippedNotes = 1,
                    modelCount = 2,
                    malformedRowWarning = null,
                ),
                startedAt = 100,
                completedAt = 200,
                providerFingerprint = "authority=fixture;spec=2",
            )
            missing.savePreferences(
                MissingKanjiPreferences(
                    preset = MissingKanjiPreferences.PRESET_CUSTOM,
                    range = MissingKanjiFrequencyRange(50, 2_500, includeUnranked = true),
                    searchQuery = "water",
                ),
            )
            missing.addManualSources(
                listOf(
                    MissingKanjiCandidate(
                        literal = "水",
                        meanings = listOf("water"),
                        onReadings = listOf("スイ"),
                        kunReadings = listOf("みず"),
                        jitenRank = 12,
                    ),
                ),
                nowMillis = 300,
            )
            missing.recordExportReceipts(
                listOf(MissingKanjiExportReceipt("水", "anki:fixture", 400, 500)),
            )
            store.snapshotInto(fixture)
        }

        KaniTestDatabase.delete(context)
        assertTrue(
            BackupRestoreStager.stage(
                ValidatedBackup(fixture, "missing-kanji.db.gz"),
                context.filesDir,
                Build.VERSION.SDK_INT,
            ),
        )
        val application = context.applicationContext as KaniApplication
        assertEquals(
            StagedRestoreApplier.Result.APPLIED,
            application.applyPendingRestoreAtStartup(),
        )

        LocalStore(context).use { restored ->
            val missing = restored.missingKanjiStore()
            assertEquals(setOf("水", "火"), missing.inventoryState().published?.literals)
            assertEquals(12, missing.inventoryState().published?.scan?.notesScanned)
            assertEquals("water", missing.loadPreferences().searchQuery)
            assertEquals("水", missing.manualSources().single().candidate.literal)
            assertEquals(
                500L,
                missing.exportReceipts("anki:fixture").getValue("水").externalNoteId,
            )
        }
    }
}
