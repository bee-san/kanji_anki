package dev.bee.kanjianki.backup

import dev.bee.kanjianki.core.DatabaseBackupPolicy
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StagedRestoreApplierTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun noOpWhenRestoreDirectoryWasNeverCreated() {
        val root = temp.newFolder("never-staged")
        val filesDir = File(root, "files").apply { assertTrue(mkdirs()) }
        val database = File(root, "live.db").apply { writeText("current") }

        val result = StagedRestoreApplier.applyOrThrow(
            filesDir,
            database,
            NOW,
            snapshotter = { _, _ -> throw AssertionError("no snapshot on no-op") },
        )

        assertEquals(StagedRestoreApplier.Result.NO_OP, result)
        assertEquals("current", database.readText())
        assertFalse(BackupRestoreStager.restoreDir(filesDir).exists())
    }

    @Test
    fun fullApplyCreatesSafetySnapshotReplacesDatabaseAndDeletesSidecars() {
        val fixture = fixture("full")

        val result = apply(fixture)

        assertEquals(StagedRestoreApplier.Result.APPLIED, result)
        assertArrayEquals(fixture.restoredBytes, fixture.database.readBytes())
        assertFalse(BackupRestoreStager.stagedFile(fixture.filesDir).exists())
        assertFalse(BackupRestoreStager.markerFile(fixture.filesDir).exists())
        assertFalse(BackupRestoreStager.restoreDir(fixture.filesDir).exists())
        assertFalse(fixture.wal.exists())
        assertFalse(fixture.shm.exists())
        val safety = DatabaseBackupPolicy.backupFile(fixture.filesDir, NOW)
        assertTrue(safety.isFile)
        assertArrayEquals(fixture.originalBytes, GZIPInputStream(FileInputStream(safety)).use { it.readBytes() })
    }

    @Test
    fun rerunAfterCrashAtEveryStepCompletesIdempotently() {
        for (step in StagedRestoreApplier.Step.entries) {
            val fixture = fixture("crash-${step.name}")
            var crashed = false
            try {
                apply(fixture, StagedRestoreApplier.StepHook { completed ->
                    if (completed == step) throw SimulatedCrash()
                })
            } catch (_: SimulatedCrash) {
                crashed = true
            }
            assertTrue("did not inject crash after $step", crashed)

            val rerun = apply(fixture)

            assertTrue(
                "unexpected rerun result after $step: $rerun",
                rerun == StagedRestoreApplier.Result.APPLIED || rerun == StagedRestoreApplier.Result.NO_OP,
            )
            assertArrayEquals("restored bytes after $step", fixture.restoredBytes, fixture.database.readBytes())
            assertFalse("staged after $step", BackupRestoreStager.stagedFile(fixture.filesDir).exists())
            assertFalse("marker after $step", BackupRestoreStager.markerFile(fixture.filesDir).exists())
            assertFalse("restore dir after $step", BackupRestoreStager.restoreDir(fixture.filesDir).exists())
            assertFalse("wal after $step", fixture.wal.exists())
            assertFalse("shm after $step", fixture.shm.exists())
            assertTrue("safety snapshot after $step", DatabaseBackupPolicy.backupFile(fixture.filesDir, NOW).isFile)
        }
    }

    @Test
    fun noOpWhenNothingIsStagedDoesNotTouchCurrentDatabase() {
        val filesDir = temp.newFolder("files-no-op")
        val database = temp.newFile("live-no-op.db")
        database.writeText("current")
        val restoreDir = BackupRestoreStager.restoreDir(filesDir).apply { assertTrue(mkdirs()) }
        val orphan = File(restoreDir, "abandoned${BackupRestoreStager.VALIDATING_SUFFIX}").apply {
            writeText("orphan")
        }

        val result = StagedRestoreApplier.applyOrThrow(
            filesDir,
            database,
            NOW,
            snapshotter = { _, _ -> throw AssertionError("no snapshot on no-op") },
        )

        assertEquals(StagedRestoreApplier.Result.NO_OP, result)
        assertEquals("current", database.readText())
        assertFalse(DatabaseBackupPolicy.backupDir(filesDir).exists())
        assertFalse(orphan.exists())
        assertFalse(restoreDir.exists())
    }

    private fun fixture(name: String): Fixture {
        val root = temp.newFolder(name)
        val filesDir = File(root, "files").apply { assertTrue(mkdirs()) }
        val databaseDir = File(root, "databases").apply { assertTrue(mkdirs()) }
        val database = File(databaseDir, DatabaseBackupPolicy.DB_NAME)
        val original = "old-$name".repeat(200).toByteArray()
        val restored = "new-$name".repeat(200).toByteArray()
        database.writeBytes(original)
        val restoreDir = BackupRestoreStager.restoreDir(filesDir).apply { assertTrue(mkdirs()) }
        val validatedFile = File(restoreDir, "validated.db").apply { writeBytes(restored) }
        assertTrue(BackupRestoreStager.stage(ValidatedBackup(validatedFile, "$name.db.gz"), filesDir, NOW))
        val wal = File(database.absolutePath + "-wal").apply { writeText("stale wal") }
        val shm = File(database.absolutePath + "-shm").apply { writeText("stale shm") }
        return Fixture(filesDir, database, wal, shm, original, restored)
    }

    private fun apply(
        fixture: Fixture,
        hook: StagedRestoreApplier.StepHook = StagedRestoreApplier.StepHook {},
    ): StagedRestoreApplier.Result {
        return StagedRestoreApplier.applyOrThrow(
            fixture.filesDir,
            fixture.database,
            NOW,
            snapshotter = { source, destination -> source.copyTo(destination, overwrite = true) },
            stepHook = hook,
        )
    }

    private data class Fixture(
        val filesDir: File,
        val database: File,
        val wal: File,
        val shm: File,
        val originalBytes: ByteArray,
        val restoredBytes: ByteArray,
    )

    private class SimulatedCrash : RuntimeException()

    private companion object {
        const val NOW = 1_778_832_000_000L
    }
}
