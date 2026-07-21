package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
class KaniBottomNavComposeUnitTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsSubroutesKeepSettingsTabSelected() {
        composeRule.setContent {
            KaniBottomNavBar(
                selectedRoute = MainActivityBase.NAV_SETTINGS_UPDATE_ROUTE,
                actions = KaniNavActions(
                    onHome = {},
                    onStudy = {},
                    onStats = {},
                    onSettings = {},
                ),
            )
        }

        composeRule.onNodeWithTag(kaniNavItemTestTag(MainActivityBase.NAV_SETTINGS_ROUTE))
            .assertIsSelected()
        composeRule.onNodeWithTag(kaniNavItemTestTag(MainActivityBase.NAV_HOME_ROUTE))
            .assertIsNotSelected()
    }

    @Test
    fun studyBadgeShowsCappedDueCount() {
        composeRule.setContent {
            KaniBottomNavBar(
                selectedRoute = MainActivityBase.NAV_HOME_ROUTE,
                studyBadgeCount = 132,
                actions = KaniNavActions(
                    onHome = {},
                    onStudy = {},
                    onStats = {},
                    onSettings = {},
                ),
            )
        }

        composeRule.onAllNodesWithTag("kani-nav-badge", useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("99+", useUnmergedTree = true).assertCountEquals(1)
        assertEquals("7", kaniNavBadgeLabel(7))
        assertEquals("99+", kaniNavBadgeLabel(100))
    }

    @Test
    fun studyBadgeIsHiddenWhenDueCountIsZero() {
        composeRule.setContent {
            KaniBottomNavBar(
                selectedRoute = MainActivityBase.NAV_HOME_ROUTE,
                studyBadgeCount = 0,
                actions = KaniNavActions(
                    onHome = {},
                    onStudy = {},
                    onStats = {},
                    onSettings = {},
                ),
            )
        }

        composeRule.onAllNodesWithTag("kani-nav-badge", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun accessibilityFontScaleUsesTwoRowsWithoutClippingLabels() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                Box(Modifier.width(324.dp)) {
                    KaniBottomNavBar(
                        selectedRoute = MainActivityBase.NAV_HOME_ROUTE,
                        actions = navActions(),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val home = navBounds(MainActivityBase.NAV_HOME_ROUTE)
        val study = navBounds(MainActivityBase.NAV_STUDY)
        val stats = navBounds(MainActivityBase.NAV_STATS_ROUTE)
        val settings = navBounds(MainActivityBase.NAV_SETTINGS_ROUTE)
        assertEquals(home.top, study.top, 0.5f)
        assertEquals(stats.top, settings.top, 0.5f)
        assertTrue("Second nav row should follow the first: home=$home, stats=$stats", stats.top >= home.bottom)
        listOf("Home", "Study", "Stats", "Settings").forEach(::assertSingleLineTextFits)
    }

    private fun navActions() = KaniNavActions(
        onHome = {},
        onStudy = {},
        onStats = {},
        onSettings = {},
    )

    private fun navBounds(route: String) =
        composeRule.onNodeWithTag(kaniNavItemTestTag(route)).fetchSemanticsNode().boundsInRoot

    private fun assertSingleLineTextFits(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(results))
            }
        val layout = results.single()
        assertFalse("$text overflowed horizontally: $layout", layout.didOverflowWidth)
        assertFalse("$text overflowed vertically: $layout", layout.didOverflowHeight)
        assertEquals("$text should remain on one line: $layout", 1, layout.lineCount)
    }
}
