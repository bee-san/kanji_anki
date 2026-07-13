package dev.bee.kanjianki.backup

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupRestoreStagerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun supportedStagePublishesOnlyTheDatabaseAndDefersMarker() {
        val filesDir = temp.newFolder("supported-files")
        val restoreDir = BackupRestoreStager.restoreDir(filesDir).apply { assertTrue(mkdirs()) }
        val source = File(restoreDir, "validated.db").apply { writeText("validated") }
        val syncedDirectories = ArrayList<File>()

        val staged = BackupRestoreStager.stage(
            ValidatedBackup(source, "selected.db.gz"),
            filesDir,
            apiLevel = 30,
            directorySynchronizer = DirectorySynchronizer { directory ->
                syncedDirectories.add(directory.canonicalFile)
            },
        )

        assertTrue(staged)
        assertFalse(source.exists())
        assertTrue(BackupRestoreStager.stagedFile(filesDir).isFile)
        assertFalse(BackupRestoreStager.markerFile(filesDir).exists())
        assertEquals(listOf(restoreDir.canonicalFile), syncedDirectories)
    }

    @Test
    fun stageAcceptsPublishedStateWhenStartupMustRetryDirectorySync() {
        val filesDir = temp.newFolder("sync-failure-files")
        val restoreDir = BackupRestoreStager.restoreDir(filesDir).apply { assertTrue(mkdirs()) }
        val source = File(restoreDir, "validated.db").apply { writeText("validated") }

        val staged = BackupRestoreStager.stage(
            ValidatedBackup(source, "selected.db.gz"),
            filesDir,
            apiLevel = 30,
            directorySynchronizer = DirectorySynchronizer {
                throw java.io.IOException("directory fsync failed")
            },
        )

        assertTrue(staged)
        assertFalse(source.exists())
        assertEquals("validated", BackupRestoreStager.stagedFile(filesDir).readText())
        assertFalse(BackupRestoreStager.markerFile(filesDir).exists())
    }

    @Test
    fun unsupportedStageDoesNotCreateRecoveryStateOrConsumeValidatedSource() {
        val filesDir = temp.newFolder("unsupported-files")
        val source = temp.newFile("legacy-validated.db").apply { writeText("validated") }

        val staged = BackupRestoreStager.stage(
            ValidatedBackup(source, "selected.db.gz"),
            filesDir,
            apiLevel = 29,
        )

        assertFalse(staged)
        assertTrue(source.isFile)
        assertFalse(BackupRestoreStager.restoreDir(filesDir).exists())
    }

    @Test
    fun stageRefusesToOverwriteEarlierPendingRestore() {
        val filesDir = temp.newFolder("pending-files")
        val restoreDir = BackupRestoreStager.restoreDir(filesDir).apply { assertTrue(mkdirs()) }
        val earlier = BackupRestoreStager.stagedFile(filesDir).apply { writeText("earlier") }
        val marker = BackupRestoreStager.markerFile(filesDir).apply { writeText("marker") }
        val source = temp.newFile("new-validated.db").apply { writeText("new") }
        var moveCalled = false

        val staged = BackupRestoreStager.stage(
            ValidatedBackup(source, "new.db.gz"),
            filesDir,
            apiLevel = 35,
            atomicReplacer = BackupRestoreStager.AtomicFileReplacer { _, _ -> moveCalled = true },
        )

        assertFalse(staged)
        assertFalse(moveCalled)
        assertTrue(restoreDir.isDirectory)
        assertTrue(source.isFile)
        assertEquals("earlier", earlier.readText())
        assertEquals("marker", marker.readText())
    }

    @Test
    fun atomicMoveUnsupportedHasNoOrdinaryReplacementFallback() {
        val source = temp.newFile("move-source.db")
        val destination = temp.newFile("move-destination.db")
        val sourceBytes = "source".toByteArray()
        val destinationBytes = "destination".toByteArray()
        source.writeBytes(sourceBytes)
        destination.writeBytes(destinationBytes)
        var attempts = 0

        try {
            BackupRestoreStager.moveAtomically(
                source,
                destination,
                BackupRestoreStager.AtomicPathMove { sourcePath, destinationPath ->
                    attempts++
                    throw AtomicMoveNotSupportedException(
                        sourcePath.toString(),
                        destinationPath.toString(),
                        "not supported",
                    )
                },
            )
            throw AssertionError("AtomicMoveNotSupportedException expected")
        } catch (_: AtomicMoveNotSupportedException) {
            // Expected.
        }

        assertEquals(1, attempts)
        assertArrayEquals(sourceBytes, source.readBytes())
        assertArrayEquals(destinationBytes, destination.readBytes())
    }
}
