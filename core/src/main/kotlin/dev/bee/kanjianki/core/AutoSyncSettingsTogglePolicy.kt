package dev.bee.kanjianki.core

object AutoSyncSettingsTogglePolicy {
    const val ENABLED_MESSAGE: String = "Daily Anki sync turned on."
    const val DISABLED_MESSAGE: String = "Daily Anki sync turned off."

    @JvmStatic
    fun enable(): ToggleResult {
        return ToggleResult(true, ENABLED_MESSAGE)
    }

    @JvmStatic
    fun disable(): ToggleResult {
        return ToggleResult(false, DISABLED_MESSAGE)
    }

    @JvmRecord
    data class ToggleResult(val enabled: Boolean, val message: String)
}
