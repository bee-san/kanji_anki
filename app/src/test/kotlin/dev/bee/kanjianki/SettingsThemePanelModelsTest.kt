package dev.bee.kanjianki

import dev.bee.kanjianki.core.KaniThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsThemePanelModelsTest {
    @Test
    fun themePanelModelDefaultsToGirlypopWithOneSelectedChoice() {
        var savedChoice: KaniThemeChoice? = null
        val model = SettingsThemePanelModels.themeSettingsPanelModel(
            currentChoice = KaniThemeChoice.fromStorageKey(null),
            onSelectChoice = { savedChoice = it },
        )

        assertEquals("Appearance", model.title)
        assertEquals("Choose your app theme.", model.body)
        assertEquals(KaniThemeChoice.entries.size, model.choices.size)
        assertEquals(1, model.choices.count { it.selected })
        assertEquals(KaniThemeChoice.GIRLYPOP, model.selectedChoice())
        assertEquals("settings-panel-theme", settingsPanelTestTag(model))

        val girlypop = model.choices.single { it.choice == KaniThemeChoice.GIRLYPOP }
        assertTrue(girlypop.selected)
        assertEquals("Girlypop", girlypop.title)
        assertEquals(4, girlypop.swatches.size)
        assertTrue(girlypop.contentDescription.isNotBlank())
        girlypop.onSelect.run()
        assertEquals(KaniThemeChoice.GIRLYPOP, savedChoice)
    }

    @Test
    fun themePanelModelChoiceActionPersistsRequestedChoice() {
        var savedChoice: KaniThemeChoice? = null
        val model = SettingsThemePanelModels.themeSettingsPanelModel(
            currentChoice = KaniThemeChoice.GIRLYPOP,
            onSelectChoice = { savedChoice = it },
        )

        model.choices.single { it.choice == KaniThemeChoice.AUTUMN }.onSelect.run()

        assertEquals(KaniThemeChoice.AUTUMN, savedChoice)
    }
}
