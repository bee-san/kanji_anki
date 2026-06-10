package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AdaptiveFocusCopyTest {
    @Test
    fun adaptiveFocusTextPreservesSummaryCopy() {
        val waiting = RecordsSchedulerModels.AdaptiveLoadPlan(20, 0, 0, emptyList(), 0, false, "")
        val all = RecordsSchedulerModels.AdaptiveLoadPlan(100, 3, 3, listOf("裂", "提", "語"), 0, true, "all")
        val focused = RecordsSchedulerModels.AdaptiveLoadPlan(20, 5, 2, listOf("裂", "提"), 0, false, "focus")

        assertEquals("Adaptive focus is waiting for sync", AdaptiveFocusCopy.adaptiveFocusText(null))
        assertEquals("Adaptive focus is waiting for sync", AdaptiveFocusCopy.adaptiveFocusText(waiting))
        assertEquals("Adaptive focus covers all current problem kanji", AdaptiveFocusCopy.adaptiveFocusText(all))
        assertEquals("Today's adaptive focus: 2 of 5 left", AdaptiveFocusCopy.adaptiveFocusText(focused))
    }

    @Test
    fun adaptiveFocusTextTranslatesToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            val waiting = RecordsSchedulerModels.AdaptiveLoadPlan(20, 0, 0, emptyList(), 0, false, "")
            val all = RecordsSchedulerModels.AdaptiveLoadPlan(100, 3, 3, listOf("裂", "提", "語"), 0, true, "all")
            val focused = RecordsSchedulerModels.AdaptiveLoadPlan(20, 5, 2, listOf("裂", "提"), 0, false, "focus")

            assertEquals("自動フォーカスは同期待ちです", AdaptiveFocusCopy.adaptiveFocusText(null))
            assertEquals("自動フォーカスは同期待ちです", AdaptiveFocusCopy.adaptiveFocusText(waiting))
            assertEquals("自動フォーカスは現在の苦手漢字をすべて含みます", AdaptiveFocusCopy.adaptiveFocusText(all))
            assertEquals("今日の自動フォーカス：5件中、残り2件", AdaptiveFocusCopy.adaptiveFocusText(focused))
        }
    }

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val original = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
