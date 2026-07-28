package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeHeaderBrandingComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun alphaAppearsUnderWordmarkWithSmallerByBeeToItsRight() {
        composeRule.setContent {
            Box(Modifier.width(360.dp)) {
                HomeHeader(title = "Kani", subtitle = "")
            }
        }
        composeRule.waitForIdle()

        val wordmark = composeRule.onNodeWithText("Kani")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val logo = composeRule.onNodeWithContentDescription("Kani logo")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val alpha = composeRule.onNodeWithText("Alpha")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val byBee = composeRule.onNodeWithText("By Bee")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertTrue("Alpha should sit below the Kani wordmark: wordmark=$wordmark, alpha=$alpha", alpha.top >= wordmark.bottom)
        assertTrue("Alpha should align with the wordmark's left edge: wordmark=$wordmark, alpha=$alpha", kotlin.math.abs(alpha.left - wordmark.left) <= 2f)
        assertTrue("Brand text should remain left of the logo: alpha=$alpha, logo=$logo", alpha.right <= logo.left)
        val horizontalGap = byBee.left - alpha.right
        assertTrue(
            "By Bee should sit beside Alpha with a small gap: alpha=$alpha, byBee=$byBee",
            horizontalGap in 1f..24f,
        )
        assertTrue(
            "By Bee should vertically overlap Alpha's row: alpha=$alpha, byBee=$byBee",
            byBee.top < alpha.bottom && byBee.bottom > alpha.top,
        )
        val alphaSize = textSize("Alpha")
        val byBeeSize = textSize("By Bee")
        assertTrue("Alpha should be a readable secondary wordmark", alphaSize in 18f..24f)
        assertTrue("By Bee should be smaller but still readable", byBeeSize in 12f..16f)
        assertTrue("By Bee should use smaller text than Alpha", byBeeSize < alphaSize)
    }

    private fun textSize(text: String): Float {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(results))
            }
        return results.single().layoutInput.style.fontSize.value
    }
}
