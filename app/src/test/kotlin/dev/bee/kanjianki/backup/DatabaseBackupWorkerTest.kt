package dev.bee.kanjianki.backup

import androidx.work.ListenableWorker
import org.junit.Assert.assertArrayEquals
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

class DatabaseBackupWorkerTest {
    @Rule
    @JvmField
    val temp = TemporaryFolder()

    @Test
    fun backupDatabaseSnapshotsTimestampedBackupAndPrunesOldFiles() {
        val db = temp.newFile("kanji_anki_simple.db")
        val content = "durable progress".toByteArray(StandardCharsets.UTF_8)
        FileOutputStream(db).use { output -> output.write(content) }
        val filesDir = temp.newFolder("files")
        val backupDir = File(filesDir, "backups")
        assertTrue(backupDir.mkdirs())
        for (i in 1..31) {
            write(File(backupDir, String.format("kanji_anki_simple_20200101_%06d.db", i)), "old-$i")
        }
        val now = 1_778_832_000_000L
        val snapshotted = booleanArrayOf(false)

        val result = DatabaseBackupWorker.backupDatabase(
            db,
            filesDir,
            now,
        ) { src, dest ->
            snapshotted[0] = true
            copyFile(src, dest)
        }

        assertSuccess(result)
        assertTrue(snapshotted[0])
        val backup = File(backupDir, "kanji_anki_simple_${timestamp(now)}.db")
        assertArrayEquals(content, read(backup))
        assertFalse(File(backupDir, "kanji_anki_simple_20200101_000001.db").exists())
        assertTrue(File(backupDir, "kanji_anki_simple_20200101_000002.db").exists())
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
        assertFalse(File(File(filesDir, "backups"), "kanji_anki_simple_${timestamp(now)}.db").exists())
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
        assertFalse(File(File(filesDir, "backups"), "kanji_anki_simple_${timestamp(now)}.db").exists())
    }

    @Test
    fun backupDatabaseLeavesIncompleteBackupWhenFailedSnapshotCannotBeDeleted() {
        val db = temp.newFile("kanji_anki_simple.db")
        write(db, "db")
        val filesDir = temp.newFolder("files")
        val now = 1_778_832_000_000L

        val result = DatabaseBackupWorker.backupDatabase(
            db,
            filesDir,
            now,
        ) { _, dst ->
            assertTrue(dst.mkdirs())
            FileOutputStream(File(dst, "partial")).use { output ->
                output.write("partial".toByteArray(StandardCharsets.UTF_8))
            }
            throw IOException("snapshot died after creating a non-empty destination")
        }

        assertFailure(result)
        val incomplete = File(File(filesDir, "backups"), "kanji_anki_simple_${timestamp(now)}.db")
        assertTrue(incomplete.isDirectory)
        assertTrue(File(incomplete, "partial").isFile)
    }

    @Test
    fun pruneOldBackupsKeepsNewestThirtyOneMatchingDatabaseFilesOnly() {
        val dir = temp.newFolder("backups")
        for (i in 1..35) {
            write(File(dir, String.format("kanji_anki_simple_20260515_%06d.db", i)), "db-$i")
        }
        val ignored = File(dir, "notes.txt")
        write(ignored, "keep")
        val wrongSuffix = File(dir, "kanji_anki_simple_20260515_999999.tmp")
        write(wrongSuffix, "keep")

        DatabaseBackupWorker.pruneOldBackups(dir)

        val files = dir.listFiles()
        assertTrue(files != null)
        val names = files!!.map { it.name }.toSet()
        assertTrue(names.contains("notes.txt"))
        assertTrue(names.contains("kanji_anki_simple_20260515_999999.tmp"))
        assertFalse(names.contains("kanji_anki_simple_20260515_000001.db"))
        assertFalse(names.contains("kanji_anki_simple_20260515_000004.db"))
        assertTrue(names.contains("kanji_anki_simple_20260515_000005.db"))
        assertTrue(names.contains("kanji_anki_simple_20260515_000035.db"))
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

    private fun read(file: File): ByteArray {
        val out = ByteArray(file.length().toInt())
        FileInputStream(file).use { input ->
            var offset = 0
            while (offset < out.size) {
                val read = input.read(out, offset, out.size - offset)
                if (read < 0) {
                    break
                }
                offset += read
            }
        }
        return out
    }
}
