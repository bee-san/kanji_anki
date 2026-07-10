package dev.bee.kanjianki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SettingsPersonalizedSchedulingControlDescriptions {
    const val TOGGLE = "Personalized scheduling toggle"
    const val FIT_NOW = "Fit FSRS weights now"
    const val RESET = "Reset fitted FSRS weights"
}

class SettingsPersonalizedSchedulingState(enabled: Boolean) {
    var enabled by mutableStateOf(enabled)
}

fun interface SettingsPersonalizedSchedulingToggleAction {
    fun setEnabled(enabled: Boolean)
}

data class SettingsPersonalizedSchedulingPanelModel(
    val title: String,
    val body: String,
    val status: String,
    val state: SettingsPersonalizedSchedulingState,
    val toggleLabel: String,
    val fitNowLabel: String,
    val resetLabel: String,
    val onToggle: SettingsPersonalizedSchedulingToggleAction,
    val onFitNow: Runnable,
    val onReset: Runnable,
) : SettingsPanelModel
