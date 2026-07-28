package dev.bee.kanjianki

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SettingsFlashcardGestureControlDescriptions {
    const val TOGGLE = "Swipe to grade toggle"
}

class SettingsFlashcardGestureState(enabled: Boolean) {
    var enabled by mutableStateOf(enabled)
}

fun interface SettingsFlashcardGestureToggleAction {
    fun setEnabled(enabled: Boolean)
}

data class SettingsFlashcardGesturePanelModel(
    val title: String,
    val body: String,
    val status: String,
    val state: SettingsFlashcardGestureState,
    val toggleLabel: String,
    val onToggle: SettingsFlashcardGestureToggleAction,
) : SettingsPanelModel
