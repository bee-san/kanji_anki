package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsBase
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLadderThresholdPanelModelsTest {
    @Test
    fun createBuildsThresholdPanelModelFromCurrentSettings() {
        var promotionDaysText: String? = null
        var failStreakText: String? = null

        val model = SettingsLadderThresholdPanelModels.create(42, -2) { promotionDaysTextArg, failStreakTextArg ->
            promotionDaysText = promotionDaysTextArg
            failStreakText = failStreakTextArg
        }

        assertEquals("Ladder thresholds", model.title)
        assertEquals("Promotion interval days", model.promotionDaysLabel)
        assertEquals("42", model.initialPromotionDaysText)
        assertEquals("1", model.initialFailStreakText)
        assertEquals(RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS.toString(), model.defaultPromotionDaysText)
        assertEquals(RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK.toString(), model.defaultFailStreakText)

        model.onSave.save("12", "4")
        assertEquals("12", promotionDaysText)
        assertEquals("4", failStreakText)
    }
}
