package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeUiTraceComposeUnitTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun withUiTraceRunsBlankSectionsWithoutStartingATrace() {
        var calls = 0

        withUiTrace("") {
            calls++
        }

        assertEquals(1, calls)
    }

    @Test
    fun homePrimaryCtaInvokesItsCallback() {
        var clicked = false

        composeRule.setContent {
            HomePrimaryCta(
                label = "Sync AnkiDroid",
                color = MainActivityBase.CORAL,
                onClick = { clicked = true },
            )
        }

        composeRule.onNodeWithText("Sync AnkiDroid").assertIsDisplayed()
        composeRule.onNodeWithTag(homePrimaryCtaTestTag("Sync AnkiDroid"))
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }

    @Test
    fun homeStudyCtaInvokesItsCallback() {
        var clicked = false

        composeRule.setContent {
            HomeStudyCta(
                title = MainActivityBase.LABEL_STUDY_NOW,
                onClick = { clicked = true },
            )
        }

        composeRule.onNodeWithText(MainActivityBase.LABEL_STUDY_NOW).assertIsDisplayed()
        composeRule.onNodeWithTag(homeStudyCtaTestTag(MainActivityBase.LABEL_STUDY_NOW))
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }

    @Test
    fun homeActionButtonsAndSectionHeaderInvokeTheirCallbacks() {
        val clicked = mutableListOf<String>()

        composeRule.setContent {
            Column {
                HomeActionGrid(
                    actions = listOf(
                        HomeActionModel("Browse", R.drawable.ic_book_24) { clicked += "Browse" },
                        HomeActionModel("Stats", R.drawable.ic_stats_24) { clicked += "Stats" },
                        HomeActionModel("Settings", R.drawable.ic_settings_24) { clicked += "Settings" },
                    )
                )
                HomeSectionHeader(
                    title = "Focus queue",
                    actionLabel = "View all",
                    onAction = { clicked += "header" },
                )
            }
        }

        listOf("Browse", "Stats", "Settings").forEach { label ->
            composeRule.onNodeWithTag(homeActionButtonTestTag(label))
                .assertHasClickAction()
                .performClick()
        }
        composeRule.onNodeWithText("Focus queue")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithTag(homeSectionActionButtonTestTag("View all"))
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf("Browse", "Stats", "Settings", "header", "header"),
                clicked,
            )
        }
    }
}
