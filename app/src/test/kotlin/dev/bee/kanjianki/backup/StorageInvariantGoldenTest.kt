package dev.bee.kanjianki.backup

import dev.bee.kanjianki.KaniApplication
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import dev.bee.kanjianki.data.WalSafeSnapshotOperations
import dev.bee.kanjianki.testing.GoldenFixtureResources
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageInvariantGoldenTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun walPublicationBoundaryMatchesThePortableInvariantManifest() {
        val actual = linkedMapOf<String, String>()

        run {
            val destination = File(temp.root, "unsupported.db")
            var invoked = false
            assertThrows(IOException::class.java) {
                WalSafeSnapshotOperations.create(destination, 29) {
                    invoked = true
                    it.writeText("unexpected")
                }
            }
            actual["api-26-29"] =
                "result=io_error;writer_invoked=$invoked;destination=${existence(destination)}"
        }
        run {
            val destination = File(temp.root, "supported.db")
            WalSafeSnapshotOperations.create(destination, 30) {
                it.writeText("SQLite format 3\u0000synthetic")
            }
            actual["api-30-plus"] =
                "result=success;destination=" +
                if (destination.isFile && destination.length() > 0L) "published_non_empty" else "invalid"
        }
        run {
            val destination = File(temp.root, "partial.db")
            assertThrows(IOException::class.java) {
                WalSafeSnapshotOperations.create(destination, 35) {
                    it.writeText("partial")
                    throw IOException("synthetic failure")
                }
            }
            actual["partial-failure"] = "result=io_error;destination=${existence(destination)}"
        }
        run {
            val destination = File(temp.root, "missing.db")
            assertThrows(IOException::class.java) {
                WalSafeSnapshotOperations.create(destination, 35) {}
            }
            actual["missing-output"] = "result=io_error;destination=${existence(destination)}"
        }
        run {
            val destination = temp.newFile("preexisting.db").apply { writeText("preserved") }
            var invoked = false
            assertThrows(IOException::class.java) {
                WalSafeSnapshotOperations.create(destination, 35) {
                    invoked = true
                    it.writeText("replacement")
                }
            }
            val destinationState = if (destination.readText() == "preserved") "preserved" else "changed"
            actual["preexisting-destination"] =
                "result=io_error;writer_invoked=$invoked;destination=$destinationState"
        }

        assertEquals(manifestRows("wal"), actual)
    }

    @Test
    fun restoreCrashStatesMatchThePortableInvariantManifest() {
        val actual = linkedMapOf<String, String>()
        for (step in StagedRestoreApplier.Step.entries) {
            val fixture = restoreFixture(step.name)
            assertThrows(SimulatedCrash::class.java) {
                apply(fixture, StagedRestoreApplier.StepHook { completed ->
                    if (completed == step) throw SimulatedCrash()
                })
            }
            actual[step.name] = restoreState(fixture)
        }

        assertEquals(manifestRows("restore"), actual)
        assertEquals(
            StagedRestoreApplier.Step.entries.map(StagedRestoreApplier.Step::name),
            actual.keys.toList(),
        )
    }

    @Test
    fun manifestRegistersExistingWalAndRestoreIntegrationCoverage() {
        val coverage = manifestRows("coverage")

        assertEquals(
            setOf(
                "wal-committed-content",
                "wal-connected-device",
                "restore-idempotent-retry",
                "restore-fsync-boundaries",
                "restore-marker-fail-closed",
            ),
            coverage.keys,
        )
        for ((id, reference) in coverage) {
            assertTrue(
                "$id must identify one exact test method",
                reference.matches(Regex("[a-zA-Z0-9_.]+#[a-zA-Z0-9_]+")),
            )
        }
        assertTrue(
            coverage.getValue("wal-connected-device")
                .startsWith("dev.bee.kanjianki.backup.DatabaseBackupWorkerInstrumentedTest#"),
        )
    }

    private fun restoreState(fixture: RestoreFixture): String {
        val marker = BackupRestoreStager.markerFile(fixture.filesDir)
        val retry = StagedRestoreApplier.retryResult(fixture.filesDir)
        return buildString {
            append("live=")
            append(
                when {
                    fixture.database.readBytes().contentEquals(fixture.original) -> "old"
                    fixture.database.readBytes().contentEquals(fixture.restored) -> "restored"
                    else -> "unknown"
                },
            )
            append(";staged=").append(BackupRestoreStager.stagedFile(fixture.filesDir).exists())
            append(";marker=").append(BackupRestoreStager.markerState(marker))
            append(";wal=").append(fixture.wal.exists())
            append(";shm=").append(fixture.shm.exists())
            append(";safety=")
                .append(DatabaseBackupPolicy.backupFile(fixture.filesDir, NOW).isFile)
            append(";restore_dir=").append(BackupRestoreStager.restoreDir(fixture.filesDir).exists())
            append(";retry=").append(retry)
            append(";startup=").append(KaniApplication.restoreAllowsStartup(retry))
        }
    }

    private fun restoreFixture(name: String): RestoreFixture {
        val root = temp.newFolder("restore-$name")
        val filesDir = File(root, "files").apply { assertTrue(mkdirs()) }
        val databaseDir = File(root, "databases").apply { assertTrue(mkdirs()) }
        val database = File(databaseDir, DatabaseBackupPolicy.DB_NAME)
        val original = "synthetic-old-$name".repeat(20).toByteArray()
        val restored = "synthetic-new-$name".repeat(20).toByteArray()
        database.writeBytes(original)
        val restoreDir = BackupRestoreStager.restoreDir(filesDir).apply { assertTrue(mkdirs()) }
        val validated = File(restoreDir, "validated.db").apply { writeBytes(restored) }
        assertTrue(
            BackupRestoreStager.stage(
                ValidatedBackup(validated, "synthetic-$name.db.gz"),
                filesDir,
                apiLevel = 35,
            ),
        )
        val wal = File(database.absolutePath + "-wal").apply { writeText("synthetic wal") }
        val shm = File(database.absolutePath + "-shm").apply { writeText("synthetic shm") }
        return RestoreFixture(filesDir, database, wal, shm, original, restored)
    }

    private fun apply(
        fixture: RestoreFixture,
        hook: StagedRestoreApplier.StepHook,
    ): StagedRestoreApplier.Result {
        return StagedRestoreApplier.applyOrThrow(
            fixture.filesDir,
            fixture.database,
            NOW,
            snapshotter = { source, destination -> source.copyTo(destination, overwrite = true) },
            stepHook = hook,
        )
    }

    private fun manifestRows(category: String): LinkedHashMap<String, String> {
        val rows = LinkedHashMap<String, String>()
        for (line in GoldenFixtureResources.text(STORAGE_INVARIANTS).lineSequence()) {
            if (line.isBlank() || line.startsWith('#')) continue
            val fields = line.split('\t')
            check(fields.size == 3) { "Malformed storage invariant row: $line" }
            if (fields[0] == category) {
                check(rows.put(fields[1], fields[2]) == null) {
                    "Duplicate $category invariant ${fields[1]}"
                }
            }
        }
        return rows
    }

    private fun existence(file: File): String = if (file.exists()) "present" else "missing"

    private data class RestoreFixture(
        val filesDir: File,
        val database: File,
        val wal: File,
        val shm: File,
        val original: ByteArray,
        val restored: ByteArray,
    )

    private class SimulatedCrash : RuntimeException()

    private companion object {
        const val NOW = 1_778_832_000_000L
        const val STORAGE_INVARIANTS =
            "dev/bee/kanjianki/fixtures/goal165/storage-invariants.tsv"
    }
}
