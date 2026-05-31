package dev.bee.kanjianki;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingsStudyAheadPanelModelsTest {
    @Test
    public void createBuildsStudyAheadPanelModelFromCurrentMinutes() {
        RecordingSaveAction action = new RecordingSaveAction();

        SettingsStudyAheadPanelModel model = SettingsStudyAheadPanelModels.create(30, action);

        assertEquals("Study ahead", model.getTitle());
        assertEquals("Minutes (0-1440)", model.getMinutesLabel());
        assertEquals("30", model.getInitialMinutesText());
        assertEquals("Save study ahead", model.getSaveLabel());

        model.getOnSave().save("45");
        assertEquals("45", action.minutesText);
    }

    private static final class RecordingSaveAction implements SettingsStudyAheadSaver {
        String minutesText;

        @Override
        public void save(String minutesText) {
            this.minutesText = minutesText;
        }
    }
}
