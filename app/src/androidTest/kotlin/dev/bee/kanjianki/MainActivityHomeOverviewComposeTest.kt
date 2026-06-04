package dev.bee.kanjianki

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.HomeTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityHomeOverviewComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersAppTitleAndSubtitle() {
        composeRule.setContent {
            HomeHeader(
                title = HomeTextCopy.appTitle(),
                subtitle = HomeTextCopy.appSubtitle()
            )
        }

        composeRule.onNodeWithText(HomeTextCopy.appTitle()).assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.appSubtitle()).assertIsDisplayed()
    }

    @Test
    fun rendersMetricRowAndInvokesClickableMetric() {
        var clicked = false

        composeRule.setContent {
            HomeMetricRow(
                metrics = listOf(
                    HomeMetricModel(
                        iconRes = R.drawable.ic_sync_24,
                        accent = MainActivityBase.TEAL,
                        label = "Sync",
                        value = "Ready",
                        body = "Connected",
                        onClick = { clicked = true }
                    ),
                    HomeMetricModel(
                        iconRes = R.drawable.ic_flame_24,
                        accent = MainActivityBase.CORAL,
                        label = "Streak",
                        value = "3 days",
                        body = null,
                        onClick = null
                    ),
                    HomeMetricModel(
                        iconRes = R.drawable.ic_target_24,
                        accent = MainActivityBase.BLUE,
                        label = "Focus",
                        value = "2 left",
                        body = null,
                        onClick = null
                    )
                )
            )
        }

        composeRule.onNodeWithText("Sync").assertIsDisplayed()
        composeRule.onNodeWithText("Ready").assertIsDisplayed()
        composeRule.onNodeWithText("Streak").assertIsDisplayed()
        composeRule.onNodeWithText("Focus").assertIsDisplayed()
        composeRule.onNodeWithTag(homeMetricCardTestTag("Sync"))
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule.onNodeWithTag(homeMetricCardTestTag("Streak"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeRule.onNodeWithTag(homeMetricCardTestTag("Focus"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))

        assertTrue(clicked)
        assertMetricCardHeightsEqual()
    }

    @Test
    fun rendersPrimaryCtaAndInvokesClick() {
        var clicked = false

        composeRule.setContent {
            HomePrimaryCta(
                label = "Sync AnkiDroid",
                color = MainActivityBase.CORAL,
                onClick = { clicked = true }
            )
        }

        composeRule.onNodeWithText("Sync AnkiDroid").assertIsDisplayed()
        composeRule.onNodeWithTag(homePrimaryCtaTestTag("Sync AnkiDroid"))
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("Sync AnkiDroid")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertTrue(clicked)
    }

    @Test
    fun rendersStudyCtaAndInvokesClick() {
        var clicked = false

        composeRule.setContent {
            HomeStudyCta(
                title = MainActivityBase.LABEL_STUDY_NOW,
                subtitle = HomeTextCopy.studySupportText(),
                onClick = { clicked = true }
            )
        }

        composeRule.onNodeWithText(MainActivityBase.LABEL_STUDY_NOW).assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.studySupportText()).assertIsDisplayed()
        composeRule.onNodeWithTag(homeStudyCtaTestTag(MainActivityBase.LABEL_STUDY_NOW))
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription(MainActivityBase.LABEL_STUDY_NOW)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertTrue(clicked)
    }

    private fun assertMetricCardHeightsEqual() {
        val syncHeight = composeRule.onNodeWithTag(homeMetricCardTestTag("Sync"))
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val streakHeight = composeRule.onNodeWithTag(homeMetricCardTestTag("Streak"))
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val focusHeight = composeRule.onNodeWithTag(homeMetricCardTestTag("Focus"))
            .fetchSemanticsNode()
            .boundsInRoot
            .height

        assertEquals(syncHeight, streakHeight, METRIC_HEIGHT_TOLERANCE_PX)
        assertEquals(syncHeight, focusHeight, METRIC_HEIGHT_TOLERANCE_PX)
    }

    private companion object {
        private const val METRIC_HEIGHT_TOLERANCE_PX = 1.0f
    }
}
