package dev.bee.kanjianki.core

import dev.bee.fsrs.Fsrs7Engine
import dev.bee.fsrs.Fsrs7Parameters
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

        val custom = Fsrs7Parameters.latestDefaultValues().also { it[7] = 4.0 }
        val customResult = BridgeScheduler.withWeights(custom)
            .applyReview(item, request, HashSet(), 10L * BridgeScheduler.DAY)
        assertNotEquals(defaultResult.item.dueAtMillis, customResult.item.dueAtMillis)

        val defaultAdapterResult = LatestFsrsAdapter().review(5.0, 5.0, StudyRatings.GOOD, 10.0, 0.9)
        val customAdapterResult = LatestFsrsAdapter(Fsrs7Engine.create(Fsrs7Parameters.of(custom)))
            .review(5.0, 5.0, StudyRatings.GOOD, 10.0, 0.9)
        assertNotEquals(
            defaultAdapterResult.promotionIntervalDays(),
            customAdapterResult.promotionIntervalDays(),
        )
    }
}
