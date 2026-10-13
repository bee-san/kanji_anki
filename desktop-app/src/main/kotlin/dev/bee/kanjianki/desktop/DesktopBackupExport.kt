package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.platform.FilePicker
import dev.bee.kanjianki.platform.FilePickerPurpose
import dev.bee.kanjianki.platform.FilePickerRequest
import dev.bee.kanjianki.platform.FileTypeFilter
import dev.bee.kanjianki.platform.PlatformFileReference
import java.nio.file.Path

/**
 * The desktop backup-export flow: pick a destination, then snapshot the live database
 * into it.
 *
 * The pieces were all in place — [DesktopFilePicker] turns an AWT SAVE dialog into a
 * capability-scoped [PlatformFileReference], and `DesktopBackupSnapshotter` writes a
 * WAL-safe gzip via `VACUUM INTO` — but nothing wired them to the shell's
 * `pickFile(BACKUP_EXPORT)` effect. This does, and stays testable by taking the picker
 * and the snapshot call as seams: the AWT dialog and the real snapshotter are supplied
 * by the composition root, a test supplies a fake picker returning a temp path and a
 * recording snapshot function.
 *
 * The chosen file is resolved through the same [pathOf] the picker registered it under,
 * so a reference fabricated elsewhere cannot make this write outside what the user
 * picked. A cancelled dialog is a null reference and a no-op. [onResult] reports the
 * outcome (bytes written, or a failure) so the caller can surface it; a fire-and-forget
 * caller passes an empty lambda, matching the effect handler's contract.
 */
internal class DesktopBackupExport(
    private val picker: FilePicker,
    private val databaseFile: Path,
    private val pathOf: (PlatformFileReference) -> Path?,
    private val snapshot: (source: Path, destination: Path) -> Long,
) {
    sealed interface Result {
        data class Exported(val bytesWritten: Long) : Result
        data object Cancelled : Result
        data class Failed(val reason: String) : Result
    }

    /** Opens the SAVE dialog and, on a chosen file, snapshots the database into it. */
    fun run(onResult: (Result) -> Unit = {}) {
        picker.launch(
            FilePickerRequest(
                purpose = FilePickerPurpose.SAVE,
                suggestedName = DEFAULT_NAME,
                filters = listOf(FileTypeFilter(description = "Kani backup", extensions = setOf("gz"))),
            ),
        ) { reference ->
            if (reference == null) {
                onResult(Result.Cancelled)
                return@launch
            }
            val destination = pathOf(reference)
            if (destination == null) {
                onResult(Result.Failed("chosen file is not accessible"))
                return@launch
            }
            val outcome = runCatching { snapshot(databaseFile, destination) }
            onResult(
                outcome.fold(
                    onSuccess = { Result.Exported(it) },
                    onFailure = { Result.Failed(it.message ?: it.toString()) },
                ),
            )
        }
    }

    private companion object {
        const val DEFAULT_NAME = "kani-backup.db.gz"
    }
}
