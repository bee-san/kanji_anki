package dev.bee.kanjianki

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import dev.bee.kanjianki.progress.progressAnalyticsDemoSnapshot
import java.util.Locale
import org.junit.Assert.assertEquals
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
        assertEquals("残りの項目", state.forecast?.burnDown?.title)
        assertEquals("残り", state.forecast?.burnDown?.series?.single()?.label)

        composeRule.setContent {
            ProgressAnalyticsDashboardScreen(state)
        }
        composeRule.waitForIdle()

        assertTextExists("統計の概要")
        assertTextExists("復習合計")
        assertTextExists("正答率")
        assertTextExists("復習分析")
        assertTextExists("現在の連続日数")
        assertTextExists("最長の連続日数")
        assertTextExists("14日")
        assertTextExists("1日平均")
        assertTextExists("最多の日")
        assertTextExists("段階別正答率")
        assertTextExists("ラダー段階の分布")
        assertTextExists("弱点の分析")
        assertTextExists("集中スコア")
        assertTextExists("ミス42回")
        assertTextExists("支援が必要")
    }

    private fun assertTextExists(text: String) {
        assertTrue(
            "Expected Compose text <$text> to exist",
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty(),
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
