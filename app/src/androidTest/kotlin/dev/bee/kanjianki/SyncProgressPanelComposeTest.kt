package dev.bee.kanjianki

import android.widget.TextView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SyncProgressPanelComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersSyncProgressTitle() {
        composeRule.setContent {
            SyncProgressTitle("Syncing cards")
        }

        composeRule.onNodeWithText("Syncing cards").assertIsDisplayed()
    }

    @Test
    fun rendersSyncProgressScreenTitle() {
        val panel = SyncProgressPanel(ApplicationProvider.getApplicationContext())

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = panel
            )
        }

        composeRule.onNodeWithText("Syncing cards").assertIsDisplayed()
    }

    @Test
    fun rehostsProgressPanelWhenPanelIdentityChanges() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val firstPanel = TextView(context).apply { text = "First panel" }
        val secondPanel = TextView(context).apply { text = "Second panel" }
        val activePanel = mutableStateOf<android.view.View>(firstPanel)

        composeRule.setContent {
            SyncProgressScreen(
                title = "Syncing cards",
                progressPanel = activePanel.value
            )
        }

        composeRule.waitForIdle()
        assertNotNull(firstPanel.parent)

        composeRule.runOnIdle {
            activePanel.value = secondPanel
        }
        composeRule.waitForIdle()

        assertNotNull(secondPanel.parent)
        assertNull(firstPanel.parent)
    }
}
