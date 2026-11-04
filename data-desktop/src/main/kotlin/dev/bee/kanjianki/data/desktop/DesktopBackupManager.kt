package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.core.DatabaseBackupPolicy
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates timestamped WAL-safe backups of a desktop profile database and prunes
 * them with the shared tiered 7-daily / 4-weekly retention policy
 * ([DatabaseBackupPolicy]). Backups live in the profile's `backups/` directory
 * ([DesktopStorageLayout.BACKUPS_DIR_NAME]); each is a gzip
 * `kanji_anki_simple_<timestamp>.db.gz` produced by [DesktopBackupSnapshotter].
 *
 * The timestamp comes from the caller (an injected `nowMillis`) rather than a
 * wall clock so the operation is deterministic and testable, matching the pure
 * policy in `:core`.
 */
object DesktopBackupManager {
    data class BackupResult(
        val file: Path,
        val gzipSizeBytes: Long,
        val prunedFiles: List<Path>,
    )

    /**
     * Snapshots [databasePath] into [backupsDir] under a timestamped name and
     * prunes older backups. Creates [backupsDir] if missing. A name collision at
     * [nowMillis] resolution advances by whole seconds until a free slot is
     * found, up to 60 tries.
     *
     * @throws IOException on snapshot or filesystem failure.
     */
    @Throws(IOException::class)
    fun createBackup(
        databasePath: Path,
        backupsDir: Path,
        nowMillis: Long,
    ): BackupResult {
        Files.createDirectories(backupsDir)
        val destination = availableBackupFile(backupsDir, nowMillis)
        val size = DesktopBackupSnapshotter.snapshot(databasePath, destination)
        val pruned = prune(backupsDir)
        return BackupResult(destination, size, pruned)
    }

    /**
     * Deletes the backups the tiered retention policy no longer keeps. Returns
     * the files actually deleted. Best effort: a file that cannot be deleted is
     * left in place and omitted from the result.
     */
    fun prune(backupsDir: Path): List<Path> {
        val toPrune = DatabaseBackupPolicy.oldBackupsToPrune(backupsDir.toFile())
        val deleted = ArrayList<Path>(toPrune.size)
        for (file in toPrune) {
            val path = file.toPath()
            try {
                if (Files.deleteIfExists(path)) deleted.add(path)
            } catch (_: IOException) {
                // Leave a stubborn file in place; it will be reconsidered next run.
            }
        }
        return deleted
    }

    /** Lists existing backup files newest-name-first (lexicographic on timestamp). */
    fun listBackups(backupsDir: Path): List<Path> {
        if (!Files.isDirectory(backupsDir)) return emptyList()
        Files.list(backupsDir).use { stream ->
            return stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".db.gz") }
                .sorted(compareByDescending { it.fileName.toString() })
                .toList()
        }
    }

    private fun availableBackupFile(backupsDir: Path, nowMillis: Long): Path {
        for (offsetSeconds in 0L until 60L) {
            // backupFile derives `<backups>/kanji_anki_simple_<ts>.db.gz`; only the
            // file name matters here, so the parent argument is arbitrary.
            val name = DatabaseBackupPolicy
                .backupFile(backupsDir.toFile(), nowMillis + offsetSeconds * 1_000L)
                .name
            val candidate = backupsDir.resolve(name)
            if (!Files.exists(candidate)) return candidate
        }
        throw IOException("Unable to allocate a unique backup name")
    }
}
