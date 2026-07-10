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
