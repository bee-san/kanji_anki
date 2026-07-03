package dev.bee.kanjianki.updatecore

/**
 * Decides when the app should proactively ask the user to grant the
 * "install unknown apps" permission that automatic updates need.
 *
 * The prompt first shows after the automatic updater has completed at least
 * one check, so a brand-new install finishes onboarding before being asked.
 * If the user declines, it re-appears only when a verified update is actually
 * waiting for a version the user has not been prompted about yet, so each
 * release asks at most once.
 */
object InstallPermissionPromptPolicy {
    @JvmStatic
    fun shouldPrompt(
        autoUpdateEnabled: Boolean,
        canRequestPackageInstalls: Boolean,
        hasCompletedUpdateCheck: Boolean,
        firstPromptShown: Boolean,
        hasPendingUpdate: Boolean,
        pendingVersion: String?,
        lastPromptedVersion: String?,
    ): Boolean {
        if (!autoUpdateEnabled || canRequestPackageInstalls || !hasCompletedUpdateCheck) {
            return false
        }
        if (!firstPromptShown) {
            return true
        }
        if (!hasPendingUpdate) {
            return false
        }
        return normalizedVersion(pendingVersion) != normalizedVersion(lastPromptedVersion)
    }

    @JvmStatic
    fun normalizedVersion(version: String?): String {
        return version?.trim() ?: ""
    }
}
