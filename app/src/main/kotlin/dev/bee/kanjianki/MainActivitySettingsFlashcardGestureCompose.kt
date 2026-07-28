@file:JvmName("MainActivitySettingsFlashcardGestureCompose")

package dev.bee.kanjianki

import androidx.compose.runtime.Composable

@Composable
fun SettingsFlashcardGesturePanel(model: SettingsFlashcardGesturePanelModel) {
    SettingsSwitchPanel(
        title = model.title,
        status = model.status,
        body = model.body,
        enabled = model.state.enabled,
        toggleLabel = model.toggleLabel,
        toggleContentDescription = SettingsFlashcardGestureControlDescriptions.TOGGLE,
        onToggle = { enabled ->
            model.state.enabled = enabled
            model.onToggle.setEnabled(enabled)
        },
    )
}
