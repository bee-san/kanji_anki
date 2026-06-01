package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanSelectionPolicyTest {
    @Test
    fun extraNewCardsOverrideAdaptivePlan() {
        val adaptive = adaptivePlan()

        val result = StudyPlanSelectionPolicy.select(
            listOf("裂"),
            false,
            listOf(row("裂"), row("語")),
            listOf(item("裂", StudyLadderRules.STATE_REVIEW, 0L, 1)),
            emptySet(),
            100L,
            adaptive
        )

        assertEquals(listOf("裂"), result.focusKanji)
        assertEquals(1, result.remaining)
        assertTrue(result.status.contains("extra new card"))
    }

    @Test
    fun extraNewCardsOverrideContinueAllMode() {
        val result = StudyPlanSelectionPolicy.select(
            listOf("裂"),
            true,
            listOf(row("裂"), row("語")),
            listOf(item("裂", StudyLadderRules.STATE_REVIEW, 0L, 1)),
            setOf("語"),
            100L,
            null
        )

        assertEquals(listOf("裂"), result.focusKanji)
        assertEquals(1, result.remaining)
        assertTrue(result.status.contains("extra new card"))
    }

    @Test
    fun continueAllOverridesAdaptivePlanWhenNoExtraCardsRequested() {
        val result = StudyPlanSelectionPolicy.select(
            emptyList(),
            true,
            listOf(row("裂"), row("語")),
            listOf(item("裂", StudyLadderRules.STATE_REVIEW, 0L, 1)),
            setOf("語"),
            100L,
            adaptivePlan()
        )

        assertTrue(result.allKanjiMode)
        assertEquals(listOf("裂", "語"), result.focusKanji)
        assertEquals(1, result.remaining)
    }

    @Test
    fun adaptivePlanPassesThroughByDefault() {
        val adaptive = adaptivePlan()

        val result = StudyPlanSelectionPolicy.select(
            null,
            false,
            listOf(row("裂")),
            listOf(item("裂", StudyLadderRules.STATE_REVIEW, 0L, 1)),
            emptySet(),
            100L,
            adaptive
        )

        assertSame(adaptive, result)
    }

    @Test
    fun adaptivePlanIsRequiredForDefaultMode() {
        assertThrows(NullPointerException::class.java) {
            StudyPlanSelectionPolicy.select(
                emptyList(),
                false,
                listOf(row("裂")),
                listOf(item("裂", StudyLadderRules.STATE_REVIEW, 0L, 1)),
                emptySet(),
                100L,
                null
            )
        }
    }

    private fun adaptivePlan(): RecordsSchedulerModels.AdaptiveLoadPlan {
        return RecordsSchedulerModels.AdaptiveLoadPlan(true, 40, 1, 1, listOf("裂"), 0, false, "adaptive")
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            100,
            "meaning",
            "reading",
            "search",
            1,
            "reason",
            "reason text",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>()
        )
    }

    private fun item(kanji: String, state: String, dueAtMillis: Long, totalReviews: Int): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, state, dueAtMillis, 1.0, 5.0, totalReviews, 0, 0, 0, null, 0L)
    }
}
