package dev.bee.kanjianki.host

import android.content.Context
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import dev.bee.kanjianki.core.BackupExportPolicy
import dev.bee.kanjianki.platform.FilePickerPurpose
import dev.bee.kanjianki.platform.FilePickerRequest
import dev.bee.kanjianki.platform.PlatformFileReference
import dev.bee.kanjianki.platform.android.AndroidFilePicker
import dev.bee.kanjianki.presentation.KaniEffect

/**
 * The four Activity Result launchers the thin host owns, registered in one place.
 *
 * **Registration order is load-bearing.** `ComponentActivity`'s automatic result registry
 * derives its keys positionally, so a result Android restored for a process it already
 * killed is routed by the position its launcher was registered at. Reorder these and a
 * pending document pick can come back as a permission grant. The order is the one
 * `MainActivityBase` has always used — save document, open document, AnkiDroid database
 * permission, notification permission — and [REGISTRATION_ORDER] exists so a test can
 * pin it, because nothing at the call site shows why the order matters.
 *
 * The picker is constructed before the launchers it drives, as in `MainActivityBase`:
 * [AndroidFilePicker] only captures the two launch lambdas, and those resolve the
 * `lateinit` fields when the user actually picks, not now.
 */
internal class AndroidHostLaunchers(
    context: Context,
    caller: ActivityResultCaller,
    private val onAnkiPermissionResult: () -> Unit,
    private val onNotificationPermissionResult: (Boolean) -> Unit,
    private val onFilePicked: (KaniEffect.FilePurpose, PlatformFileReference?) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val saveDocument: ActivityResultLauncher<String>
    private val openDocument: ActivityResultLauncher<Array<String>>
    private val ankiDatabasePermission: ActivityResultLauncher<String>
    private val notificationPermission: ActivityResultLauncher<String>

    private val filePicker = AndroidFilePicker(
        context,
        launchSaveDocument = { saveDocument.launch(it) },
        launchOpenDocument = { openDocument.launch(it) },
    )

    init {
        // Each launcher's own callback runs only when the picker has no pending callback of
        // its own -- `onSaveResult`/`onOpenResult` return false in exactly that case, which
        // is Android delivering a result for an activity instance that no longer exists.
        // Preserving that delivery is the pre-adapter behavior and the reason the branch
        // is not simply an early return.
        saveDocument = caller.registerForActivityResult(
            ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
        ) { uri ->
            if (!filePicker.onSaveResult(uri)) {
                onFilePicked(KaniEffect.FilePurpose.BACKUP_EXPORT, filePicker.referenceFor(uri))
            }
        }
        openDocument = caller.registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (!filePicker.onOpenResult(uri)) {
                onFilePicked(KaniEffect.FilePurpose.BACKUP_RESTORE, filePicker.referenceFor(uri))
            }
        }
        ankiDatabasePermission = caller.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { onAnkiPermissionResult() }
        notificationPermission = caller.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> onNotificationPermissionResult(granted) }
    }

    /**
     * Runs the system picker for [effect], reporting whether it was actually launched.
     *
     * False means the request was declined, not that it failed: either a pick is already
     * pending (the picker refuses to overwrite the waiting callback) or the purpose has no
     * Android consumer yet. Both leave no dialog on screen, so the caller can keep the
     * effect's own state unchanged.
     */
    fun pickFile(effect: KaniEffect.PickFile): Boolean {
        val request = effect.toRequest() ?: return false
        return filePicker.launchForResult(request) { file -> onFilePicked(effect.purpose, file) }
    }

    fun requestAnkiDatabasePermission(permission: String) {
        ankiDatabasePermission.launch(permission)
    }

    fun requestNotificationPermission() {
        notificationPermission.launch(PERMISSION_POST_NOTIFICATIONS)
    }

    /**
     * The platform request for [KaniEffect.PickFile], or null when Android has no consumer.
     *
     * A SAVE request must carry a name: [AndroidFilePicker] requires one for `CreateDocument`
     * and reports a missing one as a *declined* launch, so a blank one would silently open no
     * dialog at all. `KaniEffect.PickFile.suggestedName` defaults to blank and the shared
     * graph is not obliged to fill it, so the host supplies the same timestamped name the
     * old backup flow used rather than trusting the effect.
     *
     * `MISSING_KANJI_CSV_EXPORT` is deliberately unmapped, matching the desktop handler's
     * no-op: its report needs the Goal 183 dictionary assets, so a dialog now would save an
     * empty file. It also cannot simply reuse the SAVE launcher, because a *restored* save
     * result carries no purpose — the launcher callback above has to name one, and with two
     * SAVE consumers it would have to guess. Wiring this purpose therefore means persisting
     * the pending purpose across process death, not adding a branch here.
     */
    private fun KaniEffect.PickFile.toRequest(): FilePickerRequest? = when (purpose) {
        KaniEffect.FilePurpose.BACKUP_EXPORT -> FilePickerRequest(
            FilePickerPurpose.SAVE,
            suggestedName.takeIf(String::isNotBlank)
                ?: BackupExportPolicy.suggestedFileName(clock()),
        )

        KaniEffect.FilePurpose.BACKUP_RESTORE -> FilePickerRequest(FilePickerPurpose.OPEN)
        KaniEffect.FilePurpose.MISSING_KANJI_CSV_EXPORT -> null
    }

    internal companion object {
        const val BACKUP_MIME_TYPE: String = "application/gzip"
        const val PERMISSION_POST_NOTIFICATIONS: String = "android.permission.POST_NOTIFICATIONS"

        /** The order the constructor registers its launchers in; see the class KDoc. */
        val REGISTRATION_ORDER: List<String> = listOf(
            "save-document",
            "open-document",
            "anki-database-permission",
            "notification-permission",
        )
    }
}
