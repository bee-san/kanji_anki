package dev.bee.kanjianki.updatecore

/**
 * Decides whether a verified pending update should install automatically when
 * the app returns to the foreground, for example right after the user grants
 * the "install unknown apps" permission and comes back from Android settings.
 */
object ResumeInstallPolicy {
    @JvmStatic
    fun shouldInstall(
        autoUpdateEnabled: Boolean,
        canRequestPackageInstalls: Boolean,
        hasPendingUpdate: Boolean,
        installAttemptInFlight: Boolean,
    ): Boolean {
        return autoUpdateEnabled &&
            canRequestPackageInstalls &&
            hasPendingUpdate &&
            !installAttemptInFlight
    }
}
