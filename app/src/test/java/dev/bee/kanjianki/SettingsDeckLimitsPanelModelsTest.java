package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsTextCopy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class SettingsDeckLimitsPanelModelsTest {
    @Test
    public void createUsesCurrentNewPerDayAndCopyContract() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        SettingsDeckLimitsPanelModel model = SettingsDeckLimitsPanelModels.create(settings, text -> { });

        assertEquals(SettingsTextCopy.deckLimitsTitle(), model.getTitle());
        assertEquals(SettingsTextCopy.deckLimitsBody(), model.getBody());
        assertEquals(SettingsTextCopy.newCardsPerDayLabel(), model.getNewPerDayLabel());
        assertEquals(Integer.toString(settings.newPerDay), model.getInitialNewPerDayText());
        assertEquals(SettingsTextCopy.saveDeckLimitsLabel(), model.getSaveLabel());
        assertNotNull(model.getOnSave());
    }
}
