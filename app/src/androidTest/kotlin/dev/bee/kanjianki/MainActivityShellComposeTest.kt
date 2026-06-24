package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class MainActivityShellComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hostsComposeRouteContentWithoutLegacyRoot() {
        composeRule.setContent {
            MainActivityComposeRoute(
                model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STATS_ROUTE)
            ) {
                Text("Compose stats content")
            }
        }

        composeRule.onNodeWithTag("main-activity-shell")
            .assert(hasContentDescription("Kani shell ${MainActivityBase.NAV_STATS_ROUTE}"))
        composeRule.onNodeWithTag("main-route-${MainActivityBase.NAV_STATS_ROUTE}")
            .assert(hasContentDescription("Kani route ${MainActivityBase.NAV_STATS_ROUTE}"))
        composeRule.onNodeWithText("Compose stats content").assertIsDisplayed()
    }

    @Test
    fun reportsComposeRouteScrollPosition() {
        var latestScrollY = -1

        composeRule.setContent {
            MainActivityComposeRoute(
                model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STATS_ROUTE),
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

    @Test
    fun routeChangeResetsScrollToTop() {
        val selectedRoute = mutableStateOf(MainActivityBase.NAV_STATS_ROUTE)

        composeRule.setContent {
            val route = selectedRoute.value
            MainActivityComposeRoute(
                model = MainActivityShellModel(selectedRoute = route),
            ) {
                Column {
                    Text("$route route top")
                    Spacer(modifier = Modifier.width(1.dp).height(1600.dp))
                    Text("$route route bottom")
                }
            }
        }

        composeRule.onNodeWithText("${MainActivityBase.NAV_STATS_ROUTE} route bottom")
            .performScrollTo()
        composeRule.runOnIdle {
            selectedRoute.value = MainActivityBase.NAV_HOME_ROUTE
        }

        composeRule.onNodeWithText("${MainActivityBase.NAV_HOME_ROUTE} route top")
            .assertIsDisplayed()
    }
}
