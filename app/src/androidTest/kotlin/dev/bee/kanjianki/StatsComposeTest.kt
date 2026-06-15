package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.bee.kanjianki.core.HomeTextCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val INK = 0xFF2D1635.toInt()
private const val MUTED = 0xFF6C5674.toInt()
private const val CORAL = 0xFFFF4C76.toInt()
private const val TEAL = 0xFF00AEB5.toInt()
private const val BLUE = 0xFF6E5CE6.toInt()
private const val GOLD = 0xFFFFD640.toInt()
private const val VERDICT_FILL = 0xFFEEFCFA.toInt()

class StatsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersStatsRouteWithHomeButton() {
        var homeClicked = false

        composeRule.setContent {
            Box(modifier = Modifier.size(360.dp, 720.dp)) {
                StatsRouteScreen(
                    model = StatsScreenModel(
                        title = "Stats",
                        intro = "Stats intro",
                        verdict = StatsCardModel(
                            title = "Kani is working",
                            body = "Evidence is improving.",
                            fillColor = VERDICT_FILL,
                            strokeColor = TEAL,
                        ),
                        sections = emptyList(),
                    ),
                    onHome = { homeClicked = true },
                )
            }
        }

        composeRule.onNodeWithTag(homeFullWidthHomeButtonTestTag(HomeTextCopy.homeLabel())).performClick()
        composeRule.onNodeWithText("Stats").assertIsDisplayed()

        composeRule.runOnIdle {
            assertTrue(homeClicked)
        }
    }

    @Test
    fun rendersTheStatsHeadingsAndCopy() {
        composeRule.setContent {
            StatsRouteScreen(
                model = StatsScreenModel(
                    title = "Stats",
                    intro = "Kani repairs weak kanji from Anki reviews and shows progress.",
                    verdict = StatsCardModel(
                        title = "Kani is working",
                        body = "Weak kanji and support are both improving.",
                        fillColor = VERDICT_FILL,
                        strokeColor = TEAL,
                        titleColor = TEAL,
                        bodyColor = INK,
                        titleSizeSp = 24,
                        bodySizeSp = 15,
                    ),
                    sections = listOf(
                        StatsCardModel(
                            title = "Weak kanji trend",
                            summary = "3 weak kanji improved",
                            body = "Average weakness fell from 2.1 to 1.4.",
                            lines = listOf(
                                StatsLineModel(
                                    text = "裂: 88 -> 33",
                                    color = INK,
                                    bold = true,
                                    sizeSp = 17,
                                ),
                            ),
                            strokeColor = TEAL,
                        ),
                        StatsCardModel(
                            title = "Anki support",
                            summary = "2 mature cards gained",
                            body = "1 kanji gained first mature support.",
                            lines = listOf(
                                StatsLineModel(
                                    text = "律: 0 -> 1",
                                    color = INK,
                                    bold = true,
                                    sizeSp = 17,
                                ),
                            ),
                            strokeColor = BLUE,
                        ),
                    ),
                ),
                onHome = {},
            )
        }

        composeRule.onNodeWithText("Stats").assertIsDisplayed()
        composeRule.onNodeWithText("Kani repairs weak kanji from Anki reviews and shows progress.").assertIsDisplayed()
        composeRule.onNodeWithText("Weak kanji trend").assertIsDisplayed()
        composeRule.onNodeWithText("Anki support").assertIsDisplayed()
    }

    @Test
    fun rendersCompactStatsScreenWithinPhoneWidth() {
        composeRule.setContent {
            MainActivityComposeRoute(
                model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STATS_ROUTE)
            ) {
                StatsRouteScreen(
                    model = compactStatsScreenModel(),
                    onHome = {},
                )
            }
        }

        composeRule.onNodeWithText("Stats").assertIsDisplayed()
        composeRule.onNodeWithTag(homeFullWidthHomeButtonTestTag(HomeTextCopy.homeLabel())).assertIsDisplayed()
        composeRule.onNodeWithText("Weak kanji trend").assertIsDisplayed()
        composeRule.onNodeWithText("Average weakness fell from 2.1 to 1.4.").assertIsDisplayed()
        composeRule.onNodeWithText("裂: 88 -> 33").assertIsDisplayed()
        composeRule.onNodeWithText("Ladder status").assertIsDisplayed()
        composeRule.onNodeWithText("Study time").performScrollTo()
        composeRule.onNodeWithText("Study time").assertIsDisplayed()
    }

    @Test
    fun rendersScreenshotStatsFixtureWithinPhoneWidth() {
        composeRule.setContent {
            Box(modifier = Modifier.size(360.dp, 720.dp)) {
                MainActivityComposeRoute(
                    model = MainActivityShellModel(selectedRoute = MainActivityBase.NAV_STATS_ROUTE)
                ) {
                    StatsRouteScreen(
                        model = screenshotStatsScreenModel(),
                        onHome = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Stats").assertIsDisplayed()
        composeRule.onNodeWithTag(homeFullWidthHomeButtonTestTag(HomeTextCopy.homeLabel())).assertIsDisplayed()
        composeRule.onNodeWithText("Working").assertIsDisplayed()
        composeRule.onNodeWithText("The screenshot route is deterministic and ready for...").assertIsDisplayed()
        composeRule.onNodeWithText("川 12.4 → 8.2").assertIsDisplayed()
        composeRule.onNodeWithText("海 11.8 → 7.9").assertIsDisplayed()
        composeRule.onNodeWithText("森 10.5 → 7.1").assertIsDisplayed()
        composeRule.onNodeWithText("Anki support").assertIsDisplayed()
        composeRule.onNodeWithText("復 gained 2 mature cards").assertIsDisplayed()
        composeRule.onNodeWithText("語 gained 1 mature card").assertIsDisplayed()
        composeRule.onNodeWithText("Ladder status").performScrollTo()
        composeRule.onNodeWithText("Promotion ready: 2").performScrollTo()
        composeRule.onNodeWithText("Promotion ready: 2").assertIsDisplayed()
        composeRule.onNodeWithText("Demotion risk: 1").performScrollTo()
        composeRule.onNodeWithText("Demotion risk: 1").assertIsDisplayed()
        composeRule.onNodeWithText("Inactive: 3").performScrollTo()
        composeRule.onNodeWithText("Inactive: 3").assertIsDisplayed()
    }

    @Test
    fun rendersNoStatsVerdictAsSharedEmptyState() {
        composeRule.setContent {
            StatsScreen(
                model = StatsScreenModel(
                    title = "Stats",
                    intro = "Kani does not replace Anki.",
                    verdict = StatsCardModel(
                        title = "No Kani impact evidence yet",
                        body = "Study weak kanji, then sync AnkiDroid so this page can compare before and after.",
                        strokeColor = STATS_MUTED_COLOR,
                        emptyState = true,
                    ),
                    sections = emptyList(),
                ),
            )
        }

        composeRule.onNodeWithTag(homeEmptyStateTestTag("No Kani impact evidence yet")).assertIsDisplayed()
        composeRule.onNodeWithText("Study weak kanji, then sync AnkiDroid so this page can compare before and after.").assertIsDisplayed()
    }
}

private fun compactStatsScreenModel(): StatsScreenModel {
    return StatsScreenModel(
        title = "Stats",
        intro = "Kani keeps the strongest summary cards readable on a 360dp phone width.",
        verdict = StatsCardModel(
            title = "Kani is working",
            body = "Evidence is improving.",
            fillColor = VERDICT_FILL,
            strokeColor = TEAL,
            titleColor = TEAL,
            bodyColor = INK,
            titleSizeSp = 24,
            bodySizeSp = 15,
        ),
        sections = listOf(
            StatsCardModel(
                title = "Weak kanji trend",
                summary = "3 weak kanji improved",
                body = "Average weakness fell from 2.1 to 1.4.",
                lines = listOf(
                    StatsLineModel(
                        text = "裂: 88 -> 33",
                        color = INK,
                        bold = true,
                        sizeSp = 17,
                    ),
                ),
                strokeColor = TEAL,
            ),
            StatsCardModel(
                title = "Ladder status",
                summary = "6 active kanji on the ladder",
                body = "2 are ready to promote; 1 is at demotion risk.",
                lines = listOf(
                    StatsLineModel(
                        text = "Learn: 2",
                        color = INK,
                        bold = false,
                        sizeSp = 16,
                    ),
                    StatsLineModel(
                        text = "Review: 3",
                        color = INK,
                        bold = false,
                        sizeSp = 16,
                    ),
                ),
                strokeColor = GOLD,
            ),
            StatsCardModel(
                title = "Study time",
                summary = "Today: 12m 30s",
                body = "Last 7 days: 1h 12m",
                lines = listOf(
                    StatsLineModel(
                        text = "Answered tasks: 9",
                        color = MUTED,
                        bold = false,
                        sizeSp = 16,
                    ),
                    StatsLineModel(
                        text = "Avg / task: 1m 23s",
                        color = MUTED,
                        bold = false,
                        sizeSp = 16,
                    ),
                ),
                strokeColor = CORAL,
                titleColor = MUTED,
                summaryColor = INK,
                bodyColor = MUTED,
                titleSizeSp = 18,
                summarySizeSp = 24,
                bodySizeSp = 16,
            ),
            StatsCardModel(
                title = "Anki support",
                summary = "2 mature cards gained",
                body = "Enough mature support has accumulated to keep the loop moving.",
                lines = listOf(
                    StatsLineModel(
                        text = "復 gained 2 mature cards",
                        color = INK,
                        bold = true,
                        sizeSp = 18,
                    ),
                    StatsLineModel(
                        text = "語 gained 1 mature card",
                        color = INK,
                        bold = true,
                        sizeSp = 18,
                    ),
                ),
                strokeColor = BLUE,
            ),
        ),
    )
}
