package dev.bee.kanjianki.updatecore

/**
 * Decides how the "Automatically update in the background" settings option
 * behaves. The option is the one-tap setup path for background updates: it is
 * offered while background updating is not fully configured, turns the
 * automatic update schedule on when needed, and sends the user to the Android
 * settings page that grants the install permission background updates need.
 */
object BackgroundAutoUpdateOptionPolicy {
    /**
     * The option stays visible until background updates are fully configured,
     * meaning automatic checks are enabled and the install permission is
     * granted.
     */
    @JvmStatic
    fun optionVisible(autoUpdateEnabled: Boolean, canInstallUpdates: Boolean): Boolean {
        return !autoUpdateEnabled || !canInstallUpdates
    }

    /** Automatic update checks are turned on only when currently disabled. */
    @JvmStatic
    fun shouldEnableAutoUpdates(autoUpdateEnabled: Boolean): Boolean {
        return !autoUpdateEnabled
    }

    /**
     * The Android install-permission settings page is opened only while the
     * permission is missing; once granted there is nothing to change there.
     */
    @JvmStatic
    fun shouldOpenInstallSettings(canInstallUpdates: Boolean): Boolean {
        return !canInstallUpdates
    }
}
