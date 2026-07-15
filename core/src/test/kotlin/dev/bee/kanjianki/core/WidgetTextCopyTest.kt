package dev.bee.kanjianki.core

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTextCopyTest {
    @Test
    fun englishCopyCoversSetupDueAndNothingDueStates() {
        withLocale(Locale.ENGLISH) {
            assertEquals("Kani", WidgetTextCopy.appName())
            assertEquals("Set up Kani", WidgetTextCopy.notSetUpTitle())
            assertEquals("Open Kani to set up your study queue.", WidgetTextCopy.notSetUpBody())
            assertEquals("Open Kani", WidgetTextCopy.openKaniLabel())
            assertEquals("0 reviews ready", WidgetTextCopy.dueCountLabel(-1))
            assertEquals("1 review ready", WidgetTextCopy.dueCountLabel(1))
            assertEquals("3 reviews ready", WidgetTextCopy.dueCountLabel(3))
            assertEquals("0-day streak", WidgetTextCopy.streakLabel(-2))
            assertEquals("1-day streak", WidgetTextCopy.streakLabel(1))
            assertEquals("4-day streak", WidgetTextCopy.streakLabel(4))
            assertEquals("Study now", WidgetTextCopy.studyNowLabel())
            assertEquals("All caught up", WidgetTextCopy.nothingDueTitle())
            assertEquals("2-day streak · Nothing due", WidgetTextCopy.nothingDueBody(2, 0L))

            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 14)
                set(Calendar.MINUTE, 5)
            }.timeInMillis
            val body = WidgetTextCopy.nothingDueBody(2, next)
            assertEquals("2-day streak · More practice at 14:05", body)
            assertEquals(
                "Kani widget. All caught up. $body",
                WidgetTextCopy.widgetDescription("All caught up", body),
            )

            assertEquals("12 reviews · 3 new", WidgetTextCopy.dueSplitLabel(12, 3))
            assertEquals("1 review · 2 new", WidgetTextCopy.dueSplitLabel(1, 2))
            assertEquals("0 reviews · 0 new", WidgetTextCopy.dueSplitLabel(-1, -2))
            val later = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 0)
            }.timeInMillis
            assertEquals("5 more by 18:00", WidgetTextCopy.dueLaterLabel(5, later))
            assertEquals("", WidgetTextCopy.dueLaterLabel(0, later))
            assertEquals("", WidgetTextCopy.dueLaterLabel(5, 0L))
            assertEquals("Best: 21 days", WidgetTextCopy.bestStreakLabel(21))
            assertEquals("Best: 1 day", WidgetTextCopy.bestStreakLabel(1))
            assertEquals("", WidgetTextCopy.bestStreakLabel(0))
            assertEquals("Widget options", WidgetTextCopy.widgetConfigTitle())
            assertEquals("Style", WidgetTextCopy.widgetStyleSectionTitle())
            assertEquals("Due card", WidgetTextCopy.widgetStyleDueCardLabel())
            assertEquals("Heatmap", WidgetTextCopy.widgetStyleHeatmapLabel())
            assertEquals("Theme", WidgetTextCopy.widgetThemeSectionTitle())
            assertEquals("Follow app", WidgetTextCopy.widgetThemeFollowAppLabel())
            assertEquals("Save", WidgetTextCopy.widgetSaveLabel())
        }
    }

    @Test
    fun japaneseCopyCoversSetupDueAndNothingDueStates() {
        withLocale(Locale.JAPANESE) {
            assertEquals("カニ", WidgetTextCopy.appName())
            assertEquals("Kaniを設定", WidgetTextCopy.notSetUpTitle())
            assertEquals("Kaniを開いて学習キューを設定します。", WidgetTextCopy.notSetUpBody())
            assertEquals("Kaniを開く", WidgetTextCopy.openKaniLabel())
            assertEquals("復習できるカード1件", WidgetTextCopy.dueCountLabel(1))
            assertEquals("3日連続", WidgetTextCopy.streakLabel(3))
            assertEquals("今すぐ学習", WidgetTextCopy.studyNowLabel())
            assertEquals("復習完了", WidgetTextCopy.nothingDueTitle())
            assertEquals("3日連続 · 復習なし", WidgetTextCopy.nothingDueBody(3, 0L))
            assertEquals(
                "Kaniウィジェット。復習完了。3日連続 · 復習なし",
                WidgetTextCopy.widgetDescription("復習完了", "3日連続 · 復習なし"),
            )

            assertEquals("復習12件 · 新規3件", WidgetTextCopy.dueSplitLabel(12, 3))
            val later = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 0)
            }.timeInMillis
            assertEquals("18:00までにあと5件", WidgetTextCopy.dueLaterLabel(5, later))
            assertEquals("", WidgetTextCopy.dueLaterLabel(0, later))
            assertEquals("最長21日", WidgetTextCopy.bestStreakLabel(21))
            assertEquals("", WidgetTextCopy.bestStreakLabel(0))
            assertEquals("ウィジェット設定", WidgetTextCopy.widgetConfigTitle())
            assertEquals("スタイル", WidgetTextCopy.widgetStyleSectionTitle())
            assertEquals("復習カード", WidgetTextCopy.widgetStyleDueCardLabel())
            assertEquals("ヒートマップ", WidgetTextCopy.widgetStyleHeatmapLabel())
            assertEquals("テーマ", WidgetTextCopy.widgetThemeSectionTitle())
            assertEquals("アプリに合わせる", WidgetTextCopy.widgetThemeFollowAppLabel())
            assertEquals("保存", WidgetTextCopy.widgetSaveLabel())
        }
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
