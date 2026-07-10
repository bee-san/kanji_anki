package dev.bee.kanjianki.core

import dev.bee.fsrs.FsrsParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.HashSet

class BridgeSchedulerWeightsTest {
    @Test
    fun nullWeightsExactlyReproduceDefaultAndCustomWeightsReachReviewMath() {
        val item = RecordsStudyModels.StudyItem(
            "裂", "review", 0L, 5.0, 5.0, 1,
            0, 0, 0, 0, 0, 0L, false, "token", 5,
        ).withRungAndPhase(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW)
        val request = RecordsSchedulerModels.ReviewRequest("裂", "token", "good", false, false, false, 0)

        val defaultResult = BridgeScheduler().applyReview(item, request, HashSet(), 10L * BridgeScheduler.DAY)
        val nullResult = BridgeScheduler.withWeights(null).applyReview(item, request, HashSet(), 10L * BridgeScheduler.DAY)
        assertEquals(defaultResult.item.dueAtMillis, nullResult.item.dueAtMillis)
        assertEquals(defaultResult.item.stability, nullResult.item.stability, 0.0)
        assertEquals(defaultResult.item.difficulty, nullResult.item.difficulty, 0.0)

        val custom = FsrsParameters.latestDefaultValues().also { it[8] = 4.0 }
        val customResult = BridgeScheduler.withWeights(custom)
            .applyReview(item, request, HashSet(), 10L * BridgeScheduler.DAY)
        assertNotEquals(defaultResult.item.dueAtMillis, customResult.item.dueAtMillis)
    }
}
