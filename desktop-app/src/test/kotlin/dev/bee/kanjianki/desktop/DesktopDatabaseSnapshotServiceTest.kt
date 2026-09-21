package dev.bee.kanjianki.desktop

import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DesktopDatabaseSnapshotServiceTest {
    private val database: Path = Paths.get("/profile/kanji_anki_simple.db")
    private val destination: Path = Paths.get("/backups/kani-2026-08-07.db.gz")

    @Test
    fun reportsTheBytesTheSnapshotterWrote() {
        var seenDatabase: Path? = null
        var seenDestination: Path? = null
        val service = DesktopDatabaseSnapshotService(
            databaseFile = database,
            snapshot = { from, to ->
                seenDatabase = from
                seenDestination = to
                4_096L
            },
        )

        val result = service.createSnapshot(destination)

        assertEquals(4_096L, result.bytesWritten)
        // The service owns the source path; the caller only names where it goes. A
        // caller-supplied source would let a backup read a file outside the profile.
        assertEquals(database, seenDatabase)
        assertEquals(destination, seenDestination)
    }

    @Test
    fun turnsAnEmptySnapshotIntoAnIoFailureRatherThanAnArgumentError() {
        // `DatabaseSnapshotResult` rejects zero in its own `init`, so building one from
        // an empty vacuum would throw IllegalArgumentException — a programming-error
        // type — out of what is an I/O outcome. A caller catching IOException around a
        // backup would miss it and the export would crash instead of failing.
        for (reported in listOf(0L, -1L)) {
            val service = DesktopDatabaseSnapshotService(
                databaseFile = database,
                snapshot = { _, _ -> reported },
            )

            val failure = assertThrows(IOException::class.java) {
                service.createSnapshot(destination)
            }
            assertEquals(
                "database snapshot wrote no bytes to $destination",
                failure.message,
            )
        }
    }

    @Test
    fun letsTheSnapshottersOwnIoFailurePropagate() {
        val service = DesktopDatabaseSnapshotService(
            databaseFile = database,
            snapshot = { _, _ -> throw IOException("VACUUM INTO produced no database") },
        )

        // Not wrapped and not swallowed: the snapshotter's message names what actually
        // went wrong, and re-wrapping it would bury that behind this adapter's wording.
        val failure = assertThrows(IOException::class.java) {
            service.createSnapshot(destination)
        }
        assertEquals("VACUUM INTO produced no database", failure.message)
    }
}
