package dev.bee.kanjianki

import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
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
            MainActivityShell(
                legacyRoot = legacyRoot,
                model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STUDY)
            )
        }

        composeRule.onNodeWithTag("main-activity-shell")
            .assert(hasContentDescription("Kani shell ${MainActivityBase.NAV_STUDY}"))
        composeRule.onNodeWithTag("main-route-${MainActivityBase.NAV_STUDY}")
            .assert(hasContentDescription("Kani route ${MainActivityBase.NAV_STUDY}"))
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

    @Test
    fun hostsComposeRouteContentWithoutLegacyRoot() {
        composeRule.setContent {
            MainActivityComposeRoute(
                model = MainActivityShellModel(selectedRoute = "stats")
            ) {
                Text("Compose stats content")
            }
        }

        composeRule.onNodeWithTag("main-activity-shell")
            .assert(hasContentDescription("Kani shell stats"))
        composeRule.onNodeWithTag("main-route-stats")
            .assert(hasContentDescription("Kani route stats"))
        composeRule.onNodeWithText("Compose stats content").assertIsDisplayed()
    }

    @Test
    fun reportsComposeRouteScrollPosition() {
        var latestScrollY = -1

        composeRule.setContent {
            MainActivityComposeRoute(
                model = MainActivityShellModel(selectedRoute = "stats"),
                initialScrollY = 24,
                onScrollY = { latestScrollY = it },
            ) {
                Column {
                    Text("Scrollable route top")
                    Spacer(modifier = Modifier.width(1.dp).height(1600.dp))
                    Text("Scrollable route bottom")
                }
            }
        }

        composeRule.onNodeWithText("Scrollable route bottom").performScrollTo()
        composeRule.waitUntil {
            latestScrollY > 24
        }
    }

    private fun textRoot(text: String): TextView {
        return TextView(ApplicationProvider.getApplicationContext()).apply {
            this.text = text
        }
    }
}
