package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.HashSet

class FsrsPrecisionSoakTest {
    private fun reviewCard(): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem("裂", "review", 0L, 1.2, 5.0, 1, 0, 2, 1, null, 0L)
            .withRungAndPhase(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW)
    }

    @Test
    fun repeatedReviewsNeverProduceNonFiniteStabilityOrDifficulty() {
        val scheduler = BridgeScheduler()
        val consumed = HashSet<String>()
        var item = reviewCard()
        var now = 1_000L
        val ratings = listOf("again", "hard", "good", "easy")

        for (i in 0 until 500) {
            val rating = ratings[i % ratings.size]
            val result = scheduler.applyReview(
                item.withToken("t$i"),
                RecordsSchedulerModels.ReviewRequest("裂", "t$i", rating, false, false, false, 0),
                consumed,
                now,
            )
            item = result.item

            assertTrue("stability finite at step $i (rating=$rating): ${item.stability}", item.stability.isFinite())
            assertTrue("difficulty finite at step $i (rating=$rating): ${item.difficulty}", item.difficulty.isFinite())
            assertTrue("stability positive at step $i", item.stability > 0.0)

            // Round-trip the persisted memory to make sure full precision survives
            // encode/decode without turning into NaN/Infinity.
            val memory = item.memoryForRung(item.rung)
            val decoded = RecordsStudyModels.TaskMemory.decode(memory.encode(), null)
            assertEquals(memory.stability, decoded.stability, 0.0)
            assertEquals(memory.difficulty, decoded.difficulty, 0.0)

            // Advance to the next due slot with a fresh review-phase item so the next
            // iteration counts as a real due review.
            now = Math.max(item.dueAtMillis, now + 60_000L)
            item = item.copyBuilder()
                .dueAtMillis(now - 60_000L)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .state("review")
                .build()
        }
    }

    @Test
    fun persistedStabilityKeepsFullPrecisionInsteadOfTwoDecimalRounding() {
        val scheduler = BridgeScheduler()
        val result = scheduler.applyReview(
            reviewCard().withToken("p"),
            RecordsSchedulerModels.ReviewRequest("裂", "p", "good", false, false, false, 0),
            HashSet(),
            5L * BridgeScheduler.DAY,
        )
        // The old behavior rounded to 2 dp; full precision means the value is very
        // unlikely to be exactly equal to its 2-dp rounding.
        val rounded = Math.round(result.item.stability * 100.0) / 100.0
        assertTrue(
            "stability should retain sub-2dp precision (${result.item.stability})",
            result.item.stability != rounded || result.item.stability == 0.0,
        )
    }
}
