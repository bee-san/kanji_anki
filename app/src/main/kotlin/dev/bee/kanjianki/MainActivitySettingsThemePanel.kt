package dev.bee.kanjianki

internal class MainActivitySettingsThemePanel(private val activity: MainActivitySettings) {
    internal fun themeSettingsPanelModel(): SettingsThemePanelModel {
        val currentChoice = activity.store.appThemeChoice()
        return SettingsThemePanelModels.themeSettingsPanelModel(currentChoice) { choice ->
            activity.runSettingsWrite(
                traceSection = "kani.settings.theme.save",
                write = { activity.store.saveAppThemeChoice(choice) },
                onComplete = { activity.renderSettingsAppearance(true) },
            )
        }
    }
}
