package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.data.desktop.DesktopBackupRestoreValidator
import dev.bee.kanjianki.platform.FilePicker
import dev.bee.kanjianki.platform.FilePickerPurpose
import dev.bee.kanjianki.platform.FilePickerRequest
import dev.bee.kanjianki.platform.FileTypeFilter
import dev.bee.kanjianki.platform.PlatformFileReference
import java.io.InputStream
import java.nio.file.Path

/**
 * The desktop backup-restore flow's pick-and-validate half: choose a backup file,
 * validate it is a real, safe Kani snapshot, and stage it for the next launch to apply.
 *
 * Restore is deliberately restart-required — the live database cannot be swapped under
 * an open connection — so this flow validates and stages; the atomic replacement runs
 * at startup (`DesktopStagedRestoreApplier`, wired into the launch sequence). This is
 * the destructive-replacement gate: the picked file is decompressed under a bounded
 * budget, checked for SQLite magic and a compatible schema, and only an accepted
 * validation is staged. A rejected file leaves the live profile untouched.
 *
 * Seams as elsewhere: the [picker] (AWT OPEN dialog) and [validate]/[stage] calls are
 * injected so the flow is unit-tested without a display or a real database. The picked
 * file is read only through [openInput], which resolves it against what the picker
 * registered — a fabricated reference yields no stream.
 */
internal class DesktopBackupRestore(
    private val picker: FilePicker,
    private val restoreDir: Path,
    private val profileDir: Path,
    private val openInput: (PlatformFileReference) -> InputStream?,
    private val validate: (restoreDir: Path, sourceName: String, input: () -> InputStream?) -> DesktopBackupRestoreValidator.Validation,
    private val stage: (profileDir: Path, validatedDatabase: Path) -> Unit,
) {
    sealed interface Result {
        /** Staged and ready; the caller prompts for the restart that applies it. */
        data object StagedPendingRestart : Result
        data object Cancelled : Result
        /** Validation refused the file; [copyId] names why, live profile untouched. */
        data class Rejected(val copyId: String) : Result
        data class Failed(val reason: String) : Result
    }

    /** Opens the OPEN dialog and, on a chosen file, validates then stages it. */
    fun run(onResult: (Result) -> Unit = {}) {
        picker.launch(
            FilePickerRequest(
                purpose = FilePickerPurpose.OPEN,
                filters = listOf(FileTypeFilter(description = "Kani backup", extensions = setOf("gz"))),
            ),
        ) { reference ->
            if (reference == null) {
                onResult(Result.Cancelled)
                return@launch
            }
            val outcome = runCatching {
                val validation = validate(restoreDir, reference.displayName) { openInput(reference) }
                val staged = validation.stagedDatabase
                if (!validation.result.accepted || staged == null) {
                    Result.Rejected(validation.result.copyId.name)
                } else {
                    stage(profileDir, staged)
                    Result.StagedPendingRestart
                }
            }
            onResult(outcome.getOrElse { Result.Failed(it.message ?: it.toString()) })
        }
    }
}
