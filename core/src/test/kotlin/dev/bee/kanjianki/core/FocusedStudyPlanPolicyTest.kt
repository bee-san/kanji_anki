package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.HashSet

class FocusedStudyPlanPolicyTest {
    @Test
    fun studyMoreNewCardsPlanKeepsRequestedRowOrderAndCountsDueItems() {
        val now = 2_000L

        val plan = FocusedStudyPlanPolicy.studyMoreNewCardsPlan(
            listOf("新", "無", "裂"),
            listOf(row("裂"), row("新")),
            listOf(review("新", now - 1L, 1), review("裂", now + 1_000L, 1)),
            now,
        )

        assertEquals(listOf("新", "裂"), plan.focusKanji)
        assertEquals(2, plan.target)
        assertEquals(1, plan.remaining)
        assertEquals(0, plan.newAdmissionLimit)
        assertFalse(plan.allKanjiMode)
        assertEquals("Custom study: 2 extra new cards.", plan.status)
    }

    @Test
    fun studyMoreNewCardsPlanFormatsSingularAndEmptyStatuses() {
        val now = 2_000L

        val one = FocusedStudyPlanPolicy.studyMoreNewCardsPlan(
            listOf("新"),
            listOf(row("新")),
            listOf(review("新", now, 1)),
            now,
        )
        val empty = FocusedStudyPlanPolicy.studyMoreNewCardsPlan(
            null,
            listOf(row("新")),
            null,
            now,
        )

        assertEquals("Custom study: 1 extra new card.", one.status)
        assertEquals("Custom study: 0 extra new cards.", empty.status)
        assertEquals(0, empty.remaining)
    }

    @Test
    fun allCurrentProblemKanjiPlanCountsUnstudiedAndDueStudiedItems() {
        val now = 10_000L

        val plan = FocusedStudyPlanPolicy.allCurrentProblemKanjiPlan(
            listOf(row("未"), row("済"), row("待")),
            listOf(review("未", now + 1_000L, 1), review("済", now - 1L, 1), learning("待", now + 1_000L)),
            HashSet(listOf("済", "待")),
            now,
        )

        assertEquals(listOf("未", "済", "待"), plan.focusKanji)
        assertEquals(3, plan.target)
        assertEquals(2, plan.remaining)
        assertEquals(3, plan.newAdmissionLimit)
        assertTrue(plan.allKanjiMode)
        assertEquals("All current problem kanji are available today.", plan.status)
    }

    @Test
    fun allCurrentProblemKanjiPlanTreatsMissingInputsAsEmpty() {
        val plan = FocusedStudyPlanPolicy.allCurrentProblemKanjiPlan(
            null,
            null,
            null,
            1L,
        )

        assertEquals(Collections.emptyList<String>(), plan.focusKanji)
        assertEquals(0, plan.remaining)
        assertTrue(plan.allKanjiMode)
    }

    @Test
    fun itemDueForFocusPreservesStudyModeDueRules() {
        val now = 5_000L

        assertFalse(FocusedStudyPlanPolicy.itemDueForFocus(null, now))
        assertFalse(FocusedStudyPlanPolicy.itemDueForFocus(review("退", now - 1L, 3).copyBuilder().state(StudyLadderRules.STATE_RETIRED).build(), now))
        assertFalse(FocusedStudyPlanPolicy.itemDueForFocus(learning("学", now + 1L), now))
        assertTrue(FocusedStudyPlanPolicy.itemDueForFocus(learning("学", now), now))
        assertFalse(FocusedStudyPlanPolicy.itemDueForFocus(review("新", now - 1L, 0), now))
        assertFalse(FocusedStudyPlanPolicy.itemDueForFocus(review("復", now + 1L, 1), now))
        assertTrue(FocusedStudyPlanPolicy.itemDueForFocus(review("復", now, 1), now))
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow =
        RecordsImportModels.DashboardRow(
            kanji,
            900,
            "meaning",
            "reading",
            "search",
            40,
            "reason",
            "reason text",
            1,
            1,
            0,
            emptyList<RecordsImportModels.Example>(),
        )

    private fun review(kanji: String, dueAtMillis: Long, totalReviews: Int): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(kanji, StudyLadderRules.STATE_REVIEW, dueAtMillis, 1.0, 5.0, totalReviews, 0, 0, 1, null, 0L)

    private fun learning(kanji: String, dueAtMillis: Long): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(kanji, StudyLadderRules.STATE_LEARNING, dueAtMillis, 1.0, 5.0, 1, 0, 0, 1, null, 0L)
}
