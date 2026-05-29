package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsSchedulerModels;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public final class SettingsLearningStepsPanelModelsTest {
    @Test
    public void createBuildsLearningStepsPanelModelFromCurrentSettings() {
        RecordsSchedulerModels.LearningStepSettings current = new RecordsSchedulerModels.LearningStepSettings(
                Arrays.asList(2, 15),
                Arrays.asList(7)
        );
        RecordingSaveAction action = new RecordingSaveAction();

        SettingsLearningStepsPanelModel model = SettingsLearningStepsPanelModels.create(current, action);

        assertEquals("Learning steps", model.getTitle());
        assertEquals("New cards", model.getNewCardsLabel());
        assertEquals("2m, 15m", model.getInitialNewStepsText());
        assertEquals("7m", model.getInitialReviewStepsText());
        assertEquals("1m, 10m", model.getDefaultNewStepsText());
        assertEquals("10m", model.getDefaultReviewStepsText());

        model.getOnSave().save("3m", "8m");
        assertEquals("3m", action.newStepsText);
        assertEquals("8m", action.reviewStepsText);
    }

    private static final class RecordingSaveAction implements SettingsLearningStepsSaveAction {
        String newStepsText;
        String reviewStepsText;

        @Override
        public void save(String newStepsText, String reviewStepsText) {
            this.newStepsText = newStepsText;
            this.reviewStepsText = reviewStepsText;
        }
    }
}
