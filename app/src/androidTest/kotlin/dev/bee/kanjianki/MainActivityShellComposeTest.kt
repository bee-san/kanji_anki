package dev.bee.kanjianki

import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    @Test
    fun swapsHostedRootWhenLegacyRootChanges() {
        var legacyRoot by mutableStateOf(textRoot("First shell root"))

        composeRule.setContent {
            MainActivityShell(legacyRoot)
        }

        composeRule.onNodeWithText("First shell root").assertIsDisplayed()
        composeRule.runOnIdle {
            legacyRoot = textRoot("Second shell root")
        }

        composeRule.onNodeWithText("Second shell root").assertIsDisplayed()
        composeRule.onAllNodesWithText("First shell root").assertCountEquals(0)
    }

    private fun textRoot(text: String): TextView {
        return TextView(ApplicationProvider.getApplicationContext()).apply {
            this.text = text
        }
    }
}
