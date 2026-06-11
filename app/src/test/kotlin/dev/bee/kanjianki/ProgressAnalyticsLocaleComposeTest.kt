package dev.bee.kanjianki

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import dev.bee.kanjianki.progress.progressAnalyticsDemoSnapshot
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgressAnalyticsLocaleComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dashboardRendersJapaneseProgressAnalyticsCopy() = withDefaultLocale(Locale.JAPAN) {
        val state = progressAnalyticsDemoSnapshot(1_747_000_000_000L)

        composeRule.setContent {
            ProgressAnalyticsDashboardScreen(state)
        }
        composeRule.waitForIdle()

        assertTextExists("統計の概要")
        assertTextExists("復習合計")
        assertTextExists("正答率")
        assertTextExists("最高14日")
        assertTextExists("復習分析")
        assertTextExists("1日平均")
        assertTextExists("最多の日")
        assertTextExists("正答率と定着")
        assertTextExists("レベル別進捗")
        assertTextExists("全レベルの学習済み")
        assertTextExists("弱点の分析")
        assertTextExists("集中スコア")
        assertTextExists("ミス42回")
        assertTextExists("支援が必要")
    }

    @Test
    fun bottomNavRendersJapaneseLabels() = withDefaultLocale(Locale.JAPAN) {
        composeRule.setContent {
            ProgressAnalyticsBottomNav(
                selectedTab = ProgressAnalyticsBottomNavTab.Progress,
                onHome = {},
                onStudy = {},
                onProgress = {},
                onProfile = {},
            )
        }
        composeRule.waitForIdle()

        assertTextExists("ホーム")
        assertTextExists("学習")
        assertTextExists("進捗")
        assertTextExists("プロフィール")
    }

    private fun assertTextExists(text: String) {
        assertTrue(
            "Expected Compose text <$text> to exist",
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private inline fun withDefaultLocale(locale: Locale, block: () -> Unit) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
