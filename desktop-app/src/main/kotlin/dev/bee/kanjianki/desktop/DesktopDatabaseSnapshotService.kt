package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.data.desktop.DesktopBackupSnapshotter
import dev.bee.kanjianki.platform.DatabaseSnapshotResult
import dev.bee.kanjianki.platform.DatabaseSnapshotService
import java.io.IOException
import java.nio.file.Path

/**
 * Desktop's [DatabaseSnapshotService], over the existing `VACUUM INTO` snapshotter.
 *
 * Only an adapter: `DesktopBackupSnapshotter` already implements the WAL-safe
 * checkpointed snapshot the backup contract requires, and the shared port needed
 * something to bind to. Written here rather than in `:data-desktop` because the port
 * lives in `:platform-contracts` and that module has a single reviewed edge, to
 * `:core` — adding a second one so a data module could name a platform type would widen
 * the graph for an adapter the composition root can hold instead.
 *
 * The empty-snapshot case is the one worth naming. [DatabaseSnapshotResult] rejects a
 * zero byte count in its own `init`, so constructing one from an empty vacuum would
 * throw `IllegalArgumentException` — a programming-error type — from what is really an
 * I/O outcome. A caller catching `IOException` around a backup would miss it and the
 * failure would surface as a crash rather than a failed export. So the zero case is
 * turned into an `IOException` here, before the value is built.
 */
internal class DesktopDatabaseSnapshotService(
    private val databaseFile: Path,
    private val snapshot: (Path, Path) -> Long = DesktopBackupSnapshotter::snapshot,
) : DatabaseSnapshotService {
    @Throws(IOException::class)
    override fun createSnapshot(destination: Path): DatabaseSnapshotResult {
        val bytesWritten = snapshot(databaseFile, destination)
        if (bytesWritten <= 0L) {
            throw IOException("database snapshot wrote no bytes to $destination")
        }
        return DatabaseSnapshotResult(bytesWritten = bytesWritten)
    }
}
