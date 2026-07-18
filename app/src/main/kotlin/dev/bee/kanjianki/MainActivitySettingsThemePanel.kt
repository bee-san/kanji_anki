package dev.bee.kanjianki

import dev.bee.kanjianki.widget.KaniWidgetEventHooks

internal class MainActivitySettingsThemePanel(private val activity: MainActivitySettings) {
    internal fun themeSettingsPanelModel(): SettingsThemePanelModel {
        val currentChoice = activity.store.appThemeChoice()
        return SettingsThemePanelModels.themeSettingsPanelModel(currentChoice) { choice ->
            activity.runSettingsWrite(
                traceSection = "kani.settings.theme.save",
                write = { activity.store.saveAppThemeChoice(choice) },
                onComplete = {
                    KaniWidgetEventHooks.DEFAULT.themeWriteCompleted(activity)
                    activity.renderSettingsAppearance(true)
                },
            )
        }
    }
}
