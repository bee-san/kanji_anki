package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.backup.core.RestoreMarkerCodec
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Applies a validated desktop restore before any repository opens the profile
 * database. Mirrors the Android `StagedRestoreApplier` sequence on `java.nio`:
 * take a pre-restore safety backup, publish a durable [RestoreMarkerCodec]
 * SAFETY_READY marker, atomically replace the live database, delete stale
 * WAL/SHM sidecars, then delete the marker. Each step fsyncs the relevant
 * directory so a crash resumes at a well-defined point.
 *
 * A marker present without a staged file means the atomic replace already
 * completed before a crash; only the idempotent sidecar/marker cleanup runs. Any
 * marker-bearing failure blocks database use until it is resolved.
 */
object DesktopStagedRestoreApplier {
    const val STAGED_FILE_NAME = "restore-staged.db"
    const val MARKER_FILE_NAME = "restore.marker"

    enum class Result {
        /** No restore was pending. */
        NO_OP,

        /** The restore completed and the live database now holds the backup. */
        APPLIED,

        /** A recoverable failure; the restore should be retried on next launch. */
        RETRY_NEEDED,

        /** A marker-bearing failure; the app must not open the database. */
        BLOCK_STARTUP,
    }

    enum class Step {
        SAFETY_BACKUP_CREATED,
        MARKER_READY,
        DATABASE_REPLACED,
        SIDECARS_DELETED,
        MARKER_DELETED,
    }

    fun interface StepHook {
        fun after(step: Step)
    }

    /**
     * Stages [validatedDatabase] as the pending restore for [profileDir]: moves
     * it to the canonical staged path so [apply] will pick it up on next launch.
     * The staged file lives in the profile's `restore/` directory. No ready
     * marker is written here — the marker only becomes SAFETY_READY during
     * [apply], after the pre-restore safety backup exists, so a crash before the
     * safety backup never loses the live database.
     */
    @Throws(IOException::class)
    fun stage(profileDir: Path, validatedDatabase: Path) {
        val restoreDir = restoreDir(profileDir)
        Files.createDirectories(restoreDir)
        val staged = restoreDir.resolve(STAGED_FILE_NAME)
        moveAtomically(validatedDatabase, staged)
        fsyncDirectory(restoreDir)
    }

    /**
     * Applies a pending restore for [profileDir] if one exists. Safe to call on
     * every launch: returns [Result.NO_OP] with a single existence check when no
     * restore directory is present.
     */
    fun apply(
        profileDir: Path,
        nowMillis: Long,
        stepHook: StepHook = StepHook {},
    ): Result {
        val restoreDir = restoreDir(profileDir)
        if (!Files.isDirectory(restoreDir)) return Result.NO_OP
        return try {
            applyOrThrow(profileDir, restoreDir, nowMillis, stepHook)
        } catch (_: IOException) {
            if (Files.exists(restoreDir.resolve(MARKER_FILE_NAME))) {
                Result.BLOCK_STARTUP
            } else {
                Result.RETRY_NEEDED
            }
        }
    }

    @Throws(IOException::class)
    private fun applyOrThrow(
        profileDir: Path,
        restoreDir: Path,
        nowMillis: Long,
        stepHook: StepHook,
    ): Result {
        val staged = restoreDir.resolve(STAGED_FILE_NAME)
        val marker = restoreDir.resolve(MARKER_FILE_NAME)
        val databaseFile = profileDir.resolve(DesktopStorageLayout.DATABASE_FILE_NAME)

        if (!Files.exists(staged) && !Files.exists(marker)) {
            deleteEmptyRestoreDir(restoreDir)
            return Result.NO_OP
        }

        // Marker but no staged file: the atomic replace already ran. Finish only
        // the idempotent sidecar/marker cleanup.
        if (!Files.exists(staged)) {
            return finishMarkerOnlyRestore(restoreDir, databaseFile, marker, stepHook)
        }

        val markerState = markerState(marker)
        if (markerState == RestoreMarkerCodec.MarkerState.INVALID) {
            throw IOException("Restore marker is invalid")
        }
        if (markerState != RestoreMarkerCodec.MarkerState.SAFETY_READY) {
            if (Files.exists(databaseFile)) {
                createSafetyBackup(databaseFile, profileDir, nowMillis)
            }
        }
        stepHook.after(Step.SAFETY_BACKUP_CREATED)

        ensureReadyMarker(marker, staged, nowMillis)
        stepHook.after(Step.MARKER_READY)

        moveAtomically(staged, databaseFile)
        fsyncDirectory(requireParent(databaseFile))
        stepHook.after(Step.DATABASE_REPLACED)

        deleteSidecars(databaseFile)
        fsyncDirectory(requireParent(databaseFile))
        stepHook.after(Step.SIDECARS_DELETED)

        deleteRequired(marker)
        fsyncDirectory(restoreDir)
        stepHook.after(Step.MARKER_DELETED)
        deleteEmptyRestoreDir(restoreDir)
        return Result.APPLIED
    }

    @Throws(IOException::class)
    private fun finishMarkerOnlyRestore(
        restoreDir: Path,
        databaseFile: Path,
        marker: Path,
        stepHook: StepHook,
    ): Result {
        if (markerState(marker) != RestoreMarkerCodec.MarkerState.SAFETY_READY) {
            throw IOException("Restore marker is invalid")
        }
        if (!Files.isRegularFile(databaseFile)) {
            throw IOException("Restored database is missing after replacement")
        }
        deleteSidecars(databaseFile)
        fsyncDirectory(requireParent(databaseFile))
        stepHook.after(Step.SIDECARS_DELETED)
        deleteRequired(marker)
        fsyncDirectory(restoreDir)
        stepHook.after(Step.MARKER_DELETED)
        deleteEmptyRestoreDir(restoreDir)
        return Result.APPLIED
    }

    @Throws(IOException::class)
    private fun createSafetyBackup(databaseFile: Path, profileDir: Path, nowMillis: Long) {
        val backupsDir = profileDir.resolve(DesktopStorageLayout.BACKUPS_DIR_NAME)
        DesktopBackupManager.createBackup(databaseFile, backupsDir, nowMillis)
    }

    private fun markerState(marker: Path): RestoreMarkerCodec.MarkerState {
        if (!Files.exists(marker)) return RestoreMarkerCodec.MarkerState.MISSING
        val tooLarge = try {
            Files.size(marker) > RestoreMarkerCodec.MAX_MARKER_BYTES
        } catch (_: IOException) {
            true
        }
        val rawText = if (tooLarge) {
            null
        } else {
            try {
                String(Files.readAllBytes(marker), StandardCharsets.UTF_8)
            } catch (_: IOException) {
                null
            }
        }
        return RestoreMarkerCodec.classify(present = true, tooLargeOrUnreadable = tooLarge, rawText = rawText)
    }

    @Throws(IOException::class)
    private fun ensureReadyMarker(marker: Path, staged: Path, nowMillis: Long) {
        if (markerState(marker) == RestoreMarkerCodec.MarkerState.SAFETY_READY) return
        writeReadyMarker(marker, staged.fileName.toString(), nowMillis)
    }

    @Throws(IOException::class)
    private fun writeReadyMarker(marker: Path, sourceName: String, nowMillis: Long) {
        val text = RestoreMarkerCodec.encodeReady(
            RestoreMarkerCodec.ReadyMarker(sourceName = sourceName, stagedAtMillis = nowMillis),
        )
        val temp = marker.resolveSibling(marker.fileName.toString() + ".tmp")
        Files.write(
            temp,
            text.toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        moveAtomically(temp, marker)
        marker.parent?.let(::fsyncDirectory)
    }

    @Throws(IOException::class)
    private fun moveAtomically(source: Path, destination: Path) {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: UnsupportedOperationException) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    @Throws(IOException::class)
    private fun deleteSidecars(databaseFile: Path) {
        deleteRequired(databaseFile.resolveSibling(databaseFile.fileName.toString() + "-wal"))
        deleteRequired(databaseFile.resolveSibling(databaseFile.fileName.toString() + "-shm"))
    }

    @Throws(IOException::class)
    private fun deleteRequired(file: Path) {
        Files.deleteIfExists(file)
    }

    private fun requireParent(file: Path): Path =
        file.parent ?: throw IOException("File has no parent directory: $file")

    private fun deleteEmptyRestoreDir(restoreDir: Path) {
        try {
            Files.list(restoreDir).use { stream ->
                if (stream.findFirst().isEmpty) Files.deleteIfExists(restoreDir)
            }
        } catch (_: IOException) {
            // Leave a non-empty or unreadable directory in place.
        }
    }

    private fun fsyncDirectory(directory: Path) {
        // Directory fsync is best effort: many JVM/OS combinations refuse to open
        // a directory channel, and on those the enclosing atomic moves already
        // provide the ordering guarantee.
        try {
            java.nio.channels.FileChannel.open(directory, StandardOpenOption.READ).use { channel ->
                channel.force(true)
            }
        } catch (_: IOException) {
            // ignored
        }
    }

    private fun restoreDir(profileDir: Path): Path = profileDir.resolve("restore")
}
