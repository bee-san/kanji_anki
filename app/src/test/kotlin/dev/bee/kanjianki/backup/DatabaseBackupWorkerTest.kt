package dev.bee.kanjianki.backup

import androidx.work.ListenableWorker
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

class DatabaseBackupWorkerTest {
    @Rule
    @JvmField
    val temp = TemporaryFolder()

    @Test
    fun backupDatabaseCopiesTimestampedBackupAndPrunesOldFiles() {
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
        val checkpointed = booleanArrayOf(false)

        val result = DatabaseBackupWorker.backupDatabase(
            db,
            filesDir,
            now,
            { checkpointed[0] = true },
            DatabaseBackupWorker::copyFile,
        )

        assertSuccess(result)
        assertTrue(checkpointed[0])
        val backup = File(backupDir, "kanji_anki_simple_${timestamp(now)}.db")
        assertArrayEquals(content, read(backup))
        assertFalse(File(backupDir, "kanji_anki_simple_20200101_000001.db").exists())
        assertTrue(File(backupDir, "kanji_anki_simple_20200101_000002.db").exists())
    }

    @Test
    fun doWorkUsesRealBackupFlowAndCheckpointFallback() {
        val db = temp.newFile("kanji_anki_simple.db")
        val content = "not a sqlite database but still backup-worthy".toByteArray(StandardCharsets.UTF_8)
        FileOutputStream(db).use { output -> output.write(content) }
        val filesDir = temp.newFolder("files")
        val now = 1_778_832_000_000L

        val result = DatabaseBackupWorker.doWork(
            object : DatabaseBackupWorker.BackupEnvironment {
                override fun databasePath(name: String): File {
                    assertEquals("kanji_anki_simple.db", name)
                    return db
                }

                override fun filesDir(): File {
                    return filesDir
                }
            },
            now,
        )

        assertSuccess(result)
        val backup = File(File(filesDir, "backups"), "kanji_anki_simple_${timestamp(now)}.db")
        assertArrayEquals(content, read(backup))
    }

    @Test
    fun backupDatabaseFailsWhenSourceDatabaseIsMissing() {
        val filesDir = temp.newFolder("files")

        val result = DatabaseBackupWorker.backupDatabase(
            File(temp.root, "missing.db"),
            filesDir,
            1_778_832_000_000L,
            { throw AssertionError("missing databases must not be checkpointed") },
            { _, _ -> throw AssertionError("missing databases must not be copied") },
        )

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
            { throw AssertionError("unwritable backup directories must not checkpoint") },
            { _, _ -> throw AssertionError("unwritable backup directories must not copy") },
        )

        assertFailure(result)
    }

    @Test
    fun backupDatabaseDeletesIncompleteBackupWhenCopyFails() {
        val db = temp.newFile("kanji_anki_simple.db")
        write(db, "db")
        val filesDir = temp.newFolder("files")
        val now = 1_778_832_000_000L

        val result = DatabaseBackupWorker.backupDatabase(
            db,
            filesDir,
            now,
            { },
            { _, dst ->
                FileOutputStream(dst).use { output -> output.write("partial".toByteArray(StandardCharsets.UTF_8)) }
                throw IOException("disk full")
            },
        )

        assertFailure(result)
        assertFalse(File(File(filesDir, "backups"), "kanji_anki_simple_${timestamp(now)}.db").exists())
    }

    @Test
    fun backupDatabaseLeavesIncompleteBackupWhenFailedCopyCannotBeDeleted() {
        val db = temp.newFile("kanji_anki_simple.db")
        write(db, "db")
        val filesDir = temp.newFolder("files")
        val now = 1_778_832_000_000L

        val result = DatabaseBackupWorker.backupDatabase(
            db,
            filesDir,
            now,
            { },
            { _, dst ->
                assertTrue(dst.mkdirs())
                FileOutputStream(File(dst, "partial")).use { output ->
                    output.write("partial".toByteArray(StandardCharsets.UTF_8))
                }
                throw IOException("copy died after creating a non-empty destination")
            },
        )

        assertFailure(result)
        val incomplete = File(File(filesDir, "backups"), "kanji_anki_simple_${timestamp(now)}.db")
        assertTrue(incomplete.isDirectory)
        assertTrue(File(incomplete, "partial").isFile)
    }

    @Test
    fun backupDatabaseContinuesWhenCheckpointFails() {
        val db = temp.newFile("kanji_anki_simple.db")
        write(db, "db")
        val filesDir = temp.newFolder("files")

        val result = DatabaseBackupWorker.backupDatabase(
            db,
            filesDir,
            1_778_832_000_000L,
            { throw IllegalStateException("locked") },
            DatabaseBackupWorker::copyFile,
        )

        assertSuccess(result)
        assertTrue(File(File(filesDir, "backups"), "kanji_anki_simple_${timestamp(1_778_832_000_000L)}.db").isFile)
    }

    @Test
    fun checkpointSkipsCloseWhenDatabaseCannotBeOpened() {
        val opened = booleanArrayOf(false)

        DatabaseBackupWorker.checkpoint(temp.root) { dbFile ->
            opened[0] = true
            throw IOException("cannot open")
        }

        assertTrue(opened[0])
    }

    @Test
    fun checkpointClosesDatabaseAndToleratesCloseFailure() {
        val database = FakeCheckpointDatabase(true)

        DatabaseBackupWorker.checkpoint(temp.root) { database }

        assertEquals(1, database.checkpointCount)
        assertEquals(1, database.closeCount)
    }

    @Test
    fun copyFileWritesCompleteBytesAndFlushesDestination() {
        val src = temp.newFile("source.db")
        val dst = File(temp.root, "copy.db")
        val content = ByteArray(96_000) { index -> (index * 17 + 3).toByte() }
        FileOutputStream(src).use { output -> output.write(content) }

        DatabaseBackupWorker.copyFile(src, dst)

        assertTrue(dst.isFile)
        assertArrayEquals(content, read(dst))
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

    private class FakeCheckpointDatabase(private val failClose: Boolean) : DatabaseBackupWorker.CheckpointDatabase {
        var checkpointCount = 0
        var closeCount = 0

        override fun checkpoint() {
            checkpointCount++
        }

        override fun close() {
            closeCount++
            if (failClose) {
                throw IOException("close failed")
            }
        }
    }
}
