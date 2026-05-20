package dev.bee.kanjianki

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityStudyWritingPadComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hostsDrawingPadInsideSquareFrame() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val drawingPad = DrawingPadView(context)

        composeRule.setContent {
            WritingPadPanel(drawingPad = drawingPad, maxSizePx = 360)
        }
        composeRule.waitForIdle()

        assertTrue(drawingPad.parent is MainActivityUiSupport.SquarePadFrame)
    }
}
