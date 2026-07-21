package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccessibilityLayoutComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gamesModeCardContainsTitleAndStatusAtAccessibilityFontScale() {
        setAccessibilityContent {
            GamesScreen(
                GamesScreenModel(
                    title = "Games",
                    subtitle = "Practice without changing reviews.",
                    emptyTitle = null,
                    emptyBody = null,
                    showSyncButton = false,
                    onSync = Runnable {},
                    modeCards = listOf(
                        GamesModeCardModel(
                            title = "Confusable Clash",
                            label = "Meaning to kanji",
                            body = "Choose among similar kanji.",
                            accentColor = MainActivityUiSupport.BLUE,
                            available = false,
                            chipLabel = "Needs data",
                            onClick = Runnable {},
                        ),
                    ),
                ),
            )
        }

        assertTextFits("Confusable Clash", maxLines = 3)
        assertTextFits("Needs data", maxLines = 1)
    }

    @Test
    fun gamesScoreStripContainsLabelsAndValuesAtAccessibilityFontScale() {
        setAccessibilityContent {
            GamesScoreStrip(
                GamesScoreStripModel(
                    roundLabel = "Round",
                    roundValue = "10/10",
                    scoreLabel = "Score",
                    scoreValue = "10/10",
                    streakLabel = "Streak",
                    streakValue = "10",
                ),
            )
        }

        listOf("Round", "Score", "Streak", "10").forEach {
            assertTextFits(it, maxLines = 1)
        }
        repeat(2) { index ->
            val results = mutableListOf<TextLayoutResult>()
            composeRule.onAllNodesWithText("10/10", useUnmergedTree = true)[index]
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                    assertTrue(action(results))
                }
            assertLayoutFits(results.single(), "10/10", maxLines = 1)
        }
    }

    @Test
    fun settingsHubCardContainsTitleAndCountAtAccessibilityFontScale() {
        setAccessibilityContent {
            SettingsScreen(
                SettingsScreenModel(
                    homeLabel = "Home",
                    onHome = Runnable {},
                    title = "Settings",
                    cards = listOf(
                        SettingsHubCardModel(
                            routeKey = MainActivityBase.NAV_SETTINGS_STUDY_BEHAVIOR_ROUTE,
                            title = "Scheduling & study",
                            summary = "Tune workload, retention, and learning steps.",
                            iconRes = R.drawable.ic_study_24,
                            panelCount = "10 cards",
                            contentDescription = SettingsTextCopy.sectionOpenDescription("Scheduling & study"),
                            onOpen = Runnable {},
                        ),
                    ),
                ),
            )
        }

        assertTextFits("Scheduling & study", maxLines = 3)
        assertTextFits("10 cards", maxLines = 1)
    }

    private fun setAccessibilityContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                Box(Modifier.width(324.dp)) {
                    content()
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertTextFits(text: String, maxLines: Int) {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                assertTrue(action(results))
            }
        assertLayoutFits(results.single(), text, maxLines)
    }

    private fun assertLayoutFits(layout: TextLayoutResult, text: String, maxLines: Int) {
        assertFalse("$text overflowed horizontally: $layout", layout.didOverflowWidth)
        assertFalse("$text overflowed vertically: $layout", layout.didOverflowHeight)
        assertTrue("$text should use at most $maxLines lines: $layout", layout.lineCount <= maxLines)
    }
}
