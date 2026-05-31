package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveFocusCopyTest {
    @Test
    fun adaptiveFocusTextPreservesSummaryCopy() {
        val waiting = RecordsSchedulerModels.AdaptiveLoadPlan(20, 0, 0, emptyList(), 0, false, "")
        val all = RecordsSchedulerModels.AdaptiveLoadPlan(100, 3, 3, listOf("裂", "提", "語"), 0, true, "all")
        val focused = RecordsSchedulerModels.AdaptiveLoadPlan(20, 5, 2, listOf("裂", "提"), 0, false, "focus")

        assertEquals("Adaptive focus is waiting for sync", AdaptiveFocusCopy.adaptiveFocusText(null))
        assertEquals("Adaptive focus is waiting for sync", AdaptiveFocusCopy.adaptiveFocusText(waiting))
        assertEquals("Adaptive focus is set to all current problem kanji", AdaptiveFocusCopy.adaptiveFocusText(all))
        assertEquals("Today's adaptive focus: 2 items left / 5", AdaptiveFocusCopy.adaptiveFocusText(focused))
    }
}
