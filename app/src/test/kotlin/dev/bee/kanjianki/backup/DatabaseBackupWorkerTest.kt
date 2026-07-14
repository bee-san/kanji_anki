package dev.bee.kanjianki.backup

import androidx.work.ListenableWorker
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

class DatabaseBackupWorkerTest {
    @Rule
    @JvmField
    val temp = TemporaryFolder()

    @Test
    fun unsupportedStaleWorkerFinishesWithoutTouchingDatabaseOrArchives() {
        val filesDir = temp.newFolder("unsupported-files")
        val backupDir = File(filesDir, "backups").apply { assertTrue(mkdirs()) }
        val archive = File(backupDir, "kanji_anki_simple_20260101_000000.db.gz")
        val archiveBytes = "existing archive".toByteArray(StandardCharsets.UTF_8)
        archive.writeBytes(archiveBytes)
        var environmentTouched = false
        val environment = object : DatabaseBackupWorker.BackupEnvironment {
            override fun databasePath(name: String): File {
                environmentTouched = true
                throw AssertionError("unsupported worker must not inspect the database")
            }

            override fun filesDir(): File {
                environmentTouched = true
                throw AssertionError("unsupported worker must not inspect backup storage")
            }

            override fun snapshot(dbFile: File, dest: File) {
                environmentTouched = true
                throw AssertionError("unsupported worker must not snapshot")
            }
        }

        val result = DatabaseBackupWorker.doWork(environment, 1_778_832_000_000L, apiLevel = 29)

        assertSuccess(result)
        assertFalse(environmentTouched)
        assertArrayEquals(archiveBytes, archive.readBytes())
    }

    @Test
    fun backupDatabaseWritesCompressedTimestampedBackupAndPrunesOldFiles() {
        val db = temp.newFile("kanji_anki_simple.db")
        val content = "durable progress".repeat(4_000).toByteArray(StandardCharsets.UTF_8)
        FileOutputStream(db).use { output -> output.write(content) }
        val filesDir = temp.newFolder("files")
        val backupDir = File(filesDir, "backups")
        assertTrue(backupDir.mkdirs())
        // A month of daily backups already present; tiered retention should prune most.
        for (day in 1..28) {
            write(File(backupDir, String.format("kanji_anki_simple_202601%02d_120000.db.gz", day)), "old-$day")
        }
        val now = 1_778_832_000_000L
        val snapshotSource = arrayOfNulls<File>(1)

        val result = DatabaseBackupWorker.backupDatabase(
            db,
            filesDir,
            now,
        ) { src, dest ->
            snapshotSource[0] = src
            copyFile(src, dest)
        }

        assertSuccess(result)
        // Snapshotter received an uncompressed temp path, not the final .gz.
        assertTrue(snapshotSource[0] == db)
        val backup = File(backupDir, "kanji_anki_simple_${timestamp(now)}.db.gz")
        assertTrue(backup.isFile)
        // Compressed backup is smaller than the raw db and round-trips to the original.
        assertTrue(backup.length() < content.size)
        assertArrayEquals(content, gunzip(backup))
        // The intermediate temp copy is cleaned up.
        assertFalse(File(backupDir, backup.name + ".tmp").exists())
        // Tiered retention keeps at most KEEP_DAILY + KEEP_WEEKLY files.
        val remaining = backupDir.listFiles { _, name -> name.endsWith(".db.gz") }!!
        assertTrue(remaining.size <= 7 + 4 + 1)
    }

    @Test
    fun backupDatabaseFailsWhenSourceDatabaseIsMissing() {
        val filesDir = temp.newFolder("files")

        val result = DatabaseBackupWorker.backupDatabase(
            File(temp.root, "missing.db"),
            filesDir,
            1_778_832_000_000L,
        ) { _, _ -> throw AssertionError("missing databases must not be snapshotted") }

        assertFailure(result)
        assertFalse(File(filesDir, "backups").exists())
    }

    @Test
    fun backupDatabaseFailsWhenBackupDirectoryCannotBeCreated() {
        val db = temp.newFile("kanji_anki_simple.db")
        write(db, "db")
        val filesDir = temp.newFile("files-is-not-a-directory")

        val result = DatabaseBackupWorker.backupDatabase(
            db,
            filesDir,
            1_778_832_000_000L,
        ) { _, _ -> throw AssertionError("unwritable backup directories must not snapshot") }

        assertFailure(result)
    }

    @Test
    fun backupDatabaseDeletesIncompleteBackupWhenSnapshotFails() {
        val db = temp.newFile("kanji_anki_simple.db")
        write(db, "db")
        val filesDir = temp.newFolder("files")
        val now = 1_778_832_000_000L

        val result = DatabaseBackupWorker.backupDatabase(
            db,
            filesDir,
            now,
        ) { _, dst ->
            FileOutputStream(dst).use { output -> output.write("partial".toByteArray(StandardCharsets.UTF_8)) }
            throw IOException("disk full")
        }

        assertFailure(result)
        assertFalse(File(File(filesDir, "backups"), "kanji_anki_simple_${timestamp(now)}.db.gz").exists())
        // The intermediate temp copy is cleaned up even on failure.
        assertFalse(File(File(filesDir, "backups"), "kanji_anki_simple_${timestamp(now)}.db.gz.tmp").exists())
    }

    @Test
    fun backupDatabaseFailsWhenSnapshotThrowsRuntimeException() {
        val db = temp.newFile("kanji_anki_simple.db")
        write(db, "db")
        val filesDir = temp.newFolder("files")
        val now = 1_778_832_000_000L

        val result = DatabaseBackupWorker.backupDatabase(
            db,
            filesDir,
            now,
        ) { _, _ -> throw IllegalStateException("VACUUM failed") }

        assertFailure(result)
        val final = File(File(filesDir, "backups"), "kanji_anki_simple_${timestamp(now)}.db.gz")
        assertFalse(final.exists())
        assertFalse(File(final.parentFile, final.name + ".partial").exists())
        assertFalse(File(final.parentFile, final.name + ".tmp").exists())
    }

    @Test
    fun backupDatabaseRejectsSnapshotterThatProducesNoFile() {
        val db = temp.newFile("empty-snapshot-source.db")
        write(db, "db")
        val filesDir = temp.newFolder("empty-snapshot-files")
        val now = 1_778_832_000_000L

        val result = DatabaseBackupWorker.backupDatabase(db, filesDir, now) { _, _ -> }

        assertFailure(result)
        val final = DatabaseBackupPolicy.backupFile(filesDir, now)
        assertFalse(final.exists())
        assertFalse(File(final.parentFile, final.name + ".partial").exists())
        assertFalse(File(final.parentFile, final.name + ".tmp").exists())
    }

    @Test
    fun priorRunScratchIsSweptBeforeSnapshotWithoutTouchingCompletedOrUnknownFiles() {
        val db = temp.newFile("sweep-source.db")
        write(db, "new database")
        val filesDir = temp.newFolder("sweep-files")
        val backupDir = DatabaseBackupPolicy.backupDir(filesDir).apply { assertTrue(mkdirs()) }
        val staleTemp = File(backupDir, "kanji_anki_simple_20260102_030405.db.gz.tmp").apply {
            writeText("abandoned raw snapshot")
        }
        val stalePartial = File(backupDir, "kanji_anki_simple_20260102_030406.db.gz.partial").apply {
            writeText("abandoned compressed snapshot")
        }
        val completed = File(backupDir, "kanji_anki_simple_20260102_030407.db.gz").apply {
            writeText("completed archive")
        }
        val invalidTimestamp = File(backupDir, "kanji_anki_simple_20260230_030408.db.gz.tmp").apply {
            writeText("unknown invalid-date file")
        }
        val unknown = File(backupDir, "notes.db.gz.partial").apply { writeText("unrelated") }

        val result = DatabaseBackupWorker.backupDatabase(db, filesDir, 1_778_832_000_000L) { source, destination ->
            assertFalse(staleTemp.exists())
            assertFalse(stalePartial.exists())
            assertTrue(completed.isFile)
            assertTrue(invalidTimestamp.isFile)
            assertTrue(unknown.isFile)
            copyFile(source, destination)
        }

        assertSuccess(result)
        assertFalse(staleTemp.exists())
        assertFalse(stalePartial.exists())
        assertEquals("completed archive", completed.readText())
        assertEquals("unknown invalid-date file", invalidTimestamp.readText())
        assertEquals("unrelated", unknown.readText())
    }

    @Test
    fun nonRemovablePriorScratchAbortsBeforeSnapshotAndPreservesCompletedAndUnknownFiles() {
        val db = temp.newFile("stale-scratch-source.db")
        write(db, "db")
        val filesDir = temp.newFolder("stale-scratch-files")
        val backupDir = DatabaseBackupPolicy.backupDir(filesDir).apply { assertTrue(mkdirs()) }
        val now = 1_778_832_000_000L
        val final = DatabaseBackupPolicy.backupFile(filesDir, now)
        val completed = "completed archive".toByteArray(StandardCharsets.UTF_8)
        final.writeBytes(completed)
        val scratchDirectory = File(
            backupDir,
            "kanji_anki_simple_20260102_030405.db.gz.partial",
        ).apply { assertTrue(mkdirs()) }
        File(scratchDirectory, "stubborn").writeText("keep")
        val invalidTimestamp = File(backupDir, "kanji_anki_simple_20260230_030405.db.gz.tmp").apply {
            writeText("preserve invalid date")
        }
        val unknown = File(backupDir, "unknown.db.gz.tmp").apply { writeText("preserve unknown") }
        var snapshotCalled = false

        val result = DatabaseBackupWorker.backupDatabase(db, filesDir, now) { _, _ ->
            snapshotCalled = true
        }

        assertFailure(result)
        assertFalse(snapshotCalled)
        assertArrayEquals(completed, final.readBytes())
        assertTrue(scratchDirectory.isDirectory)
        assertEquals("preserve invalid date", invalidTimestamp.readText())
        assertEquals("preserve unknown", unknown.readText())
    }

    @Test
    fun failedAtomicPublicationPreservesExistingFinalAndCleansPartial() {
        val db = temp.newFile("kanji_anki_simple.db")
        write(db, "new database")
        val filesDir = temp.newFolder("files")
        val backupDir = File(filesDir, "backups")
        assertTrue(backupDir.mkdirs())
        val now = 1_778_832_000_000L
        val final = File(backupDir, "kanji_anki_simple_${timestamp(now)}.db.gz")
        val oldBytes = "previous complete backup".toByteArray(StandardCharsets.UTF_8)
        FileOutputStream(final).use { it.write(oldBytes) }

        val result = DatabaseBackupWorker.backupDatabase(
            db,
            filesDir,
            now,
            { src, dest -> copyFile(src, dest) },
            { partial, destination ->
                assertTrue(partial.name.endsWith(".db.gz.partial"))
                assertTrue(partial.parentFile == destination.parentFile)
                throw IOException("atomic move unavailable")
            },
        )

        assertFailure(result)
        assertArrayEquals(oldBytes, final.readBytes())
        assertFalse(File(backupDir, final.name + ".partial").exists())
        assertFalse(File(backupDir, final.name + ".tmp").exists())
    }

    @Test
    fun realAtomicPublisherRefusesToReplaceExistingCompletedArchive() {
        val backupDir = temp.newFolder("publisher-existing-final")
        val partial = File(backupDir, "backup.db.gz.partial").apply { writeText("new archive") }
        val destination = File(backupDir, "backup.db.gz").apply { writeText("completed archive") }

        try {
            DatabaseBackupWorker.publishAtomically(partial, destination)
            throw AssertionError("existing completed archive must not be replaced")
        } catch (_: IOException) {
            // Expected.
        }

        assertEquals("completed archive", destination.readText())
        assertEquals("new archive", partial.readText())
    }

    @Test
    fun pruneOldBackupsTieredRetentionKeepsRecentDailyAndWeeklyCompressedFiles() {
        val dir = temp.newFolder("backups")
        // 40 consecutive daily backups spanning early-March to mid-April 2026.
        val stamps = ArrayList<String>()
        for (day in 1..31) {
            stamps.add(String.format("202603%02d_120000", day))
        }
        for (day in 1..9) {
            stamps.add(String.format("202604%02d_120000", day))
        }
        for (stamp in stamps) {
            write(File(dir, "kanji_anki_simple_$stamp.db.gz"), "db-$stamp")
        }
        write(File(dir, "notes.txt"), "keep")

        DatabaseBackupWorker.pruneOldBackups(dir)

        val names = dir.listFiles()!!.map { it.name }.toSet()
        assertTrue(names.contains("notes.txt"))
        val remainingBackups = names.count { it.startsWith("kanji_anki_simple_") && it.endsWith(".db.gz") }
        // 7 daily + up to 4 weekly.
        assertTrue("kept $remainingBackups backups", remainingBackups in 8..11)
        // Newest daily is kept; the oldest March backup is pruned.
        assertTrue(names.contains("kanji_anki_simple_20260409_120000.db.gz"))
        assertFalse(names.contains("kanji_anki_simple_20260301_120000.db.gz"))
    }

    @Test
    fun pruneOldBackupsContinuesWhenOldestMatchingEntryCannotBeDeleted() {
        val dir = temp.newFolder("backups")
        val stubborn = File(dir, "kanji_anki_simple_20200101_000000.db")
        assertTrue(stubborn.mkdirs())
        write(File(stubborn, "partial"), "partial")
        for (i in 1..31) {
            write(File(dir, String.format("kanji_anki_simple_20260515_%06d.db", i)), "db-$i")
        }
        write(File(dir, "other_20200101_000000.db"), "not a Kani backup")

        DatabaseBackupWorker.pruneOldBackups(dir)

        assertTrue(stubborn.isDirectory)
        assertTrue(File(stubborn, "partial").isFile)
        assertTrue(File(dir, "other_20200101_000000.db").isFile)
    }

    @Test
    fun pruneOldBackupsAllowsMissingOrSmallDirectories() {
        val missing = File(temp.root, "missing")
        val small = temp.newFolder("small")
        write(File(small, "kanji_anki_simple_20260515_000001.db"), "one")

        DatabaseBackupWorker.pruneOldBackups(missing)
        DatabaseBackupWorker.pruneOldBackups(small)

        assertFalse(missing.exists())
        assertTrue(File(small, "kanji_anki_simple_20260515_000001.db").exists())
    }

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun gunzip(file: File): ByteArray {
        return GZIPInputStream(FileInputStream(file)).use { it.readBytes() }
    }

    private fun write(file: File, text: String) {
        FileOutputStream(file).use { output -> output.write(text.toByteArray(StandardCharsets.UTF_8)) }
    }

    private fun timestamp(millis: Long): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(millis)
    }

    private fun assertSuccess(result: ListenableWorker.Result) {
        assertTrue(result is ListenableWorker.Result.Success)
    }

    private fun assertFailure(result: ListenableWorker.Result) {
        assertTrue(result is ListenableWorker.Result.Failure)
    }
}
