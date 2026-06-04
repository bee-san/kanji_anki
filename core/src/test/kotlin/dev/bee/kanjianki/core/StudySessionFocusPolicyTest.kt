package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySessionFocusPolicyTest {
    @Test
    fun focusedModeLimitsSchedulerToFocusKanjiCopy() {
        val plan = plan(false, "裂", "語", "裂")

        val allowed = requireNotNull(StudySessionFocusPolicy.allowedKanji(plan, false))

        assertEquals(2, allowed.size)
        assertTrue(allowed.contains("裂"))
        assertTrue(allowed.contains("語"))
        assertNotSame(plan.focusKanji, allowed)
    }

    @Test
    fun continueAllKanjiUsesUnrestrictedScheduler() {
        assertNull(StudySessionFocusPolicy.allowedKanji(plan(false, "裂"), true))
    }

    @Test
    fun allKanjiPlanUsesUnrestrictedScheduler() {
        assertNull(StudySessionFocusPolicy.allowedKanji(plan(true, "裂"), false))
    }

    @Test
    fun emptyFocusedPlanPreservesEmptyAllowedSet() {
        assertEquals(emptySet<String>(), StudySessionFocusPolicy.allowedKanji(plan(false), false))
    }

    @Test
    fun nullPlanIsRejectedLikePreviousDirectPlanAccess() {
        assertThrows(NullPointerException::class.java) { StudySessionFocusPolicy.allowedKanji(null, false) }
    }

    private fun plan(allKanjiMode: Boolean, vararg focusKanji: String): RecordsSchedulerModels.AdaptiveLoadPlan {
        return RecordsSchedulerModels.AdaptiveLoadPlan(
            25,
            focusKanji.size,
            focusKanji.size,
            focusKanji.toList(),
            focusKanji.size,
            allKanjiMode,
            "status",
        )
    }
}
