package dev.bee.kanjianki

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

internal class MainActivityShellActivityComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityBootsThroughComposeShell() {
        composeRule.onNodeWithTag("main-activity-shell")
            .assertIsDisplayed()
            .assert(hasContentDescription("Kani shell home"))
    }
}
