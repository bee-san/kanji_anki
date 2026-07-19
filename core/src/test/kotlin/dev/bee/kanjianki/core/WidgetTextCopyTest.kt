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
            assertEquals("Study", WidgetTextCopy.studyLabel())
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
            assertEquals("Kani unavailable", WidgetTextCopy.errorTitle())
            assertEquals(
                "Open Kani to recover your local study data.",
                WidgetTextCopy.errorBody(),
            )
            assertEquals("Due", WidgetTextCopy.quickDueStatus())
            assertEquals("Caught up", WidgetTextCopy.quickCaughtUpStatus())
            assertEquals("Set up", WidgetTextCopy.quickSetupStatus())
            assertEquals("Unavailable", WidgetTextCopy.quickErrorStatus())
            assertEquals("999+", WidgetTextCopy.visualCountLabel(1_204))
            assertEquals("0", WidgetTextCopy.visualCountLabel(-2))
            assertEquals("87 reviews", WidgetTextCopy.reviewCountLabel(87))
            assertEquals("87 reviews in 35 days", WidgetTextCopy.activityPeriodLabel(87, 35))
            assertEquals("87 · 35 days", WidgetTextCopy.activityPeriodShortLabel(87, 35))
            assertEquals("No activity yet", WidgetTextCopy.noActivityTitle())
            assertEquals("Reviews will appear here.", WidgetTextCopy.noActivityBody())
            assertEquals("Open stats", WidgetTextCopy.openStatsLabel())
            assertEquals("Stats", WidgetTextCopy.statsLabel())
            assertEquals(
                "Activity widget. 87 reviews in 35 days, 4 today. 5-day streak. Best: 21 days. Open stats.",
                WidgetTextCopy.activityDescription(87, 35, 4, 5, 21, "Open stats"),
            )
            assertEquals(
                "Quick study widget. 3 reviews ready. Study now.",
                WidgetTextCopy.quickStudyDescription("3 reviews ready", "Study now"),
            )
            assertEquals("Focus kanji", WidgetTextCopy.focusKanjiLabel())
            assertEquals("Details", WidgetTextCopy.focusDetailsLabel())
            assertEquals("Due now", WidgetTextCopy.focusDueStatus())
            assertEquals("No focus kanji", WidgetTextCopy.focusEmptyTitle())
            assertEquals(
                "Open Kani to sync eligible study items.",
                WidgetTextCopy.focusEmptyBody(),
            )
            assertEquals(
                "Focus kanji widget. 学. learn. Reading: がく. Due now. Details. Study now.",
                WidgetTextCopy.focusKanjiDescription(
                    kanji = "学",
                    primaryMeaning = "learn",
                    readings = "がく",
                    isDueNow = true,
                    hasStudyAction = true,
                ),
            )
            assertEquals(
                "Focus kanji widget. 学. learn. Details.",
                WidgetTextCopy.focusKanjiDescription(
                    kanji = "学",
                    primaryMeaning = "learn",
                    readings = "",
                    isDueNow = false,
                    hasStudyAction = false,
                ),
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
            assertEquals("学習", WidgetTextCopy.studyLabel())
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
            assertEquals("Kaniを利用できません", WidgetTextCopy.errorTitle())
            assertEquals("Kaniを開いてローカル学習データを復旧します。", WidgetTextCopy.errorBody())
            assertEquals("期限", WidgetTextCopy.quickDueStatus())
            assertEquals("復習完了", WidgetTextCopy.quickCaughtUpStatus())
            assertEquals("設定", WidgetTextCopy.quickSetupStatus())
            assertEquals("利用不可", WidgetTextCopy.quickErrorStatus())
            assertEquals("復習87件", WidgetTextCopy.reviewCountLabel(87))
            assertEquals("35日間で復習87件", WidgetTextCopy.activityPeriodLabel(87, 35))
            assertEquals("学習履歴はまだありません", WidgetTextCopy.noActivityTitle())
            assertEquals("復習するとここに表示されます。", WidgetTextCopy.noActivityBody())
            assertEquals("統計を開く", WidgetTextCopy.openStatsLabel())
            assertEquals("統計", WidgetTextCopy.statsLabel())
            assertEquals(
                "学習履歴ウィジェット。35日間で復習87件、今日4件。5日連続。最長21日。統計を開く。",
                WidgetTextCopy.activityDescription(87, 35, 4, 5, 21, "統計を開く"),
            )
            assertEquals(
                "クイック学習ウィジェット。復習できるカード3件。今すぐ学習。",
                WidgetTextCopy.quickStudyDescription("復習できるカード3件", "今すぐ学習"),
            )
            assertEquals("注目の漢字", WidgetTextCopy.focusKanjiLabel())
            assertEquals("詳細", WidgetTextCopy.focusDetailsLabel())
            assertEquals("今すぐ復習", WidgetTextCopy.focusDueStatus())
            assertEquals("注目の漢字なし", WidgetTextCopy.focusEmptyTitle())
            assertEquals(
                "Kaniを開いて学習対象を同期します。",
                WidgetTextCopy.focusEmptyBody(),
            )
            assertEquals(
                "注目の漢字ウィジェット。学。学ぶ。読み：がく。今すぐ復習。詳細。今すぐ学習。",
                WidgetTextCopy.focusKanjiDescription(
                    kanji = "学",
                    primaryMeaning = "学ぶ",
                    readings = "がく",
                    isDueNow = true,
                    hasStudyAction = true,
                ),
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
