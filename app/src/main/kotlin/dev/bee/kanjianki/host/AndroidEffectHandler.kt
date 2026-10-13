package dev.bee.kanjianki.host

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import dev.bee.kanjianki.presentation.KaniEffect
import dev.bee.kanjianki.shell.ShellEffectHandler

/**
 * The Android host's [ShellEffectHandler]: clipboard and external URLs over the
 * framework, mirroring desktop's AWT adapter.
 *
 * [pickFile] delegates to the activity's [AndroidHostLaunchers], because the Storage
 * Access Framework needs an `ActivityResultLauncher` registered before STARTED and a
 * handler holding only a `Context` cannot have one. It stays nullable so a
 * non-activity context (a test, a preview) gets the previous no-op instead of a crash.
 * Focus targets are registered by the feature composables that own the fields; an
 * unknown target is a no-op by contract.
 */
internal class AndroidEffectHandler(
    private val context: Context,
    private val launchers: AndroidHostLaunchers? = null,
) : ShellEffectHandler {
    override fun openUrl(url: String) {
        // Only http/https reach an external handler, the same allowlist desktop uses:
        // an arbitrary scheme handed to ACTION_VIEW is a launch surface, not a link.
        val uri = runCatching { url.toUri() }.getOrNull() ?: return
        if (uri.scheme?.lowercase() !in OPENABLE_SCHEMES || uri.host.isNullOrBlank()) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    override fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("Kani", text)) }
    }

    override fun pickFile(purpose: KaniEffect.PickFile) {
        launchers?.pickFile(purpose)
    }

    override fun requestFocus(target: String) = Unit

    private companion object {
        val OPENABLE_SCHEMES = setOf("https", "http")
    }
}
