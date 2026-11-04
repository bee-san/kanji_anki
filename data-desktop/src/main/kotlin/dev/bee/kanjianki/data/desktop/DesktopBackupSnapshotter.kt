package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.data.sql.SqlConnectionMode
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPOutputStream

/**
 * Produces a WAL-safe, transactionally consistent gzip snapshot of a desktop
 * profile database. Uses SQLite `VACUUM INTO` (fully checkpointed, defragmented,
 * includes committed WAL content) on a dedicated connection outside any
 * transaction — `VACUUM` cannot run inside one — then gzips the vacuumed file
 * and publishes it atomically. This is the desktop counterpart to the Android
 * `WalSafeSnapshotOperations`/`VACUUM INTO ?` path; the bundled SQLite build is
 * always new enough, so there is no platform floor to gate on.
 *
 * Every intermediate file is written under the destination's parent and cleaned
 * up on any failure, so a partially written snapshot never survives.
 */
object DesktopBackupSnapshotter {
    private const val VACUUM_SUFFIX = ".vacuum.tmp"
    private const val PARTIAL_SUFFIX = ".partial"

    /**
     * Writes a gzip snapshot of the database at [databasePath] to [destination].
     * [destination] must not already exist. The [databasePath] file must exist.
     *
     * @return the number of compressed bytes written to [destination].
     * @throws IOException on any snapshot, compression, or publication failure.
     */
    @Throws(IOException::class)
    fun snapshot(databasePath: Path, destination: Path): Long {
        if (Files.exists(destination)) {
            throw IOException("Snapshot destination already exists: $destination")
        }
        if (!Files.isRegularFile(databasePath)) {
            throw IOException("Database file does not exist: $databasePath")
        }
        val parent = destination.parent
            ?: throw IOException("Snapshot destination has no parent directory")
        Files.createDirectories(parent)

        val vacuumed = parent.resolve(destination.fileName.toString() + VACUUM_SUFFIX)
        val partial = parent.resolve(destination.fileName.toString() + PARTIAL_SUFFIX)
        deleteScratch(vacuumed)
        deleteScratch(partial)

        try {
            vacuumInto(databasePath, vacuumed)
            if (!Files.isRegularFile(vacuumed) || Files.size(vacuumed) <= 0L) {
                throw IOException("VACUUM INTO produced no database")
            }
            gzip(vacuumed, partial)
            // Atomic publish: the destination only ever appears fully written.
            try {
                Files.move(partial, destination, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: UnsupportedOperationException) {
                Files.move(partial, destination)
            }
            return Files.size(destination)
        } catch (failure: IOException) {
            deleteScratch(partial)
            deleteScratch(destination)
            throw failure
        } catch (failure: RuntimeException) {
            deleteScratch(partial)
            deleteScratch(destination)
            throw IOException("Desktop backup snapshot failed", failure)
        } finally {
            deleteScratch(vacuumed)
        }
    }

    private fun vacuumInto(databasePath: Path, vacuumed: Path) {
        // A dedicated read-write connection, no surrounding transaction: VACUUM
        // must run at statement scope. The escaped path literal is quoted, not
        // parameter-bound, because VACUUM INTO does not accept a bind parameter
        // in a prepared statement across SQLite builds.
        BundledSqlDriver(databasePath.toString()).use { driver ->
            driver.openConnection(SqlConnectionMode.READ_WRITE).use { connection ->
                val target = vacuumed.toAbsolutePath().toString().replace("'", "''")
                connection.execute("VACUUM INTO '$target'")
            }
        }
    }

    private fun gzip(source: Path, destination: Path) {
        Files.newInputStream(source).use { input ->
            Files.newOutputStream(destination).use { rawOutput ->
                gzipStream(rawOutput).use { gzipOutput ->
                    input.copyTo(gzipOutput)
                    gzipOutput.finish()
                }
            }
        }
    }

    private fun gzipStream(output: OutputStream): GZIPOutputStream = GZIPOutputStream(output)

    private fun deleteScratch(path: Path) {
        try {
            Files.deleteIfExists(path)
        } catch (_: IOException) {
            // Best effort: a leftover scratch file is cleared on the next snapshot.
        }
    }
}
