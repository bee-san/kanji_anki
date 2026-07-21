package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeMetricCardComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun phoneWidthHomeMetricsKeepLabelsReadableAndValuesContained() {
        composeRule.setContent {
            Box(Modifier.width(324.dp)) {
                HomeMetricRow(
                    listOf(
                        metric("Sync", "11 Jul 2026", "Tap to sync"),
                        metric("Streak", "No streak yet", "Not done today"),
                        metric("Focus", "5/5 left", null),
                    ),
                )
            }
        }
        composeRule.waitForIdle()

        listOf("Sync", "Streak", "Focus").forEach(::assertSingleLineTextFits)
        assertTextFits("11 Jul 2026", maxLines = 2)
        assertTextFits("No streak yet", maxLines = 2)
    }

    @Test
    fun accessibilityFontScaleStacksMetricsAndKeepsLabelsReadable() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                Box(Modifier.width(324.dp)) {
                    HomeMetricRow(
                        listOf(
                            metric("Sync", "11 Jul 2026", "Tap to sync"),
                            metric("Streak", "No streak yet", "Not done today"),
                            metric("Focus", "5/5 left", null),
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val sync = composeRule.onNodeWithTag(homeMetricCardTestTag("Sync")).fetchSemanticsNode().boundsInRoot
        val streak = composeRule.onNodeWithTag(homeMetricCardTestTag("Streak")).fetchSemanticsNode().boundsInRoot
        val focus = composeRule.onNodeWithTag(homeMetricCardTestTag("Focus")).fetchSemanticsNode().boundsInRoot
        assertEquals(sync.left, streak.left, 0.5f)
        assertEquals(streak.left, focus.left, 0.5f)
        assertTrue("Streak should be below Sync: sync=$sync, streak=$streak", streak.top >= sync.bottom)
        assertTrue("Focus should be below Streak: streak=$streak, focus=$focus", focus.top >= streak.bottom)
        listOf("Sync", "Streak", "Focus").forEach(::assertSingleLineTextFits)
    }

    private fun assertSingleLineTextFits(text: String) {
        assertTextFits(text, maxLines = 1)
    }

    private fun assertTextFits(text: String, maxLines: Int) {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(results))
            }
        val layout = results.single()
        assertFalse("$text overflowed horizontally: $layout", layout.didOverflowWidth)
        assertFalse("$text overflowed vertically: $layout", layout.didOverflowHeight)
        assertTrue("$text should use at most $maxLines lines: $layout", layout.lineCount <= maxLines)
    }

    private fun metric(label: String, value: String, body: String?): HomeMetricModel =
        HomeMetricModel(
            iconRes = R.drawable.ic_stats_24,
            accent = MainActivityUiSupport.TEAL,
            label = label,
            value = value,
            body = body,
            onClick = null,
        )
}
