package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import dev.bee.kanjianki.core.HomeTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeChromeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersActionButtons() {
        val clicked = mutableListOf<String>()

        composeRule.setContent {
            HomeActionGrid(
                actions = listOf(
                    HomeActionModel("Browse", R.drawable.ic_book_24) { clicked += "Browse" },
                    HomeActionModel("Stats", R.drawable.ic_stats_24) { clicked += "Stats" },
                    HomeActionModel("Settings", R.drawable.ic_settings_24) { clicked += "Settings" },
                )
            )
        }

        listOf("Browse", "Stats", "Settings").forEach { label ->
            composeRule.onNodeWithTag(homeActionButtonTestTag(label))
                .assertIsDisplayed()
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performClick()
        }
        assertEquals(listOf("Browse", "Stats", "Settings"), clicked)
    }

    @Test
    fun rendersSectionHeaderAndInvokesAction() {
        var clicked = false

        composeRule.setContent {
            HomeSectionHeader(
                title = "Focus queue",
                actionLabel = "View all",
                onAction = { clicked = true }
            )
        }

        composeRule.onNodeWithText("Focus queue").assertIsDisplayed()
        composeRule.onNodeWithTag(homeSectionActionButtonTestTag("View all"))
            .assertIsDisplayed()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        composeRule.onNodeWithTag(homeSectionActionButtonTestTag("View all")).performClick()
        assertTrue(clicked)
    }

    @Test
    fun rendersHomeButtonAndInvokesAction() {
        var clicked = false

        composeRule.setContent {
            HomeFullWidthHomeButton(
                label = HomeTextCopy.homeLabel(),
                onClick = { clicked = true }
            )
        }

        composeRule.onNodeWithText(HomeTextCopy.homeLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(homeFullWidthHomeButtonTestTag(HomeTextCopy.homeLabel()))
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertTrue(clicked)
    }
}
