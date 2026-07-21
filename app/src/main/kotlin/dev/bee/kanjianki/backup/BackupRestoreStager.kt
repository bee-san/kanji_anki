package dev.bee.kanjianki.backup

import dev.bee.kanjianki.core.DatabaseBackupAvailabilityPolicy
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object BackupRestoreStager {
    const val RESTORE_DIR_NAME = "restore"
    const val STAGED_FILE_NAME = "${DatabaseBackupPolicy.DB_NAME}.staged"
    const val MARKER_FILE_NAME = "restore.marker"
    const val VALIDATING_SUFFIX = ".db.validating"
    private const val READY_MARKER_FORMAT = "2"
    private const val READY_MARKER_PHASE = "safety_ready"
    private const val MAX_MARKER_BYTES = 1_024L

    internal enum class MarkerState {
        MISSING,
        LEGACY,
        SAFETY_READY,
        INVALID,
    }

    internal fun interface AtomicFileReplacer {
        @Throws(IOException::class)
        fun replace(source: File, destination: File)
    }

    internal fun interface AtomicPathMove {
        @Throws(IOException::class)
        fun move(source: Path, destination: Path)
    }

    private val systemAtomicReplacer = AtomicFileReplacer(::moveAtomically)

    @JvmStatic
    fun restoreDir(filesDir: File): File = File(filesDir, RESTORE_DIR_NAME)

    @JvmStatic
    fun stagedFile(filesDir: File): File = File(restoreDir(filesDir), STAGED_FILE_NAME)

    @JvmStatic
    fun markerFile(filesDir: File): File = File(restoreDir(filesDir), MARKER_FILE_NAME)

    @JvmStatic
    fun cleanupOrphanValidationFiles(restoreDir: File) {
        restoreDir.listFiles { file -> file.isFile && file.name.endsWith(VALIDATING_SUFFIX) }
            ?.forEach(::deleteBestEffort)
    }

    @JvmStatic
    fun deleteBestEffort(file: File) {
        if (file.exists() && !file.delete()) {
            file.deleteOnExit()
        }
    }

    @JvmStatic
    fun stage(
        validated: ValidatedBackup,
        filesDir: File,
        apiLevel: Int,
        atomicReplacer: AtomicFileReplacer = systemAtomicReplacer,
        directorySynchronizer: DirectorySynchronizer = SystemDirectorySynchronizer,
    ): Boolean {
        if (!DatabaseBackupAvailabilityPolicy.forAndroidApi(apiLevel).operationsAllowed) return false
        val restoreDir = restoreDir(filesDir)
        if ((!restoreDir.exists() && !restoreDir.mkdirs()) || !restoreDir.isDirectory) return false
        val staged = stagedFile(filesDir)
        val marker = markerFile(filesDir)
        // Never overwrite an earlier pending or post-replacement recovery state.
        if (staged.exists() || marker.exists()) return false
        var published = false
        return try {
            // Staging is one durable atomic publication. Startup publishes a versioned
            // marker only after its pre-restore safety archive is durable.
            atomicReplacer.replace(validated.databaseFile, staged)
            published = true
            syncMoveParents(validated.databaseFile, staged, directorySynchronizer)
            true
        } catch (_: IOException) {
            published
        } catch (_: RuntimeException) {
            published
        }
    }

    @Throws(IOException::class)
    internal fun ensureRecoveryMarker(
        marker: File,
        atomicReplacer: AtomicFileReplacer = systemAtomicReplacer,
        directorySynchronizer: DirectorySynchronizer = SystemDirectorySynchronizer,
    ) {
        when (markerState(marker)) {
            MarkerState.SAFETY_READY -> {
                directorySynchronizer.sync(requireParent(marker))
                return
            }
            MarkerState.MISSING, MarkerState.LEGACY -> Unit
            MarkerState.INVALID -> throw IOException("Restore marker is invalid")
        }
        val temp = File(marker.parentFile, marker.name + ".tmp")
        deleteBestEffort(temp)
        try {
            writeMarker(temp, "unknown", System.currentTimeMillis())
            atomicReplacer.replace(temp, marker)
            syncMoveParents(temp, marker, directorySynchronizer)
        } catch (error: IOException) {
            deleteBestEffort(temp)
            throw error
        } catch (error: RuntimeException) {
            deleteBestEffort(temp)
            throw error
        }
    }

    internal fun markerState(marker: File): MarkerState {
        if (!marker.exists()) return MarkerState.MISSING
        if (!marker.isFile || marker.length() !in 1L..MAX_MARKER_BYTES) return MarkerState.INVALID
        val lines = try {
            marker.readLines(Charsets.UTF_8)
        } catch (_: IOException) {
            return MarkerState.INVALID
        } catch (_: RuntimeException) {
            return MarkerState.INVALID
        }
        val values = lines.mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
        }.toMap()
        val readySourceName = values["source_name"]?.trim()
        val readyTimestamp = values["staged_at"]?.toLongOrNull()
        if (values["format"] == READY_MARKER_FORMAT &&
            values["phase"] == READY_MARKER_PHASE &&
            !readySourceName.isNullOrEmpty() &&
            readyTimestamp != null &&
            readyTimestamp >= 0L
        ) {
            return MarkerState.SAFETY_READY
        }
        val legacyTimestamp = values["staged_at"]?.toLongOrNull()
        return if (values.containsKey("source_name") && legacyTimestamp != null &&
            !values.containsKey("format") && !values.containsKey("phase")
        ) {
            MarkerState.LEGACY
        } else {
            MarkerState.INVALID
        }
    }

    @Throws(IOException::class)
    internal fun syncMoveParents(
        source: File,
        destination: File,
        directorySynchronizer: DirectorySynchronizer,
    ) {
        val sourceParent = requireParent(source)
        val destinationParent = requireParent(destination)
        // Persist the destination name before the source-name removal. The ready marker
        // is already durable before the only cross-directory destructive replacement.
        directorySynchronizer.sync(destinationParent)
        if (sourceParent.absoluteFile != destinationParent.absoluteFile) {
            directorySynchronizer.sync(sourceParent)
        }
    }

    @Throws(IOException::class)
    internal fun moveAtomically(
        source: File,
        destination: File,
        pathMove: AtomicPathMove = AtomicPathMove { sourcePath, destinationPath ->
            Files.move(
                sourcePath,
                destinationPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        },
    ) {
        destination.parentFile?.let { parent ->
            if ((!parent.exists() && !parent.mkdirs()) || !parent.isDirectory) {
                throw IOException("Unable to create destination directory")
            }
        }
        // There is deliberately no ordinary move/copy fallback. If namespace-atomic
        // replacement is unavailable, preserve both recovery sides and fail closed.
        pathMove.move(source.toPath(), destination.toPath())
    }

    private fun writeMarker(file: File, sourceName: String, nowMillis: Long) {
        val sanitizedSource = sourceName.replace('\n', ' ').replace('\r', ' ').take(240)
        FileOutputStream(file).use { output ->
            output.write(
                (
                    "format=$READY_MARKER_FORMAT\n" +
                        "phase=$READY_MARKER_PHASE\n" +
                        "source_name=$sanitizedSource\n" +
                        "staged_at=$nowMillis\n"
                ).toByteArray(Charsets.UTF_8),
            )
            output.fd.sync()
        }
    }

    private fun requireParent(file: File): File {
        return file.parentFile ?: throw IOException("Recovery file has no parent directory")
    }
}
