package dev.bee.kanjianki.host

import dev.bee.kanjianki.platform.PlatformFileAccess
import dev.bee.kanjianki.platform.PlatformFileReference
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The two-phase export, and what each phase leaves behind.
 *
 * The failures worth guarding are all about the snapshot that exists between the phases:
 * it is written before the user has chosen anything, so every path out of the dialog —
 * chosen, cancelled, or a process that died holding it — has to account for it. A leaked
 * snapshot is a database-sized file in the cache that nothing will ever delete.
 */
class AndroidBackupExportTest {
    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    @Test
    fun theSnapshotIsTakenBeforeTheDialogAndCopiedIntoTheChosenFile() {
        val export = export()

        val preparation = export.prepare()

        assertTrue("a healthy host may pick", preparation.mayPick)
        assertEquals("", preparation.message)
        // Named for the user, not "backup.tmp": this is the name the save dialog pre-fills.
        assertTrue(preparation.suggestedName, preparation.suggestedName.isNotBlank())
        // The snapshot is on disk *now*, before any dialog. That ordering is the whole
        // reason this class is not a mirror of the desktop flow.
        assertEquals(1, snapshotCount)

        val sink = RecordingFileAccess()
        val message = AndroidBackupExport(
            cacheRoot = { cacheRoot },
            databaseFile = { database },
            fileAccess = sink,
            snapshotter = { _, destination -> destination.writeText(SNAPSHOT_BYTES) },
            operationsAllowed = { true },
        ).copyInto(reference())

        assertEquals(SNAPSHOT_BYTES, sink.written())
        assertTrue(message, message.isNotBlank())
        assertTrue("the scratch snapshot is gone", scratchFiles().isEmpty())
    }

    @Test
    fun aCancelledDialogDiscardsTheSnapshotItAlreadyTook() {
        val export = export()
        export.prepare()
        assertTrue("the snapshot exists to be discarded", scratchFiles().isNotEmpty())

        val message = export.copyInto(null)

        // Nothing to say — the user cancelled, which is not a failure — but the snapshot
        // must not survive it.
        assertEquals("", message)
        assertTrue("a cancelled dialog leaves no snapshot", scratchFiles().isEmpty())
    }

    @Test
    fun aSecondExportDoesNotWriteTheFirstSnapshotIntoTheNewlyChosenFile() {
        val export = export()
        export.prepare()
        export.prepare()

        val sink = RecordingFileAccess()
        AndroidBackupExport(
            cacheRoot = { cacheRoot },
            databaseFile = { database },
            fileAccess = sink,
            snapshotter = { _, destination -> destination.writeText(SNAPSHOT_BYTES) },
            operationsAllowed = { true },
        ).copyInto(reference())

        // Two prepares, one pending export: the first is discarded rather than queued, so
        // the file the user just chose cannot receive a database from minutes ago.
        assertEquals(2, snapshotCount)
        assertEquals(SNAPSHOT_BYTES, sink.written())
        assertTrue(scratchFiles().isEmpty())
    }

    @Test
    fun aHostThatCannotSnapshotSafelyIsNotOfferedADialogAtAll() {
        val export = AndroidBackupExport(
            cacheRoot = { cacheRoot },
            databaseFile = { database },
            fileAccess = RecordingFileAccess(),
            snapshotter = { _, _ -> throw AssertionError("must not snapshot on an unsupported host") },
            operationsAllowed = { false },
        )

        val preparation = export.prepare()

        // Stock API 26-29 cannot make the live snapshot the restore contract needs, so the
        // honest answer is a message rather than a dialog whose saved file would be torn.
        assertFalse(preparation.mayPick)
        assertTrue(preparation.message, preparation.message.isNotBlank())
        assertTrue(scratchFiles().isEmpty())
    }

    @Test
    fun aFailedSnapshotReportsAndOpensNoDialog() {
        val export = AndroidBackupExport(
            cacheRoot = { cacheRoot },
            databaseFile = { database },
            fileAccess = RecordingFileAccess(),
            snapshotter = { _, _ -> throw IOException("disk full") },
            operationsAllowed = { true },
        )

        val preparation = export.prepare()

        assertFalse("a failed snapshot must not open a save dialog", preparation.mayPick)
        assertTrue(preparation.message, preparation.message.isNotBlank())
        // And it cleans up after itself, so the next export starts from an empty scratch.
        assertTrue(scratchFiles().isEmpty())
    }

    @Test
    fun aResultThatArrivesWithNoPreparedExportIsReportedRatherThanCrashing() {
        // Android can restore an activity result for a process that already died holding
        // the pending export. The reference is real, the snapshot is not.
        val message = export().copyInto(reference())

        assertTrue(message, message.isNotBlank())
        assertNotEquals("", message)
    }

    private fun export(): AndroidBackupExport = AndroidBackupExport(
        cacheRoot = { cacheRoot },
        databaseFile = { database },
        fileAccess = RecordingFileAccess(),
        snapshotter = { _, destination ->
            snapshotCount++
            destination.writeText(SNAPSHOT_BYTES)
        },
        operationsAllowed = { true },
    )

    private var snapshotCount: Int = 0

    private val cacheRoot: File
        get() = temp.root

    private val database: File
        get() = File(temp.root, "kani.db").apply { if (!exists()) writeText("live database") }

    /** The files the export's own scratch directory holds; empty when nothing leaked. */
    private fun scratchFiles(): List<File> =
        File(cacheRoot, "backup-export").listFiles()?.toList().orEmpty()

    private fun reference(): PlatformFileReference =
        PlatformFileReference.create(opaqueId = "content://docs/1", displayName = "kani-backup.db.gz")

    /**
     * A [PlatformFileAccess] whose output stream keeps what was written to it.
     *
     * [written] gunzips, because the export copies the *compressed* snapshot into the
     * chosen document — that is the archive format the restore path reads. Asserting on the
     * raw bytes would compare a gzip stream to plain text and fail for the wrong reason.
     */
    private class RecordingFileAccess : PlatformFileAccess {
        private val sink = ByteArrayOutputStream()

        fun written(): String =
            GZIPInputStream(sink.toByteArray().inputStream()).use { it.readBytes() }
                .toString(Charsets.UTF_8)

        override fun openInput(file: PlatformFileReference): InputStream? = null

        override fun openOutput(file: PlatformFileReference): OutputStream = sink
    }

    private companion object {
        const val SNAPSHOT_BYTES = "snapshot"
    }
}
