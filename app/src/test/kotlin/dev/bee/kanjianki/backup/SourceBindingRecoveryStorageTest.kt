package dev.bee.kanjianki.backup

import dev.bee.kanjianki.core.DatabaseBackupPolicy
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SourceBindingRecoveryStorageTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun unsupportedAndroidVersionsExposeNoRecoveryOperations() {
        val fixture = fixture(apiLevel = 29)
        var currentSnapshotCalled = false
        var freshSnapshotCalled = false
        val storage = fixture.storage(
            currentSnapshotter = { currentSnapshotCalled = true },
            freshSnapshotter = { freshSnapshotCalled = true },
        )

        assertFalse(storage.operationsAllowed())
        assertFalse(storage.createSafetyBackup(NOW))
        assertFalse(storage.stageFreshProfile())
        assertFalse(currentSnapshotCalled)
        assertFalse(freshSnapshotCalled)
        assertFalse(BackupRestoreStager.restoreDir(fixture.filesDir).exists())
    }

    @Test
    fun safetyBackupUsesWalSafeSnapshotAndPublishesCompressedArchive() {
        val fixture = fixture()
        val storage = fixture.storage(
            currentSnapshotter = { destination ->
                destination.writeText("consistent WAL snapshot")
            },
        )

        assertTrue(storage.createSafetyBackup(NOW))

        val archive = DatabaseBackupPolicy.backupFile(fixture.filesDir, NOW)
        assertTrue(archive.isFile)
        val restored = GZIPInputStream(archive.inputStream()).use { input ->
            input.readBytes().decodeToString()
        }
        assertEquals("consistent WAL snapshot", restored)
    }

    @Test
    fun safetyBackupFailurePublishesNoArchive() {
        val fixture = fixture()
        val storage = fixture.storage(
            currentSnapshotter = { destination ->
                destination.writeText("incomplete")
                throw IOException("snapshot failed")
            },
        )

        assertFalse(storage.createSafetyBackup(NOW))
        assertFalse(DatabaseBackupPolicy.backupFile(fixture.filesDir, NOW).exists())
    }

    @Test
    fun freshProfilePreparationStagesOnlyAfterSafetyBackupSucceeds() {
        val fixture = fixture()
        var freshSnapshotCalls = 0
        val storage = fixture.storage(
            currentSnapshotter = { throw IOException("backup failed") },
            freshSnapshotter = {
                freshSnapshotCalls += 1
                it.writeText("fresh profile")
            },
        )

        val result = storage.prepareFreshProfile(NOW)

        assertEquals(FreshProfilePreparationResult.BACKUP_FAILED, result)
        assertEquals(0, freshSnapshotCalls)
        assertFalse(BackupRestoreStager.stagedFile(fixture.filesDir).exists())
    }

    @Test
    fun freshProfilePreparationStagesDurableDatabaseAfterBackup() {
        val fixture = fixture()
        val storage = fixture.storage(
            currentSnapshotter = { it.writeText("current profile") },
            freshSnapshotter = { it.writeText("fresh profile") },
        )

        val result = storage.prepareFreshProfile(NOW)

        assertEquals(FreshProfilePreparationResult.STAGED, result)
        assertTrue(DatabaseBackupPolicy.backupFile(fixture.filesDir, NOW).isFile)
        assertEquals(
            "fresh profile",
            BackupRestoreStager.stagedFile(fixture.filesDir).readText(),
        )
        assertFalse(BackupRestoreStager.markerFile(fixture.filesDir).exists())
        assertTrue(fixture.cacheDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun freshSnapshotFailurePreservesCompletedSafetyBackupWithoutStaging() {
        val fixture = fixture()
        val storage = fixture.storage(
            currentSnapshotter = { it.writeText("current profile") },
            freshSnapshotter = { throw IOException("fresh snapshot failed") },
        )

        val result = storage.prepareFreshProfile(NOW)

        assertEquals(FreshProfilePreparationResult.STAGING_FAILED, result)
        assertTrue(DatabaseBackupPolicy.backupFile(fixture.filesDir, NOW).isFile)
        assertFalse(BackupRestoreStager.stagedFile(fixture.filesDir).exists())
        assertTrue(fixture.cacheDir.listFiles().orEmpty().isEmpty())
    }

    private fun fixture(apiLevel: Int = 35): Fixture {
        val root = temp.newFolder("fixture-${fixtureNumber++}")
        val database = File(root, DatabaseBackupPolicy.DB_NAME).apply {
            writeText("live database")
        }
        return Fixture(
            database = database,
            filesDir = File(root, "files").apply { assertTrue(mkdirs()) },
            cacheDir = File(root, "cache").apply { assertTrue(mkdirs()) },
            apiLevel = apiLevel,
        )
    }

    private data class Fixture(
        val database: File,
        val filesDir: File,
        val cacheDir: File,
        val apiLevel: Int,
    ) {
        fun storage(
            currentSnapshotter: (File) -> Unit = { it.writeText("current") },
            freshSnapshotter: (File) -> Unit = { it.writeText("fresh") },
        ): SourceBindingRecoveryStorage =
            SourceBindingRecoveryStorage.testing(
                databaseFile = database,
                filesDir = filesDir,
                cacheDir = cacheDir,
                apiLevel = apiLevel,
                currentSnapshotter = currentSnapshotter,
                freshSnapshotter = freshSnapshotter,
            )
    }

    private companion object {
        const val NOW = 1_778_832_000_000L
        var fixtureNumber = 0
    }
}
