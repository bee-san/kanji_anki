@file:JvmName("MainActivitySettingsPersonalizedSchedulingCompose")

package dev.bee.kanjianki

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun SettingsPersonalizedSchedulingPanel(model: SettingsPersonalizedSchedulingPanelModel) {
    SettingsSwitchPanel(
        title = model.title,
        status = model.status,
        body = model.body,
        enabled = model.state.enabled,
        toggleLabel = model.toggleLabel,
        toggleContentDescription = SettingsPersonalizedSchedulingControlDescriptions.TOGGLE,
        onToggle = { enabled ->
            model.state.enabled = enabled
            model.onToggle.setEnabled(enabled)
        },
    ) {
        KaniPrimaryButton(
            label = model.fitNowLabel,
            modifier = Modifier.semantics {
                contentDescription = SettingsPersonalizedSchedulingControlDescriptions.FIT_NOW
            },
        ) { model.onFitNow.run() }
        KaniOutlinedButton(
            label = model.resetLabel,
            modifier = Modifier.semantics {
                contentDescription = SettingsPersonalizedSchedulingControlDescriptions.RESET
            },
        ) { model.onReset.run() }
    }
}
