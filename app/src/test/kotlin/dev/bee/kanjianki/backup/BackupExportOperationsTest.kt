package dev.bee.kanjianki.backup

import dev.bee.kanjianki.core.BackupExportPolicy
import dev.bee.kanjianki.platform.PlatformFileAccess
import dev.bee.kanjianki.platform.PlatformFileReference
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupExportOperationsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun exportCopyRoundTripsBytesThroughUriStreamAndReportsGzipSize() {
        val db = temp.newFile("kanji_anki_simple.db")
        val original = "SQLite format 3\u0000durable Kani data".repeat(200).toByteArray()
        db.writeBytes(original)
        val preparation = BackupExportOperations.prepare(
            temp.root,
            db,
            1_778_832_000_000L,
        ) { source, destination -> source.copyTo(destination) }
        assertTrue(preparation is BackupExportPreparation.Ready)
        val prepared = (preparation as BackupExportPreparation.Ready).export
        val copied = ByteArrayOutputStream()

        val result = BackupExportOperations.copyToFile(
            prepared,
            PlatformFileReference.create("content://test/export", "export.db.gz"),
            outputFiles { copied },
        )

        assertTrue(result.success)
        assertEquals(BackupExportPolicy.CopyId.EXPORT_COMPLETE, result.copy.id)
        assertTrue(result.copy.text.contains(formatExpectedSize(prepared.gzipSizeBytes)))
        assertArrayEquals(original, GZIPInputStream(ByteArrayInputStream(copied.toByteArray())).use { it.readBytes() })
        assertFalse(prepared.file.exists())
    }

    @Test
    fun failedDestinationDeletesPreparedTempFile() {
        val db = temp.newFile("db")
        db.writeText("data")
        val prepared = (BackupExportOperations.prepare(temp.root, db, 1L) { source, destination ->
            source.copyTo(destination)
        } as BackupExportPreparation.Ready).export

        val result = BackupExportOperations.copyToFile(
            prepared,
            PlatformFileReference.create("content://test/failure", "failure.db.gz"),
            outputFiles { throw IOException("provider unavailable") },
        )

        assertFalse(result.success)
        assertEquals(BackupExportPolicy.CopyId.EXPORT_WRITE_FAILED, result.copy.id)
        assertFalse(prepared.file.exists())
        assertTrue(prepared.file.parentFile?.listFiles().isNullOrEmpty())
    }

    @Test
    fun failedSnapshotLeavesNoRawOrGzipTemps() {
        val db = temp.newFile("db-fails")

        val preparation = BackupExportOperations.prepare(temp.root, db, 1L) { _, destination ->
            destination.writeText("partial")
            throw IOException("snapshot failed")
        }

        assertTrue(preparation is BackupExportPreparation.Failed)
        assertTrue(File(temp.root, "backup-export").listFiles().isNullOrEmpty())
    }

    @Test
    fun snapshotterThatProducesNoDatabaseFailsWithoutPreparedExport() {
        val db = temp.newFile("db-empty-snapshot")
        db.writeText("source")

        val preparation = BackupExportOperations.prepare(temp.root, db, 2L) { _, _ -> }

        assertTrue(preparation is BackupExportPreparation.Failed)
        assertTrue(File(temp.root, "backup-export").listFiles().isNullOrEmpty())
    }

    @Test
    fun nonRemovableStaleScratchAbortsBeforeSnapshot() {
        val db = temp.newFile("db-stale-scratch")
        db.writeText("source")
        val exportDir = File(temp.root, "backup-export").apply { assertTrue(mkdirs()) }
        val stubborn = File(exportDir, "stubborn").apply { assertTrue(mkdirs()) }
        File(stubborn, "child").writeText("orphan")
        var snapshotCalled = false

        val preparation = BackupExportOperations.prepare(temp.root, db, 3L) { _, _ ->
            snapshotCalled = true
        }

        assertTrue(preparation is BackupExportPreparation.Failed)
        assertFalse(snapshotCalled)
        assertTrue(File(stubborn, "child").isFile)
    }

    private fun formatExpectedSize(bytes: Long): String {
        return when {
            bytes < 1_024L -> "$bytes B"
            bytes < 1_048_576L -> String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1_024.0)
            else -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)
        }
    }

    private fun outputFiles(open: () -> OutputStream): PlatformFileAccess =
        object : PlatformFileAccess {
            override fun openInput(file: PlatformFileReference): InputStream? = null

            override fun openOutput(file: PlatformFileReference): OutputStream = open()
        }
}
