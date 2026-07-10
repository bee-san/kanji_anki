package dev.bee.kanjianki.backup

import dev.bee.kanjianki.core.DatabaseBackupPolicy
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object BackupRestoreStager {
    const val RESTORE_DIR_NAME = "restore"
    const val STAGED_FILE_NAME = "${DatabaseBackupPolicy.DB_NAME}.staged"
    const val MARKER_FILE_NAME = "restore.marker"
    const val VALIDATING_SUFFIX = ".db.validating"

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
    fun stage(validated: ValidatedBackup, filesDir: File, nowMillis: Long): Boolean {
        val restoreDir = restoreDir(filesDir)
        if ((!restoreDir.exists() && !restoreDir.mkdirs()) || !restoreDir.isDirectory) return false
        val staged = stagedFile(filesDir)
        val marker = markerFile(filesDir)
        val markerTemp = File(restoreDir, "$MARKER_FILE_NAME.tmp")
        return try {
            moveAtomically(validated.databaseFile, staged)
            writeMarker(markerTemp, validated.sourceName, nowMillis)
            moveAtomically(markerTemp, marker)
            true
        } catch (_: IOException) {
            deleteBestEffort(markerTemp)
            deleteBestEffort(staged)
            false
        } catch (_: RuntimeException) {
            deleteBestEffort(markerTemp)
            deleteBestEffort(staged)
            false
        }
    }

    @Throws(IOException::class)
    internal fun ensureRecoveryMarker(marker: File) {
        if (marker.exists()) return
        val temp = File(marker.parentFile, marker.name + ".tmp")
        writeMarker(temp, "unknown", System.currentTimeMillis())
        moveAtomically(temp, marker)
    }

    @Throws(IOException::class)
    internal fun moveAtomically(source: File, destination: File) {
        destination.parentFile?.let { parent ->
            if ((!parent.exists() && !parent.mkdirs()) || !parent.isDirectory) {
                throw IOException("Unable to create destination directory")
            }
        }
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun writeMarker(file: File, sourceName: String, nowMillis: Long) {
        val sanitizedSource = sourceName.replace('\n', ' ').replace('\r', ' ').take(240)
        FileOutputStream(file).use { output ->
            output.write("source_name=$sanitizedSource\nstaged_at=$nowMillis\n".toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }
}
