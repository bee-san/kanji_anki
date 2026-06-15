package dev.bee.kanjianki

import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.theme.KaniThemeChoice

internal data class SettingsThemeChoiceModel(
    val choice: KaniThemeChoice,
    val title: String,
    val subtitle: String,
    val selected: Boolean,
    val swatches: List<Int>,
    val contentDescription: String,
    val onSelect: Runnable,
)

internal data class SettingsThemePanelModel(
    val title: String,
    val body: String,
    val choices: List<SettingsThemeChoiceModel>,
) : SettingsPanelModel {
    internal fun selectedChoice(): KaniThemeChoice? = choices.firstOrNull { it.selected }?.choice
}

internal object SettingsThemePanelModels {
    internal fun themeSettingsPanelModel(
        currentChoice: KaniThemeChoice,
        onSelectChoice: (KaniThemeChoice) -> Unit,
    ): SettingsThemePanelModel {
        val choices = KaniThemeChoice.entries.map { choice ->
            val selected = choice == currentChoice
            SettingsThemeChoiceModel(
                choice = choice,
                title = SettingsThemeCopy.choiceTitle(choice),
                subtitle = SettingsThemeCopy.choiceSubtitle(choice),
                selected = selected,
                swatches = SettingsThemeCopy.previewSwatches(choice),
                contentDescription = SettingsThemeCopy.choiceContentDescription(choice, selected),
                onSelect = Runnable { onSelectChoice(choice) },
            )
        }
        return SettingsThemePanelModel(
            title = SettingsTextCopy.settingsAppearanceTitle(),
            body = SettingsTextCopy.settingsAppearanceBody(),
            choices = choices,
        )
    }
}
