package dev.bee.kanjianki

import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeRecentMistakesComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recentMistakesCardExposesButtonSemanticsAndContentDescription() {
        var clicked = false

        composeRule.setContent {
            HomeRecentMistakesPanel(
                HomeRecentMistakesPanelModel(
                    emptyTitle = "No recent mistakes yet",
                    emptyBody = "Missed and hard reviews appear here.",
                    cards = listOf(
                        HomeRecentMistakesCardModel(
                            kanji = "裂",
                            title = "split",
                            subtitle = "Rated again",
                            sourceEvidence = "From 裂語",
                            accentColor = ComposeColor(0xFFFF4C76),
                            onClick = { clicked = true },
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithTag(homeRecentMistakesCardTestTag("裂"))
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule.onNodeWithContentDescription("Recent mistakes card, 裂, split, Rated again, From 裂語")
            .assertIsDisplayed()

        assertTrue(clicked)
    }

    @Test
    fun recentMistakesCardContentDescriptionOmitsBlankEvidence() {
        composeRule.setContent {
            HomeRecentMistakesPanel(
                HomeRecentMistakesPanelModel(
                    emptyTitle = "No recent mistakes yet",
                    emptyBody = "Missed and hard reviews appear here.",
                    cards = listOf(
                        HomeRecentMistakesCardModel(
                            kanji = "語",
                            title = "language",
                            subtitle = "Hard",
                            sourceEvidence = "",
                            accentColor = ComposeColor(0xFFF0B548),
                            onClick = {},
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithContentDescription("Recent mistakes card, 語, language, Hard")
            .assertIsDisplayed()
    }
}
