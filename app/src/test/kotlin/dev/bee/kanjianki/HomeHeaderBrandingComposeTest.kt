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
    fun alphaAndByBeeAppearBelowTheKaniLogo() {
        composeRule.setContent {
            Box(Modifier.width(360.dp)) {
                HomeHeader(title = "Kani", subtitle = "")
            }
        }
        composeRule.waitForIdle()

        val logo = composeRule.onNodeWithContentDescription("Kani logo")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val alpha = composeRule.onNodeWithText("Alpha")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val byBee = composeRule.onNodeWithText("By Bee")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot

        assertTrue("Alpha should sit below the logo: logo=$logo, alpha=$alpha", alpha.top >= logo.bottom)
        val horizontalGap = byBee.left - alpha.right
        assertTrue(
            "By Bee should sit beside Alpha with a small gap: alpha=$alpha, byBee=$byBee",
            horizontalGap in 1f..24f,
        )
        assertTrue(
            "By Bee should vertically overlap Alpha's row: alpha=$alpha, byBee=$byBee",
            byBee.top < alpha.bottom && byBee.bottom > alpha.top,
        )
        assertTrue("By Bee should use smaller text than Alpha", textSize("By Bee") < textSize("Alpha"))
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
