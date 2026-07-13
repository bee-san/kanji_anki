package dev.bee.kanjianki.backup

import dev.bee.kanjianki.KaniApplication
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
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

    @Test
    fun failedSafetySnapshotPreservesExistingCompletedBackup() {
        val fixture = fixture("safety-failure")
        val existing = DatabaseBackupPolicy.backupFile(fixture.filesDir, NOW)
        existing.parentFile!!.mkdirs()
        val previousBytes = "previous completed archive".toByteArray()
        existing.writeBytes(previousBytes)

        try {
            StagedRestoreApplier.applyOrThrow(
                fixture.filesDir,
                fixture.database,
                NOW,
                snapshotter = { _, destination ->
                    destination.writeText("incomplete raw snapshot")
                    throw IOException("snapshot failed")
                },
            )
            throw AssertionError("failed safety snapshot must abort restore")
        } catch (_: IOException) {
            // Expected.
        }

        assertArrayEquals(previousBytes, existing.readBytes())
        assertArrayEquals(fixture.originalBytes, fixture.database.readBytes())
        assertTrue(BackupRestoreStager.stagedFile(fixture.filesDir).isFile)
        assertFalse(BackupRestoreStager.markerFile(fixture.filesDir).exists())
        assertTrue(fixture.wal.isFile)
        assertTrue(fixture.shm.isFile)
        assertFalse(File(existing.parentFile, existing.name + ".partial").exists())
        assertFalse(File(existing.parentFile, existing.name + ".pre-restore.tmp").exists())
        assertEquals(
            StagedRestoreApplier.Result.RETRY_NEEDED,
            StagedRestoreApplier.retryResult(fixture.filesDir),
        )
        assertTrue(KaniApplication.restoreAllowsStartup(StagedRestoreApplier.Result.RETRY_NEEDED))
    }

    @Test
    fun unsupportedPlatformPreservesStagedOnlyPreReplacementStateByteForByte() {
        val fixture = fixture("unsupported")
        val staged = BackupRestoreStager.stagedFile(fixture.filesDir)
        val marker = BackupRestoreStager.markerFile(fixture.filesDir)
        val stagedBytes = staged.readBytes()

        val result = StagedRestoreApplier.applyOrThrow(
            fixture.filesDir,
            fixture.database,
            NOW,
            snapshotter = { _, _ -> throw AssertionError("unsupported restore must not snapshot") },
            operations = StagedRestoreApplier.Operations(apiLevel = 29),
        )

        assertEquals(StagedRestoreApplier.Result.UNSUPPORTED_PLATFORM, result)
        assertArrayEquals(fixture.originalBytes, fixture.database.readBytes())
        assertArrayEquals(stagedBytes, staged.readBytes())
        assertFalse(marker.exists())
        assertTrue(fixture.wal.isFile)
        assertTrue(fixture.shm.isFile)
        assertFalse(DatabaseBackupPolicy.backupDir(fixture.filesDir).exists())
    }

    @Test
    fun unsupportedPlatformBlocksReadyMarkerStateWithoutTouchingFiles() {
        val fixture = fixture("unsupported-ready")
        val staged = BackupRestoreStager.stagedFile(fixture.filesDir)
        val marker = BackupRestoreStager.markerFile(fixture.filesDir)
        BackupRestoreStager.ensureRecoveryMarker(marker)
        val stagedBytes = staged.readBytes()
        val markerBytes = marker.readBytes()

        val result = StagedRestoreApplier.applyOrThrow(
            fixture.filesDir,
            fixture.database,
            NOW,
            snapshotter = { _, _ -> throw AssertionError("unsupported restore must not snapshot") },
            operations = StagedRestoreApplier.Operations(apiLevel = 29),
        )

        assertEquals(StagedRestoreApplier.Result.BLOCK_STARTUP, result)
        assertFalse(KaniApplication.restoreAllowsStartup(result))
        assertArrayEquals(fixture.originalBytes, fixture.database.readBytes())
        assertArrayEquals(stagedBytes, staged.readBytes())
        assertArrayEquals(markerBytes, marker.readBytes())
        assertTrue(fixture.wal.isFile)
        assertTrue(fixture.shm.isFile)
        assertFalse(DatabaseBackupPolicy.backupDir(fixture.filesDir).exists())
    }

    @Test
    fun unsupportedPlatformStillFinishesMarkerOnlyRecovery() {
        val fixture = fixture("unsupported-marker-only")
        val staged = BackupRestoreStager.stagedFile(fixture.filesDir)
        val marker = BackupRestoreStager.markerFile(fixture.filesDir)
        BackupRestoreStager.ensureRecoveryMarker(marker)
        BackupRestoreStager.moveAtomically(staged, fixture.database)

        val result = StagedRestoreApplier.applyOrThrow(
            fixture.filesDir,
            fixture.database,
            NOW,
            snapshotter = { _, _ -> throw AssertionError("marker-only recovery must not snapshot") },
            operations = StagedRestoreApplier.Operations(apiLevel = 29),
        )

        assertEquals(StagedRestoreApplier.Result.APPLIED, result)
        assertArrayEquals(fixture.restoredBytes, fixture.database.readBytes())
        assertFalse(marker.exists())
        assertFalse(fixture.wal.exists())
        assertFalse(fixture.shm.exists())
    }

    @Test
    fun atomicReplacementFailurePreservesBothDatabasesAndCompletedSafetyBackup() {
        val fixture = fixture("atomic-failure")
        val staged = BackupRestoreStager.stagedFile(fixture.filesDir)
        val operations = StagedRestoreApplier.Operations(
            atomicReplacer = BackupRestoreStager.AtomicFileReplacer { source, destination ->
                if (source == staged) {
                    throw AtomicMoveNotSupportedException(
                        source.absolutePath,
                        destination.absolutePath,
                        "not supported",
                    )
                }
                BackupRestoreStager.moveAtomically(source, destination)
            },
        )

        try {
            apply(fixture, operations = operations)
            throw AssertionError("atomic replacement failure must abort restore")
        } catch (_: AtomicMoveNotSupportedException) {
            // Expected.
        }

        assertArrayEquals(fixture.originalBytes, fixture.database.readBytes())
        assertArrayEquals(fixture.restoredBytes, staged.readBytes())
        assertTrue(BackupRestoreStager.markerFile(fixture.filesDir).isFile)
        assertTrue(fixture.wal.isFile)
        assertTrue(fixture.shm.isFile)
        val safety = DatabaseBackupPolicy.backupFile(fixture.filesDir, NOW)
        assertArrayEquals(fixture.originalBytes, GZIPInputStream(safety.inputStream()).use { it.readBytes() })

        val retry = StagedRestoreApplier.applyOrThrow(
            fixture.filesDir,
            fixture.database,
            NOW,
            snapshotter = { _, _ -> throw AssertionError("ready-marker retry must not snapshot") },
        )
        assertEquals(StagedRestoreApplier.Result.APPLIED, retry)
        assertArrayEquals(fixture.restoredBytes, fixture.database.readBytes())
    }

    @Test
    fun legacyPreSafetyMarkerIsUpgradedOnlyAfterTakingSafetySnapshot() {
        val fixture = fixture("legacy-marker")
        val marker = BackupRestoreStager.markerFile(fixture.filesDir)
        marker.writeText("source_name=legacy.db.gz\nstaged_at=$NOW\n")
        var snapshots = 0

        val result = StagedRestoreApplier.applyOrThrow(
            fixture.filesDir,
            fixture.database,
            NOW,
            snapshotter = { source, destination ->
                snapshots++
                source.copyTo(destination, overwrite = true)
            },
        )

        assertEquals(StagedRestoreApplier.Result.APPLIED, result)
        assertEquals(1, snapshots)
        assertArrayEquals(fixture.restoredBytes, fixture.database.readBytes())
        val safety = DatabaseBackupPolicy.backupFile(fixture.filesDir, NOW)
        assertArrayEquals(fixture.originalBytes, GZIPInputStream(safety.inputStream()).use { it.readBytes() })
    }

    @Test
    fun durableRestoreOrdersDirectorySyncsAroundReplacementAndSidecarCleanup() {
        val fixture = fixture("sync-order")
        val syncedDirectories = ArrayList<File>()
        val synchronizer = DirectorySynchronizer { directory ->
            syncedDirectories.add(directory.canonicalFile)
        }
        val operations = StagedRestoreApplier.Operations(
            backupPublisher = DatabaseBackupWorker.BackupPublisher { partial, destination ->
                BackupRestoreStager.moveAtomically(partial, destination)
                synchronizer.sync(destination.parentFile!!)
            },
            directorySynchronizer = synchronizer,
        )

        assertEquals(StagedRestoreApplier.Result.APPLIED, apply(fixture, operations = operations))

        val backupDir = DatabaseBackupPolicy.backupDir(fixture.filesDir).canonicalFile
        val restoreDir = BackupRestoreStager.restoreDir(fixture.filesDir).canonicalFile
        val databaseDir = fixture.database.parentFile!!.canonicalFile
        assertEquals(
            listOf(restoreDir, backupDir, restoreDir, databaseDir, restoreDir, databaseDir, restoreDir),
            syncedDirectories,
        )
    }

    @Test
    fun databaseDirectorySyncFailureAfterReplacementBlocksStartup() {
        val fixture = fixture("database-sync-failure")
        var syncCalls = 0
        val operations = StagedRestoreApplier.Operations(
            directorySynchronizer = DirectorySynchronizer {
                syncCalls++
                if (syncCalls == 3) throw IOException("database directory fsync failed")
            },
        )

        try {
            apply(fixture, operations = operations)
            throw AssertionError("post-replacement directory sync failure must abort")
        } catch (_: IOException) {
            // Expected.
        }

        assertArrayEquals(fixture.restoredBytes, fixture.database.readBytes())
        assertFalse(BackupRestoreStager.stagedFile(fixture.filesDir).exists())
        assertTrue(BackupRestoreStager.markerFile(fixture.filesDir).isFile)
        assertTrue(fixture.wal.isFile)
        assertTrue(fixture.shm.isFile)
        assertEquals(StagedRestoreApplier.Result.BLOCK_STARTUP, StagedRestoreApplier.retryResult(fixture.filesDir))
    }

    @Test
    fun directorySyncFailuresRemainSafeAtMarkerSidecarAndFinalBoundaries() {
        for (failAt in listOf(1, 2, 4, 5, 6)) {
            val fixture = fixture("directory-sync-boundary-$failAt")
            var syncCalls = 0
            val operations = StagedRestoreApplier.Operations(
                directorySynchronizer = DirectorySynchronizer {
                    syncCalls++
                    if (syncCalls == failAt) throw IOException("directory fsync $failAt failed")
                },
            )

            try {
                apply(fixture, operations = operations)
                throw AssertionError("directory sync $failAt failure must surface")
            } catch (_: IOException) {
                // Expected.
            }

            val retry = StagedRestoreApplier.retryResult(fixture.filesDir)
            when (failAt) {
                1 -> {
                    assertArrayEquals(fixture.originalBytes, fixture.database.readBytes())
                    assertTrue(BackupRestoreStager.stagedFile(fixture.filesDir).isFile)
                    assertFalse(BackupRestoreStager.markerFile(fixture.filesDir).exists())
                    assertTrue(fixture.wal.isFile)
                    assertTrue(fixture.shm.isFile)
                    assertEquals(StagedRestoreApplier.Result.RETRY_NEEDED, retry)
                    assertTrue(KaniApplication.restoreAllowsStartup(retry))
                }
                2 -> {
                    assertArrayEquals(fixture.originalBytes, fixture.database.readBytes())
                    assertTrue(BackupRestoreStager.stagedFile(fixture.filesDir).isFile)
                    assertTrue(BackupRestoreStager.markerFile(fixture.filesDir).isFile)
                    assertTrue(fixture.wal.isFile)
                    assertTrue(fixture.shm.isFile)
                    assertEquals(StagedRestoreApplier.Result.BLOCK_STARTUP, retry)
                    assertFalse(KaniApplication.restoreAllowsStartup(retry))
                }
                4 -> {
                    assertArrayEquals(fixture.restoredBytes, fixture.database.readBytes())
                    assertFalse(BackupRestoreStager.stagedFile(fixture.filesDir).exists())
                    assertTrue(BackupRestoreStager.markerFile(fixture.filesDir).isFile)
                    assertTrue(fixture.wal.isFile)
                    assertTrue(fixture.shm.isFile)
                    assertEquals(StagedRestoreApplier.Result.BLOCK_STARTUP, retry)
                    assertFalse(KaniApplication.restoreAllowsStartup(retry))
                }
                5 -> {
                    assertArrayEquals(fixture.restoredBytes, fixture.database.readBytes())
                    assertFalse(BackupRestoreStager.stagedFile(fixture.filesDir).exists())
                    assertTrue(BackupRestoreStager.markerFile(fixture.filesDir).isFile)
                    assertFalse(fixture.wal.exists())
                    assertFalse(fixture.shm.exists())
                    assertEquals(StagedRestoreApplier.Result.BLOCK_STARTUP, retry)
                    assertFalse(KaniApplication.restoreAllowsStartup(retry))
                }
                6 -> {
                    assertArrayEquals(fixture.restoredBytes, fixture.database.readBytes())
                    assertFalse(BackupRestoreStager.stagedFile(fixture.filesDir).exists())
                    assertFalse(BackupRestoreStager.markerFile(fixture.filesDir).exists())
                    assertFalse(fixture.wal.exists())
                    assertFalse(fixture.shm.exists())
                    assertEquals(StagedRestoreApplier.Result.RETRY_NEEDED, retry)
                    assertTrue(KaniApplication.restoreAllowsStartup(retry))
                }
            }
        }
    }

    @Test
    fun safetyPublicationFailurePreservesPriorArchiveAndAllRestoreState() {
        val fixture = fixture("publish-failure")
        val destination = DatabaseBackupPolicy.backupFile(fixture.filesDir, NOW)
        destination.parentFile!!.mkdirs()
        val completed = "prior complete archive".toByteArray()
        destination.writeBytes(completed)
        val operations = StagedRestoreApplier.Operations(
            backupPublisher = DatabaseBackupWorker.BackupPublisher { _, _ ->
                throw IOException("atomic publication unavailable")
            },
        )

        try {
            apply(fixture, operations = operations)
            throw AssertionError("safety publication failure must abort restore")
        } catch (_: IOException) {
            // Expected.
        }

        assertArrayEquals(completed, destination.readBytes())
        assertArrayEquals(fixture.originalBytes, fixture.database.readBytes())
        assertArrayEquals(fixture.restoredBytes, BackupRestoreStager.stagedFile(fixture.filesDir).readBytes())
        assertFalse(BackupRestoreStager.markerFile(fixture.filesDir).exists())
        assertTrue(fixture.wal.isFile)
        assertTrue(fixture.shm.isFile)
        assertFalse(File(destination.parentFile, destination.name + ".partial").exists())
        assertFalse(File(destination.parentFile, destination.name + ".pre-restore.tmp").exists())
    }

    @Test
    fun postReplacementCleanupFailureBlocksStartupUntilMarkerOnlyRetryFinishes() {
        val fixture = fixture("cleanup-failure")
        val operations = StagedRestoreApplier.Operations(
            requiredFileDeleter = StagedRestoreApplier.RequiredFileDeleter { file ->
                if (file.name.endsWith("-shm")) throw IOException("cannot delete shm")
                if (file.exists() && !file.delete()) throw IOException("cannot delete file")
            },
        )

        try {
            apply(fixture, operations = operations)
            throw AssertionError("sidecar cleanup failure must abort restore")
        } catch (_: IOException) {
            // Expected.
        }

        assertArrayEquals(fixture.restoredBytes, fixture.database.readBytes())
        assertFalse(BackupRestoreStager.stagedFile(fixture.filesDir).exists())
        assertTrue(BackupRestoreStager.markerFile(fixture.filesDir).isFile)
        assertFalse(fixture.wal.exists())
        assertTrue(fixture.shm.isFile)
        val retry = StagedRestoreApplier.retryResult(fixture.filesDir)
        assertEquals(StagedRestoreApplier.Result.BLOCK_STARTUP, retry)
        assertFalse(KaniApplication.restoreAllowsStartup(retry))

        val completed = StagedRestoreApplier.applyOrThrow(
            fixture.filesDir,
            fixture.database,
            NOW,
            snapshotter = { _, _ -> throw AssertionError("marker-only retry must not snapshot") },
        )
        assertEquals(StagedRestoreApplier.Result.APPLIED, completed)
        assertFalse(BackupRestoreStager.markerFile(fixture.filesDir).exists())
        assertFalse(fixture.shm.exists())
    }

    @Test
    fun safetyBackupSweepsRecognizedPriorRestoreScratchOnly() {
        val fixture = fixture("restore-scratch-sweep")
        val backupDir = DatabaseBackupPolicy.backupDir(fixture.filesDir).apply { assertTrue(mkdirs()) }
        val staleRaw = File(
            backupDir,
            "kanji_anki_simple_20260102_030405.db.gz.pre-restore.tmp",
        ).apply { writeText("abandoned raw") }
        val stalePartial = File(
            backupDir,
            "kanji_anki_simple_20260102_030406.db.gz.partial",
        ).apply { writeText("abandoned gzip") }
        val completed = File(backupDir, "kanji_anki_simple_20260102_030407.db.gz").apply {
            writeText("completed")
        }
        val unknown = File(backupDir, "foreign.db.gz.pre-restore.tmp").apply { writeText("unknown") }

        assertEquals(StagedRestoreApplier.Result.APPLIED, apply(fixture))

        assertFalse(staleRaw.exists())
        assertFalse(stalePartial.exists())
        assertEquals("completed", completed.readText())
        assertEquals("unknown", unknown.readText())
    }

    @Test
    fun invalidMarkerDirectoryCannotDiscardCurrentWalState() {
        val filesDir = temp.newFolder("invalid-marker-files")
        val databaseDir = temp.newFolder("invalid-marker-database")
        val database = File(databaseDir, DatabaseBackupPolicy.DB_NAME).apply { writeText("current") }
        val wal = File(database.absolutePath + "-wal").apply { writeText("committed wal") }
        val shm = File(database.absolutePath + "-shm").apply { writeText("shared memory") }
        val marker = BackupRestoreStager.markerFile(filesDir)
        assertTrue(marker.mkdirs())

        try {
            StagedRestoreApplier.applyOrThrow(
                filesDir,
                database,
                NOW,
                snapshotter = { _, _ -> throw AssertionError("invalid marker must not snapshot") },
            )
            throw AssertionError("invalid marker must fail closed")
        } catch (_: IOException) {
            // Expected.
        }

        assertEquals("current", database.readText())
        assertEquals("committed wal", wal.readText())
        assertEquals("shared memory", shm.readText())
        assertTrue(marker.isDirectory)
        assertEquals(StagedRestoreApplier.Result.BLOCK_STARTUP, StagedRestoreApplier.retryResult(filesDir))
    }

    @Test
    fun legacyMarkerOnlyStatePreservesAmbiguousLiveDatabaseAndSidecars() {
        val filesDir = temp.newFolder("legacy-marker-only-files")
        val databaseDir = temp.newFolder("legacy-marker-only-database")
        val database = File(databaseDir, DatabaseBackupPolicy.DB_NAME).apply { writeText("current") }
        val wal = File(database.absolutePath + "-wal").apply { writeText("possibly current wal") }
        val shm = File(database.absolutePath + "-shm").apply { writeText("possibly current shm") }
        val marker = BackupRestoreStager.markerFile(filesDir)
        marker.parentFile!!.mkdirs()
        marker.writeText("source_name=legacy.db.gz\nstaged_at=$NOW\n")

        try {
            StagedRestoreApplier.applyOrThrow(
                filesDir,
                database,
                NOW,
                snapshotter = { _, _ -> throw AssertionError("legacy marker-only state must not snapshot") },
            )
            throw AssertionError("legacy marker-only state must block for manual recovery")
        } catch (_: IOException) {
            // Expected.
        }

        assertEquals("current", database.readText())
        assertEquals("possibly current wal", wal.readText())
        assertEquals("possibly current shm", shm.readText())
        assertTrue(marker.isFile)
        assertEquals(StagedRestoreApplier.Result.BLOCK_STARTUP, StagedRestoreApplier.retryResult(filesDir))
    }

    @Test
    fun markerOnlyStateWithoutLiveDatabaseBlocksStartup() {
        val filesDir = temp.newFolder("missing-live-files")
        val marker = BackupRestoreStager.markerFile(filesDir)
        marker.parentFile!!.mkdirs()
        BackupRestoreStager.ensureRecoveryMarker(marker)
        val missingDatabase = File(temp.root, "missing-live.db")

        try {
            StagedRestoreApplier.applyOrThrow(
                filesDir,
                missingDatabase,
                NOW,
                snapshotter = { _, _ -> throw AssertionError("missing live DB must not snapshot") },
            )
            throw AssertionError("missing replaced database must fail")
        } catch (_: IOException) {
            // Expected.
        }

        assertTrue(marker.isFile)
        val retry = StagedRestoreApplier.retryResult(filesDir)
        assertEquals(StagedRestoreApplier.Result.BLOCK_STARTUP, retry)
        assertFalse(KaniApplication.restoreAllowsStartup(retry))
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
        assertTrue(
            BackupRestoreStager.stage(
                ValidatedBackup(validatedFile, "$name.db.gz"),
                filesDir,
                apiLevel = 35,
            ),
        )
        val wal = File(database.absolutePath + "-wal").apply { writeText("stale wal") }
        val shm = File(database.absolutePath + "-shm").apply { writeText("stale shm") }
        return Fixture(filesDir, database, wal, shm, original, restored)
    }

    private fun apply(
        fixture: Fixture,
        hook: StagedRestoreApplier.StepHook = StagedRestoreApplier.StepHook {},
        operations: StagedRestoreApplier.Operations = StagedRestoreApplier.Operations(),
    ): StagedRestoreApplier.Result {
        return StagedRestoreApplier.applyOrThrow(
            fixture.filesDir,
            fixture.database,
            NOW,
            snapshotter = { source, destination -> source.copyTo(destination, overwrite = true) },
            operations = operations,
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
