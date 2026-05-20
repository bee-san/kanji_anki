package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class StudyTopBarComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersProgressAndWiresCallbacks() {
        val closeClicks = AtomicInteger()
        val settingsClicks = AtomicInteger()

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(260.dp)) {
                    StudyTopBar(
                        completed = 2,
                        target = 5,
                        fraction = 0.4f,
                        onClose = { closeClicks.incrementAndGet() },
                        onSettings = { settingsClicks.incrementAndGet() }
                    )
                }
            }
        }

        composeRule.onNodeWithText("2 / 5").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close study").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(StudyTopBarDescriptions.PROGRESS)
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.4f, 0f..1f))

        composeRule.onNodeWithContentDescription("Close study").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.runOnIdle {
            assertEquals(1, closeClicks.get())
            assertEquals(1, settingsClicks.get())
        }
    }
}
