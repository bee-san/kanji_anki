package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
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
    fun narrowHomeMetricValuesWrapWithoutOverflowing() {
        composeRule.setContent {
            Box(Modifier.width(360.dp)) {
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

        assertTextFits("11 Jul 2026")
        assertTextFits("No streak yet")
    }

    private fun assertTextFits(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(results))
            }
        val layout = results.single()
        assertFalse("$text overflowed horizontally: $layout", layout.didOverflowWidth)
        assertFalse("$text overflowed vertically: $layout", layout.didOverflowHeight)
        assertTrue("$text should use at most two lines: $layout", layout.lineCount <= 2)
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
