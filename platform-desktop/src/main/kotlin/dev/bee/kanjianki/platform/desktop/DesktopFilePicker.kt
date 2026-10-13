package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.FilePicker
import dev.bee.kanjianki.platform.FilePickerRequest
import dev.bee.kanjianki.platform.PlatformFileReference
import java.nio.file.Path

/**
 * Desktop's [FilePicker]: shows a native file dialog and turns the chosen path into a
 * capability-scoped [PlatformFileReference].
 *
 * The registration step is the whole point. On desktop a path *is* the capability —
 * there is no content-URI permission grant as on Android — so shared code must never
 * receive a raw path it could read or overwrite. A reference only becomes usable by
 * being handed to [DesktopFileAccess.register], and that happens here, for exactly the
 * path the user selected in the dialog. A reference fabricated anywhere else resolves
 * to nothing.
 *
 * The native dialog is injected as [showDialog] rather than called directly, for the
 * same reason [DesktopExternalNavigator] injects `browse`: an AWT `FileDialog` blocks
 * on a real window and cannot run in a unit test, and `:platform-desktop`'s 100%
 * coverage gate demands this class be exercised. The composition root supplies
 * [awtFileDialog]; tests supply a fake that returns a temp path or null. Cancellation
 * — a user who closes the dialog — is [showDialog] returning null, which becomes a
 * null [PlatformFileReference], the contract's "no file chosen".
 */
class DesktopFilePicker(
    private val fileAccess: DesktopFileAccess,
    private val showDialog: (FilePickerRequest) -> Path?,
) : FilePicker {
    override fun launch(
        request: FilePickerRequest,
        onResult: (PlatformFileReference?) -> Unit,
    ) {
        val chosen = runCatching { showDialog(request) }.getOrNull()
        if (chosen == null) {
            onResult(null)
            return
        }
        // A dialog that returned a directory or a name-less root is not a file the
        // user picked; treat it as a cancellation rather than registering something
        // unusable. register() also rejects a path with no file name, so this keeps
        // the failure a quiet null instead of an exception crossing the callback.
        val reference = runCatching { fileAccess.register(chosen) }.getOrNull()
        onResult(reference)
    }
}
