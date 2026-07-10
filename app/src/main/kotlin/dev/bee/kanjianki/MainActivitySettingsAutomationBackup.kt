package dev.bee.kanjianki

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import dev.bee.kanjianki.backup.BackupExportOperations
import dev.bee.kanjianki.backup.BackupExportPreparation
import dev.bee.kanjianki.backup.BackupRestoreStager
import dev.bee.kanjianki.backup.BackupRestoreValidation
import dev.bee.kanjianki.backup.BackupRestoreValidator
import dev.bee.kanjianki.backup.PendingExportHolder
import dev.bee.kanjianki.backup.UriStreams
import dev.bee.kanjianki.backup.ValidatedBackup
import dev.bee.kanjianki.core.BackupExportPolicy
import dev.bee.kanjianki.core.BackupRestorePolicy
import dev.bee.kanjianki.core.DatabaseBackupPolicy
import dev.bee.kanjianki.sync.ManualSyncEngine
import java.io.File
import java.util.concurrent.atomic.AtomicReference

internal class BackupRestoreSyncGate(
    private val syncRunning: () -> Boolean = { ManualSyncEngine.isRunning() },
) {
    fun restoreAllowed(): Boolean = BackupRestorePolicy.restoreAllowed(syncRunning())
}

/** Owns Settings > Automation backup/export/restore interactions. */
internal class MainActivitySettingsAutomationBackup(
    private val activity: MainActivitySettings,
    private val syncGate: BackupRestoreSyncGate = BackupRestoreSyncGate(),
) {
    private var pendingValidatedBackup: ValidatedBackup? = null

    fun backupSettingsPanelModel(): SettingsBackupPanelModel {
        val archives = backupArchives(DatabaseBackupPolicy.backupDir(activity.filesDir))
        val lastBackup = archives.maxOfOrNull { it.lastModified() }?.takeIf { it > 0L }
        return SettingsBackupPanelModel(
            title = BackupExportPolicy.panelTitle(),
            body = BackupExportPolicy.panelBody(),
            lastBackupLine = BackupExportPolicy.lastBackupLine(lastBackup),
            archiveCountLine = BackupExportPolicy.archiveCountLine(archives.size),
            exportLabel = BackupExportPolicy.exportNowLabel(),
            onExport = Runnable(::prepareExport),
            restoreLabel = BackupExportPolicy.restoreFromBackupLabel(),
            onRestore = Runnable(::pickRestore),
        )
    }

    fun onExportDocumentSelected(uri: Uri?) {
        val prepared = PendingExportHolder.take() ?: return
        if (uri == null) {
            BackupExportOperations.discard(prepared)
            return
        }
        val result = AtomicReference<dev.bee.kanjianki.backup.BackupExportCopyResult>()
        activity.runSettingsWrite(
            traceSection = "kani.settings.backup.export-write",
            write = {
                result.set(
                    BackupExportOperations.copyToUri(
                        prepared,
                        uri,
                        UriStreams { destination ->
                            activity.contentResolver.openOutputStream(destination, "w")
                        },
                    ),
                )
            },
        ) {
            val completed = result.get() ?: return@runSettingsWrite
            Toast.makeText(activity, completed.copy.text, Toast.LENGTH_LONG).show()
            activity.renderSettingsAutomation(true)
        }
    }

    fun onRestoreDocumentSelected(uri: Uri?) {
        if (uri == null) return
        if (!ensureRestoreAllowed()) return
        val result = AtomicReference<BackupRestoreValidation>()
        val sourceName = sourceName(uri)
        activity.runSettingsWrite(
            traceSection = "kani.settings.backup.restore-validate",
            write = {
                result.set(
                    BackupRestoreValidator.validate(
                        restoreDir = BackupRestoreStager.restoreDir(activity.filesDir),
                        sourceName = sourceName,
                        input = { activity.contentResolver.openInputStream(uri) },
                    ),
                )
            },
        ) {
            val validation = result.get() ?: return@runSettingsWrite
            val validated = validation.validatedBackup
            if (!validation.policy.accepted || validated == null) {
                Toast.makeText(activity, validation.policy.message, Toast.LENGTH_LONG).show()
                return@runSettingsWrite
            }
            discardPendingValidatedBackup()
            pendingValidatedBackup = validated
            activity.pendingBackupRestoreDialog = BackupRestoreConfirmDialogModel(
                title = BackupRestorePolicy.confirmTitle(),
                message = BackupRestorePolicy.confirmMessage(),
                confirmLabel = BackupRestorePolicy.confirmLabel(),
                dismissLabel = BackupRestorePolicy.cancelLabel(),
                onConfirm = Runnable(::confirmRestore),
                onDismiss = Runnable(::dismissRestore),
            )
            activity.renderSettingsAutomation(true)
        }
    }

    private fun prepareExport() {
        PendingExportHolder.discard()
        val result = AtomicReference<BackupExportPreparation>()
        activity.runSettingsWrite(
            traceSection = "kani.settings.backup.export-snapshot",
            write = {
                result.set(
                    BackupExportOperations.prepare(
                        tempRoot = activity.cacheDir,
                        dbFile = activity.getDatabasePath(DatabaseBackupPolicy.DB_NAME),
                        nowMillis = System.currentTimeMillis(),
                        snapshotter = { dbFile, destination ->
                            activity.store.snapshotInto(dbFile, destination)
                        },
                    ),
                )
            },
        ) {
            when (val preparation = result.get()) {
                is BackupExportPreparation.Ready -> {
                    PendingExportHolder.replace(preparation.export)
                    if (!activity.launchBackupExportDocument(preparation.export.suggestedName)) {
                        PendingExportHolder.discard()
                        Toast.makeText(
                            activity,
                            BackupExportPolicy.exportWriteFailed().text,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
                is BackupExportPreparation.Failed -> {
                    Toast.makeText(activity, preparation.copy.text, Toast.LENGTH_LONG).show()
                }
                null -> Unit
            }
        }
    }

    private fun pickRestore() {
        if (!ensureRestoreAllowed()) return
        if (!activity.launchBackupRestoreDocument()) {
            Toast.makeText(activity, BackupRestorePolicy.stagingFailedMessage(), Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmRestore() {
        if (!ensureRestoreAllowed()) return
        val validated = pendingValidatedBackup ?: return
        activity.pendingBackupRestoreDialog = null
        pendingValidatedBackup = null
        val staged = AtomicReference(false)
        activity.runSettingsWrite(
            traceSection = "kani.settings.backup.restore-stage",
            write = {
                staged.set(
                    BackupRestoreStager.stage(
                        validated,
                        activity.filesDir,
                        System.currentTimeMillis(),
                    ),
                )
            },
        ) {
            if (!staged.get()) {
                BackupRestoreStager.deleteBestEffort(validated.databaseFile)
                Toast.makeText(activity, BackupRestorePolicy.stagingFailedMessage(), Toast.LENGTH_LONG).show()
                activity.renderSettingsAutomation(true)
                return@runSettingsWrite
            }
            activity.closeForStagedRestore()
        }
    }

    private fun dismissRestore() {
        activity.pendingBackupRestoreDialog = null
        discardPendingValidatedBackup()
        activity.renderSettingsAutomation(true)
    }

    private fun ensureRestoreAllowed(): Boolean {
        if (syncGate.restoreAllowed()) return true
        Toast.makeText(activity, BackupRestorePolicy.panelBlockedBySyncMessage(), Toast.LENGTH_LONG).show()
        return false
    }

    private fun discardPendingValidatedBackup() {
        pendingValidatedBackup?.databaseFile?.let { BackupRestoreStager.deleteBestEffort(it) }
        pendingValidatedBackup = null
    }

    private fun sourceName(uri: Uri): String {
        return runCatching {
            activity.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "selected-backup.db.gz"
    }

    private fun backupArchives(directory: File): List<File> {
        return directory.listFiles { file ->
            file.isFile && file.name.startsWith("kanji_anki_simple_") &&
                (file.name.endsWith(".db.gz") || file.name.endsWith(".db"))
        }?.toList().orEmpty()
    }
}
