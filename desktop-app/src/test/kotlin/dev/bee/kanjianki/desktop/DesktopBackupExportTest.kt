package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.platform.FilePicker
import dev.bee.kanjianki.platform.PlatformFileReference
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup-export flow: pick → snapshot, with the picker and the snapshot call as
 * seams so the AWT dialog and the real VACUUM-INTO writer are not needed here.
 */
class DesktopBackupExportTest {
    private val roots = ArrayList<Path>()

    @After
    fun tearDown() {
        roots.asReversed().forEach { root ->
            if (!Files.exists(root)) return@forEach
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test
    fun aChosenDestinationIsSnapshottedFromTheDatabase() {
        val db = Files.writeString(root().resolve("live.db"), "database")
        val chosen = root().resolve("backup.db.gz")
        var snapshotSource: Path? = null
        var snapshotDest: Path? = null
        val export = DesktopBackupExport(
            picker = pickerReturning(chosen),
            databaseFile = db,
            pathOf = { chosen },
            snapshot = { source, destination ->
                snapshotSource = source
                snapshotDest = destination
                4096L
            },
        )

        var result: DesktopBackupExport.Result? = null
        export.run { result = it }

        assertEquals(db, snapshotSource)
        assertEquals(chosen, snapshotDest)
        assertEquals(DesktopBackupExport.Result.Exported(4096L), result)
    }

    @Test
    fun aCancelledDialogSnapshotsNothing() {
        var snapshotted = false
        val export = DesktopBackupExport(
            picker = pickerReturning(null),
            databaseFile = root().resolve("live.db"),
            pathOf = { error("must not resolve on cancel") },
            snapshot = { _, _ -> snapshotted = true; 0L },
        )

        var result: DesktopBackupExport.Result? = null
        export.run { result = it }

        assertEquals(DesktopBackupExport.Result.Cancelled, result)
        assertTrue(!snapshotted)
    }

    @Test
    fun anUnresolvableReferenceIsAFailureNotACrash() {
        val export = DesktopBackupExport(
            picker = pickerReturning(root().resolve("x.gz")),
            databaseFile = root().resolve("live.db"),
            pathOf = { null },
            snapshot = { _, _ -> error("must not snapshot without a path") },
        )

        var result: DesktopBackupExport.Result? = null
        export.run { result = it }

        assertTrue(result is DesktopBackupExport.Result.Failed)
    }

    @Test
    fun aSnapshotFailureIsReportedRatherThanThrown() {
        val chosen = root().resolve("backup.db.gz")
        val export = DesktopBackupExport(
            picker = pickerReturning(chosen),
            databaseFile = root().resolve("live.db"),
            pathOf = { chosen },
            snapshot = { _, _ -> throw java.io.IOException("disk full") },
        )

        var result: DesktopBackupExport.Result? = null
        export.run { result = it }

        assertEquals(DesktopBackupExport.Result.Failed("disk full"), result)
    }

    private fun pickerReturning(path: Path?): FilePicker = FilePicker { _, onResult ->
        onResult(path?.let { PlatformFileReference.create(it.toString(), it.fileName.toString()) })
    }

    private fun root(): Path = Files.createTempDirectory("kani-backup-export").also(roots::add)
}
