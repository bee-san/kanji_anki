package dev.bee.kanjianki

import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.DailyStudyPlanRequest
import dev.bee.kanjianki.core.RecommendedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityHomeTodayPlanUnitTest {
    private val dayMillis = 24L * 60L * 60L * 1000L

    @Test
    fun syncFirstCardDropsTheReasonThatEchoesTheSummary() {
        // The Today card used to print "Sync needed before Kani can judge progress"
        // as the headline and again (lowercased) as the body.
        val now = 10L * dayMillis
        val plan = DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = listOf(now - 1L),
                lastSuccessfulSyncAtMillis = now - 2L * dayMillis,
            ),
        )
        assertEquals(RecommendedAction.SYNC_FIRST, plan.recommendedAction)

        val model = homeTodayPlanModel(plan, onStudy = {}, onSync = {})

        assertEquals("Sync needed before Kani can judge progress", model.summary)
        assertEquals(emptyList<String>(), model.details)
        assertEquals("Sync AnkiDroid", model.actionLabel)
    }

    @Test
    fun studyNowCardKeepsReasonsTheSummaryDoesNotAlreadyState() {
        val now = 10L * dayMillis
        val plan = DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = listOf(now - 1L, now - 2L),
                newProblemKanjiAvailable = 3,
                lastSuccessfulSyncAtMillis = now - 1L,
            ),
        )
        assertEquals(RecommendedAction.STUDY_NOW, plan.recommendedAction)

        val model = homeTodayPlanModel(plan, onStudy = {}, onSync = {})

        // "2 due now" is already part of the summary headline; the additive new-kanji
        // reason is information the summary does not carry, so it stays.
        assertTrue(model.summary.startsWith("2 due now"))
        assertEquals(listOf("3 new problem kanji available"), model.details)
    }

    @Test
    fun waitUntilLaterCardKeepsReasonAndNextUsefulTime() {
        val now = 10L * dayMillis
        val plan = DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = listOf(now + 60L * 60L * 1000L),
                dueLaterLookaheadMillis = 2L * 60L * 60L * 1000L,
                lastSuccessfulSyncAtMillis = now - 1L,
            ),
        )
        assertEquals(RecommendedAction.WAIT_UNTIL_LATER, plan.recommendedAction)

        val model = homeTodayPlanModel(plan, onStudy = {}, onSync = {})

        assertEquals(2, model.details.size)
        assertEquals("1 learning repeat later", model.details[0])
        assertTrue(model.details[1].startsWith("Next useful time: "))
    }
}
