package dev.bee.kanjianki.host

import android.content.Context
import dev.bee.kanjianki.MainActivityRuntimeOverrides

/**
 * Whether this build may install an update package, honouring the test override.
 *
 * Relocated here from the deleted `MainActivity*` chain, which is also why it takes a
 * [Context] rather than an Activity: `packageManager` was all it ever needed, and Goal 199
 * requires that no production helper accept an Activity subclass. `ResumeUpdateInstaller`
 * and the Update settings section are the callers.
 *
 * The override seam is kept because the instrumented update tests cannot grant
 * `REQUEST_INSTALL_PACKAGES` to themselves — without it, every install-permission path
 * would be untestable off a manually-configured device.
 */
internal fun canRequestPackageInstalls(context: Context): Boolean {
    MainActivityRuntimeOverrides.installPermission?.let { return it }
    return context.packageManager.canRequestPackageInstalls()
}
