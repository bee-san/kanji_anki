package dev.bee.kanjianki

import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.widget.KaniWidgetEventHooks

internal class MainActivitySettingsThemePanel(private val activity: MainActivitySettings) {
    internal fun themeSettingsPanelModel(
        snapshot: SettingsSnapshot = activity.loadSettingsSnapshot(),
    ): SettingsThemePanelModel {
        val currentChoice = snapshot.themeChoice
        return SettingsThemePanelModels.themeSettingsPanelModel(currentChoice) { choice ->
            activity.runSettingsWrite(
                traceSection = "kani.settings.theme.save",
                write = { activity.saveSettings(SettingsSaveCommand.Theme(choice)) },
                onComplete = {
                    KaniWidgetEventHooks.DEFAULT.themeWriteCompleted(activity)
                    activity.renderSettingsAppearance(true)
                },
            )
        }
    }
}
