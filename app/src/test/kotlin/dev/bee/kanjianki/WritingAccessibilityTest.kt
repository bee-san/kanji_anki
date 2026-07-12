package dev.bee.kanjianki

import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.StudyWritingCopy
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
class WritingAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun drawingPadExposesAnswerSafeInstructionsAndUpdatedStrokeState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pad = DrawingPadView(context)
        val exactSize = View.MeasureSpec.makeMeasureSpec(PAD_SIZE, View.MeasureSpec.EXACTLY)
        pad.measure(exactSize, exactSize)
        pad.layout(0, 0, PAD_SIZE, PAD_SIZE)
        pad.setTarget("裂")

        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, pad.importantForAccessibility)
        assertEquals(StudyWritingCopy.drawingPadDescription(), pad.contentDescription)
        assertEquals(StudyWritingCopy.drawingPadStrokeState(0), ViewCompat.getStateDescription(pad))
        assertFalse(pad.contentDescription.contains("裂"))

        sendTouch(pad, 100L, 100L, MotionEvent.ACTION_DOWN, 100f, 100f)
        sendTouch(pad, 100L, 120L, MotionEvent.ACTION_UP, 300f, 300f)
        assertEquals(StudyWritingCopy.drawingPadStrokeState(1), ViewCompat.getStateDescription(pad))

        sendTouch(pad, 200L, 200L, MotionEvent.ACTION_DOWN, 500f, 500f)
        sendTouch(pad, 200L, 220L, MotionEvent.ACTION_UP, 700f, 700f)
        assertEquals(StudyWritingCopy.drawingPadStrokeState(2), ViewCompat.getStateDescription(pad))

        assertTrue(pad.undoLastStroke())
        assertEquals(StudyWritingCopy.drawingPadStrokeState(1), ViewCompat.getStateDescription(pad))

        pad.clear()
        assertEquals(StudyWritingCopy.drawingPadStrokeState(0), ViewCompat.getStateDescription(pad))
    }

    @Test
    fun writingGuidanceUpdatesUseAPoliteLiveRegion() {
        val state = WritingStatusState().apply {
            setStatus("Stay close to stroke 1.", MainActivityUiSupport.CORAL)
        }

        composeRule.setContent {
            WritingStatusText(state)
        }

        composeRule.onNodeWithText("Stay close to stroke 1.")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
    }

    private fun sendTouch(
        pad: DrawingPadView,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        try {
            pad.onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private companion object {
        const val PAD_SIZE = 1000
    }
}
