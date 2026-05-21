package dev.bee.kanjianki

import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test

class MainActivityShellComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hostsLegacyRootInsideComposeShell() {
        val legacyRoot = TextView(ApplicationProvider.getApplicationContext()).apply {
            text = "Legacy shell content"
        }

        composeRule.setContent {
            MainActivityShell(legacyRoot)
        }

        composeRule.onNodeWithText("Legacy shell content").assertIsDisplayed()
    }
}
