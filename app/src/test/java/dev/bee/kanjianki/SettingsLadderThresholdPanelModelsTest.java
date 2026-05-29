package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SettingsLadderThresholdPanelModelsTest {
    @Test
    public void createBuildsThresholdPanelModelFromCurrentSettings() {
        RecordingSaveAction action = new RecordingSaveAction();

        SettingsLadderThresholdPanelModel model = SettingsLadderThresholdPanelModels.create(
                42,
                -2,
                action
        );

        assertEquals("Ladder thresholds", model.getTitle());
        assertEquals("FSRS days to go up", model.getPromotionDaysLabel());
        assertEquals("42", model.getInitialPromotionDaysText());
        assertEquals("1", model.getInitialFailStreakText());
        assertEquals(String.valueOf(RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS), model.getDefaultPromotionDaysText());
        assertEquals(String.valueOf(RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK), model.getDefaultFailStreakText());

        model.getOnSave().save("12", "4");
        assertEquals("12", action.promotionDaysText);
        assertEquals("4", action.failStreakText);
    }

    private static final class RecordingSaveAction implements SettingsLadderThresholdSaveAction {
        String promotionDaysText;
        String failStreakText;

        @Override
        public void save(String promotionDaysText, String failStreakText) {
            this.promotionDaysText = promotionDaysText;
            this.failStreakText = failStreakText;
        }
    }
}
