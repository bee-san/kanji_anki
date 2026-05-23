package dev.bee.kanjianki.updatecore

object AutoUpdateSettingsTogglePolicy {
    const val ENABLED_MESSAGE = "Automatic updates turned on."
    const val DISABLED_MESSAGE = "Automatic updates turned off."

    @JvmStatic
    fun toggle(currentlyEnabled: Boolean): ToggleResult {
        val enabled = !currentlyEnabled
        return ToggleResult(enabled, if (enabled) ENABLED_MESSAGE else DISABLED_MESSAGE)
    }

    class ToggleResult(
        private val enabled: Boolean,
        private val message: String,
    ) {
        fun enabled(): Boolean = enabled
        fun message(): String = message
    }
}
