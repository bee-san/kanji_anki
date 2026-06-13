package dev.bee.kanjianki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun rendersStatsRouteWithHomeAction() {
        var homeClicked = false

        composeRule.setContent {
            StatsRouteScreen(
                model = StatsScreenModel(
                    title = "Stats",
                    intro = "Stats intro",
                    verdict = StatsCardModel(
                        title = "Kani is working",
                        body = "Evidence is improving.",
                        fillColor = VERDICT_FILL,
                        strokeColor = TEAL
                    ),
                    sections = emptyList()
                ),
                onHome = { homeClicked = true }
            )
        }

        composeRule.onNodeWithTag(homeFullWidthHomeButtonTestTag("Home")).performClick()
        composeRule.onNodeWithText("Stats").assertIsDisplayed()

        composeRule.runOnIdle {
            assertTrue(homeClicked)
        }
    }

    @Test
    fun rendersTheStatsHeadingsAndCopy() {
        composeRule.setContent {
            StatsScreen(
                model = StatsScreenModel(
                    title = "Stats",
                    intro = "Kani repairs weak kanji from Anki reviews and shows whether the evidence improves after sync.",
                    verdict = StatsCardModel(
                        title = "Kani is working",
                        body = "Weak kanji and support are both improving.",
                        fillColor = VERDICT_FILL,
                        strokeColor = TEAL,
                        titleColor = TEAL,
                        bodyColor = INK,
                        titleSizeSp = 24,
                        bodySizeSp = 15
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
                                    sizeSp = 17
                                )
                            ),
                            strokeColor = TEAL
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
                                    sizeSp = 17
                                )
                            ),
                            strokeColor = BLUE
                        ),
                        StatsCardModel(
                            title = "Needs attention",
                            summary = "4 kanji with enough evidence",
                            body = "These entries still need more Anki evidence.",
                            lines = listOf(
                                StatsLineModel(
                                    text = "説: 1 review, 0 same-card, -2.0 retention, +0.4 difficulty",
                                    color = INK,
                                    bold = true,
                                    sizeSp = 16
                                ),
                                StatsLineModel(
                                    text = "2 kanji still need more Anki evidence.",
                                    color = MUTED,
                                    bold = false,
                                    sizeSp = 15
                                )
                            ),
                            strokeColor = CORAL
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
                                    sizeSp = 16
                                ),
                                StatsLineModel(
                                    text = "Review: 3",
                                    color = INK,
                                    bold = false,
                                    sizeSp = 16
                                )
                            ),
                            strokeColor = GOLD
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
                                    sizeSp = 16
                                ),
                                StatsLineModel(
                                    text = "Avg / task: 1m 23s",
                                    color = MUTED,
                                    bold = false,
                                    sizeSp = 16
                                )
                            ),
                            strokeColor = CORAL,
                            titleColor = MUTED,
                            summaryColor = INK,
                            bodyColor = MUTED,
                            titleSizeSp = 18,
                            summarySizeSp = 24,
                            bodySizeSp = 16
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithText("Stats").assertIsDisplayed()
        composeRule.onNodeWithText("Kani repairs weak kanji from Anki reviews and shows whether the evidence improves after sync.").assertIsDisplayed()
        composeRule.onNodeWithText("Weak kanji trend").assertIsDisplayed()
        composeRule.onNodeWithText("Anki support").assertIsDisplayed()
        composeRule.onNodeWithText("Needs attention").assertIsDisplayed()
        composeRule.onNodeWithText("Ladder status").assertIsDisplayed()
        composeRule.onNodeWithText("Study time").assertIsDisplayed()
    }

    @Test
    fun rendersCompactStatsScreenWithinPhoneWidth() {
        composeRule.setContent {
            Box(modifier = Modifier.width(360.dp)) {
                StatsScreen(model = compactStatsScreenModel())
            }
        }

        composeRule.onNodeWithText("Stats").assertIsDisplayed()
        composeRule.onNodeWithText("Weak kanji trend").assertIsDisplayed()
        composeRule.onNodeWithText("Average weakness fell from 2.1 to 1.4.").assertIsDisplayed()
        composeRule.onNodeWithText("裂: 88 -> 33").assertIsDisplayed()
        composeRule.onNodeWithText("Ladder status").assertIsDisplayed()
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
                        emptyState = true
                    ),
                    sections = emptyList()
                )
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
            bodySizeSp = 15
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
                        sizeSp = 17
                    )
                ),
                strokeColor = TEAL
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
                        sizeSp = 16
                    ),
                    StatsLineModel(
                        text = "Review: 3",
                        color = INK,
                        bold = false,
                        sizeSp = 16
                    )
                ),
                strokeColor = GOLD
            )
        )
    )
}
