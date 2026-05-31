package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsBase
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLadderThresholdPanelModelsTest {
    @Test
    fun createBuildsThresholdPanelModelFromCurrentSettings() {
        val action = RecordingSaveAction()

        val model = SettingsLadderThresholdPanelModels.create(
            42,
            -2,
            action,
        )

        assertEquals("Ladder thresholds", model.title)
        assertEquals("FSRS days to go up", model.promotionDaysLabel)
        assertEquals("42", model.initialPromotionDaysText)
        assertEquals("1", model.initialFailStreakText)
        assertEquals(
            RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS.toString(),
            model.defaultPromotionDaysText,
        )
        assertEquals(
            RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK.toString(),
            model.defaultFailStreakText,
        )

        model.onSave.save("12", "4")
        assertEquals("12", action.promotionDaysText)
        assertEquals("4", action.failStreakText)
    }

    private class RecordingSaveAction : SettingsLadderThresholdSaveAction {
        var promotionDaysText: String? = null
        var failStreakText: String? = null

        override fun save(promotionDaysText: String, failStreakText: String) {
            this.promotionDaysText = promotionDaysText
            this.failStreakText = failStreakText
        }
    }
}
