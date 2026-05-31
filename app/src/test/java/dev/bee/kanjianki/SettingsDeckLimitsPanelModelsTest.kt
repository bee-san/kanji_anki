package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SettingsDeckLimitsPanelModelsTest {
    @Test
    fun createUsesCurrentNewPerDayAndCopyContract() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val model = SettingsDeckLimitsPanelModels.create(settings) { }

        assertEquals(SettingsTextCopy.deckLimitsTitle(), model.title)
        assertEquals(SettingsTextCopy.deckLimitsBody(), model.body)
        assertEquals(SettingsTextCopy.newCardsPerDayLabel(), model.newPerDayLabel)
        assertEquals(settings.newPerDay.toString(), model.initialNewPerDayText)
        assertEquals(SettingsTextCopy.saveDeckLimitsLabel(), model.saveLabel)
        assertNotNull(model.onSave)
    }
}
