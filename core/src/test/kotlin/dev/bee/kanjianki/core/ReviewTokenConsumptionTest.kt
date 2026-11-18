package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTokenConsumptionTest {
    /** Delegates to a real model but throws on the first [failuresBeforeSuccess] calls. */
    private class FlakyMemoryModel(
        private var failuresBeforeSuccess: Int,
        private val delegate: KaniMemoryModel = TestMemoryModels.fsrs(),
    ) : KaniMemoryModel {
        override fun previewFirstGood(request: MemoryReviewRequest): MemoryReviewResult {
            failIfNeeded()
            return delegate.previewFirstGood(request)
        }

        override fun review(request: MemoryReviewRequest): MemoryReviewResult {
            failIfNeeded()
            return delegate.review(request)
        }

        private fun failIfNeeded() {
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess--
                throw IllegalStateException("simulated mid-apply memory-model failure")
            }
        }
    }

    private fun item(): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem("裂", "review", 0, 2.0, 4.0, 4, 0, 2, 2, "token-1", 0)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .build()
    }

    @Test
    fun failedReviewLeavesTokenUnconsumedSoRetrySucceeds() {
        val engine = ReviewTransitionEngine(FlakyMemoryModel(failuresBeforeSuccess = 1))
        val consumed = HashSet<String>()
        val request = RecordsSchedulerModels.ReviewRequest("裂", "token-1", "good", false, false, false, 0)

        // First attempt fails mid-apply; the token must NOT be consumed.
        var threw = false
        try {
            engine.applyReview(
                BridgeScheduler.ReviewApplication.builder(item(), request, consumed, 1_000L).build(),
            )
        } catch (_: RuntimeException) {
            threw = true
        }
        assertTrue("first apply should surface the failure", threw)
        assertFalse("token must not be consumed after a failed apply", consumed.contains("token-1"))

        // Retry with the same token succeeds and is not rejected as a duplicate.
        val retry = engine.applyReview(
            BridgeScheduler.ReviewApplication.builder(item(), request, consumed, 2_000L).build(),
        )
        assertFalse(retry.duplicate)
        assertEquals("good", retry.appliedRating)
        assertTrue(consumed.contains("token-1"))
    }

    @Test
    fun successfulReviewConsumesTokenAndBlocksReplay() {
        val engine = ReviewTransitionEngine(FlakyMemoryModel(failuresBeforeSuccess = 0))
        val consumed = HashSet<String>()
        val request = RecordsSchedulerModels.ReviewRequest("裂", "token-1", "good", false, false, false, 0)

        val first = engine.applyReview(
            BridgeScheduler.ReviewApplication.builder(item(), request, consumed, 1_000L).build(),
        )
        val second = engine.applyReview(
            BridgeScheduler.ReviewApplication.builder(first.item.withToken("token-1"), request, consumed, 2_000L).build(),
        )

        assertFalse(first.duplicate)
        assertTrue(second.duplicate)
    }
}
