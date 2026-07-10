package dev.bee.kanjianki.backup

import android.net.Uri
import dev.bee.kanjianki.core.BackupExportPolicy
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream

fun interface UriStreams {
    @Throws(IOException::class)
    fun openOutputStream(uri: Uri): OutputStream?
}

internal data class PreparedBackupExport(
    val file: File,
    val suggestedName: String,
    val gzipSizeBytes: Long,
)

internal sealed interface BackupExportPreparation {
    data class Ready(val export: PreparedBackupExport) : BackupExportPreparation

    data class Failed(val copy: BackupExportPolicy.Copy) : BackupExportPreparation
}

internal data class BackupExportCopyResult(
    val success: Boolean,
    val copy: BackupExportPolicy.Copy,
)

/** Thin file/stream seam around the Android document picker. */
internal object BackupExportOperations {
    private const val EXPORT_TEMP_DIR = "backup-export"

    @JvmStatic
    fun prepare(
        tempRoot: File,
        dbFile: File,
        nowMillis: Long,
        snapshotter: DatabaseBackupWorker.Snapshotter,
    ): BackupExportPreparation {
        val tempDir = File(tempRoot, EXPORT_TEMP_DIR)
        if ((!tempDir.exists() && !tempDir.mkdirs()) || !tempDir.isDirectory) {
            return BackupExportPreparation.Failed(BackupExportPolicy.exportPrepareFailed())
        }
        // Process death while the system picker was open can orphan a process-local pending
        // export. A new export is a safe point to clear those private cache files.
        tempDir.listFiles()?.forEach(::deleteQuietly)
        val suggestedName = BackupExportPolicy.suggestedFileName(nowMillis)
        val raw = File(tempDir, "$suggestedName.raw.tmp")
        val gzip = File(tempDir, "$suggestedName.pending")
        deleteQuietly(raw)
        deleteQuietly(gzip)

        return try {
            snapshotter.snapshot(dbFile, raw)
            DatabaseBackupWorker.gzipFile(raw, gzip)
            BackupExportPreparation.Ready(
                PreparedBackupExport(
                    file = gzip,
                    suggestedName = suggestedName,
                    gzipSizeBytes = gzip.length(),
                ),
            )
        } catch (_: IOException) {
            deleteQuietly(gzip)
            BackupExportPreparation.Failed(BackupExportPolicy.exportPrepareFailed())
        } catch (_: RuntimeException) {
            deleteQuietly(gzip)
            BackupExportPreparation.Failed(BackupExportPolicy.exportPrepareFailed())
        } finally {
            deleteQuietly(raw)
        }
    }

    @JvmStatic
    fun copyToUri(
        prepared: PreparedBackupExport,
        destination: Uri,
        streams: UriStreams,
    ): BackupExportCopyResult {
        return try {
            val output = streams.openOutputStream(destination)
                ?: throw IOException("Document provider returned no output stream")
            output.use { destinationStream ->
                FileInputStream(prepared.file).use { source ->
                    source.copyTo(destinationStream)
                }
                destinationStream.flush()
            }
            BackupExportCopyResult(
                success = true,
                copy = BackupExportPolicy.exportComplete(prepared.gzipSizeBytes),
            )
        } catch (_: IOException) {
            BackupExportCopyResult(false, BackupExportPolicy.exportWriteFailed())
        } catch (_: RuntimeException) {
            BackupExportCopyResult(false, BackupExportPolicy.exportWriteFailed())
        } finally {
            deleteQuietly(prepared.file)
            prepared.file.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }
                ?.let(BackupRestoreStager::deleteBestEffort)
        }
    }

    @JvmStatic
    fun discard(prepared: PreparedBackupExport?) {
        if (prepared == null) return
        deleteQuietly(prepared.file)
        prepared.file.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }
            ?.let(BackupRestoreStager::deleteBestEffort)
    }

    private fun deleteQuietly(file: File) {
        BackupRestoreStager.deleteBestEffort(file)
    }
}

/** Process-local bridge between panel preparation and the one ActivityResult callback. */
internal object PendingExportHolder {
    private var pending: PreparedBackupExport? = null

    @Synchronized
    fun replace(export: PreparedBackupExport) {
        BackupExportOperations.discard(pending)
        pending = export
    }

    @Synchronized
    fun take(): PreparedBackupExport? {
        val result = pending
        pending = null
        return result
    }

    @Synchronized
    fun discard() {
        BackupExportOperations.discard(pending)
        pending = null
    }
}
