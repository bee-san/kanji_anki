package dev.bee.kanjianki.host

import dev.bee.kanjianki.backup.BackupExportOperations
import dev.bee.kanjianki.backup.BackupExportPreparation
import dev.bee.kanjianki.backup.DatabaseBackupWorker
import dev.bee.kanjianki.backup.PendingExportHolder
import dev.bee.kanjianki.core.BackupExportPolicy
import dev.bee.kanjianki.core.DatabaseBackupAvailabilityPolicy
import dev.bee.kanjianki.platform.PlatformFileAccess
import dev.bee.kanjianki.platform.PlatformFileReference
import java.io.File

/**
 * The Android backup-export flow, in the two halves the platform forces it into.
 *
 * Desktop snapshots the database *after* the user picks a destination, because an AWT
 * dialog blocks and the chosen path is a real file it can write. Android cannot: the
 * Storage Access Framework returns a document reference through an activity result, and
 * `VACUUM INTO` needs a filesystem path, so the snapshot is taken to private cache
 * *before* the dialog opens ([prepare]) and copied into the chosen document after
 * ([copyInto]). That ordering is the whole reason this class is not a mirror of
 * `DesktopBackupExport`, and it is why `PendingExportHolder` exists.
 *
 * The consequence worth stating: the snapshot is on disk while the picker is on screen,
 * so a cancelled dialog has something to clean up, and process death with the dialog open
 * orphans it. Both are handled — [copyInto] with a null reference discards, and
 * [BackupExportOperations.prepare] clears the scratch directory on the next export —
 * rather than left to accumulate database-sized files in the cache.
 *
 * Taking its collaborators as seams keeps it testable without an activity: a test supplies
 * a temp cache root, a temp database file, and a recording snapshotter.
 */
internal class AndroidBackupExport(
    private val cacheRoot: () -> File,
    private val databaseFile: () -> File,
    private val fileAccess: PlatformFileAccess,
    private val snapshotter: DatabaseBackupWorker.Snapshotter,
    private val operationsAllowed: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Snapshots the database into private cache, ready for a picked destination.
     *
     * Returns the copy to show and whether the picker should open at all: a stock API
     * 26–29 host cannot make a live snapshot (CLAUDE.md's backup contract), so it reports
     * that instead of opening a dialog whose save would produce a torn database.
     */
    fun prepare(): Preparation {
        if (!operationsAllowed()) {
            return Preparation(
                mayPick = false,
                message = DatabaseBackupAvailabilityPolicy.unavailableActionMessage(),
            )
        }
        // Any earlier snapshot is dead the moment a new export starts: keeping it would
        // let a stale database be written to the file the user is about to choose.
        PendingExportHolder.discard()
        return when (
            val prepared = BackupExportOperations.prepare(
                tempRoot = cacheRoot(),
                dbFile = databaseFile(),
                nowMillis = clock(),
                snapshotter = snapshotter,
            )
        ) {
            is BackupExportPreparation.Ready -> {
                PendingExportHolder.replace(prepared.export)
                Preparation(mayPick = true, message = "", suggestedName = prepared.export.suggestedName)
            }
            is BackupExportPreparation.Failed ->
                Preparation(mayPick = false, message = prepared.copy.text)
        }
    }

    /**
     * Copies the prepared snapshot into [destination], or discards it when there is none.
     *
     * A null [destination] is a cancelled dialog, which still has to discard: the snapshot
     * is already written. No prepared export at all is also not an error — a restored
     * activity result can arrive after the process that prepared it died — and reports the
     * generic failure rather than crashing on a missing field.
     */
    fun copyInto(destination: PlatformFileReference?): String {
        val prepared = PendingExportHolder.take()
            ?: return BackupExportPolicy.exportPrepareFailed().text
        if (destination == null || !operationsAllowed()) {
            BackupExportOperations.discard(prepared)
            return ""
        }
        return BackupExportOperations.copyToFile(prepared, destination, fileAccess).copy.text
    }

    /**
     * Whether the picker may open, the copy to show, and the name to pre-fill.
     *
     * [message] is blank only when there is nothing to say; a blank [message] with
     * `mayPick = false` would be a silently dropped export, which is why every refusal
     * above sets one.
     */
    data class Preparation(
        val mayPick: Boolean,
        val message: String,
        val suggestedName: String = "",
    )
}
