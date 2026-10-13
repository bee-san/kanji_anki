package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SettingsStudyBehaviorTextCopyTest {
    @Test
    fun labelsAndUnitsResolveInEnglish() {
        assertEquals("Promotion interval", SettingsStudyBehaviorTextCopy.promotionIntervalLabel())
        assertEquals("Demotion after fails", SettingsStudyBehaviorTextCopy.demotionFailStreakLabel())
        assertEquals("Study ahead", SettingsStudyBehaviorTextCopy.studyAheadLabel())
        assertEquals("days", SettingsStudyBehaviorTextCopy.daysUnit())
        assertEquals("fails", SettingsStudyBehaviorTextCopy.failsUnit())
        assertEquals("min", SettingsStudyBehaviorTextCopy.minutesUnit())
        assertEquals("New card order", SettingsStudyBehaviorTextCopy.newCardSortLabel())
        assertEquals("New cards per day", SettingsStudyBehaviorTextCopy.newPerDayLabel())
        assertEquals("Active queue cap", SettingsStudyBehaviorTextCopy.activeQueueCapLabel())
        assertEquals("cards", SettingsStudyBehaviorTextCopy.cardsUnit())
    }

    @Test
    fun everySortModeNamesItselfAndUnknownFallsBackToBalanced() {
        val modes = listOf(
            RecordsBase.NEW_CARD_SORT_FREQUENCY,
            RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY,
            RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK,
            RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS,
            RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY,
        )
        for (mode in modes) {
            assertTrue(mode, SettingsStudyBehaviorTextCopy.newCardSortModeLabel(mode).isNotBlank())
        }
        assertEquals("Frequency", SettingsStudyBehaviorTextCopy.newCardSortModeLabel(RecordsBase.NEW_CARD_SORT_FREQUENCY))
        assertEquals("Balanced", SettingsStudyBehaviorTextCopy.newCardSortModeLabel("balanced_priority"))
        assertEquals("Balanced", SettingsStudyBehaviorTextCopy.newCardSortModeLabel("something_unknown"))
    }

    @Test
    fun labelsTranslateToJapaneseLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            assertEquals("昇格の間隔", SettingsStudyBehaviorTextCopy.promotionIntervalLabel())
            assertEquals("降格までの連続失敗", SettingsStudyBehaviorTextCopy.demotionFailStreakLabel())
            assertEquals("前倒し学習", SettingsStudyBehaviorTextCopy.studyAheadLabel())
            assertEquals("日", SettingsStudyBehaviorTextCopy.daysUnit())
            assertEquals("回", SettingsStudyBehaviorTextCopy.failsUnit())
            assertEquals("分", SettingsStudyBehaviorTextCopy.minutesUnit())
            assertEquals("新規カードの順序", SettingsStudyBehaviorTextCopy.newCardSortLabel())
            assertEquals("1日の新規カード", SettingsStudyBehaviorTextCopy.newPerDayLabel())
            assertEquals("アクティブ上限", SettingsStudyBehaviorTextCopy.activeQueueCapLabel())
            assertEquals("枚", SettingsStudyBehaviorTextCopy.cardsUnit())
            assertEquals("頻度", SettingsStudyBehaviorTextCopy.newCardSortModeLabel(RecordsBase.NEW_CARD_SORT_FREQUENCY))
            assertEquals("難易度", SettingsStudyBehaviorTextCopy.newCardSortModeLabel(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY))
            assertEquals("忘却リスク", SettingsStudyBehaviorTextCopy.newCardSortModeLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
            assertEquals("弱点", SettingsStudyBehaviorTextCopy.newCardSortModeLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS))
            assertEquals("バランス", SettingsStudyBehaviorTextCopy.newCardSortModeLabel("balanced_priority"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
