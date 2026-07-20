package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.bee.kanjianki.core.HomeTextCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Offline UX contract for the home update-check-failed banner (task
 * t_2e024478). The [HomeScreenModel] already carries [HomeScreenModel.updateCheckFailedLine]
 * and [HomeScreenModel.onRetryUpdateCheck], but nothing rendered them, so an
 * offline/captive-portal update-check failure was silently swallowed and the
 * user had no truthful message and no retry affordance. These tests pin that the
 * banner is shown with an accessible retry action, that retry fires the
 * callback, and that a healthy model (null line) renders nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeUpdateCheckFailedBannerComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setHomeContent(model: HomeScreenModel) {
        composeRule.setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                HomeScreen(model)
            }
        }
    }

    @Test
    fun offlineUpdateCheckFailureShowsTruthfulLineAndAccessibleRetry() {
        var retried = false
        setHomeContent(
            baseModel(
                updateCheckFailedLine = HomeTextCopy.updateCheckFailedLine(),
                onRetryUpdateCheck = { retried = true },
            ),
        )

        composeRule.onNodeWithTag(homeUpdateCheckFailedBannerTestTag())
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.updateCheckFailedLine())
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText(HomeTextCopy.retryLabel())
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertTrue(retried)
    }

    @Test
    fun healthyModelRendersNoUpdateCheckFailedBanner() {
        setHomeContent(baseModel(updateCheckFailedLine = null, onRetryUpdateCheck = null))

        composeRule.onAllNodesWithText(HomeTextCopy.updateCheckFailedLine()).assertCountEquals(0)
        composeRule.onAllNodesWithText(HomeTextCopy.retryLabel()).assertCountEquals(0)
    }

    @Test
    fun bannerWithoutRetryCallbackStillShowsTruthfulLineWithoutRetryAction() {
        // Defensive: if the model somehow carries a failed line but no callback,
        // we still tell the user the truth and never offer a dead retry button.
        setHomeContent(
            baseModel(
                updateCheckFailedLine = HomeTextCopy.updateCheckFailedLine(),
                onRetryUpdateCheck = null,
            ),
        )

        composeRule.onNodeWithText(HomeTextCopy.updateCheckFailedLine())
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(HomeTextCopy.retryLabel()).assertCountEquals(0)
    }

    private fun baseModel(
        updateCheckFailedLine: String?,
        onRetryUpdateCheck: (() -> Unit)?,
    ): HomeScreenModel {
        return HomeScreenModel(
            title = "Kani",
            subtitle = "Repair weak kanji from AnkiDroid.",
            metrics = listOf(
                HomeMetricModel(R.drawable.ic_sync_24, MainActivityUiSupport.TEAL, "Sync", "Today", "Ready", null),
                HomeMetricModel(R.drawable.ic_flame_24, MainActivityUiSupport.GOLD, "Streak", "2 days", "Done today", null),
                HomeMetricModel(R.drawable.ic_target_24, MainActivityUiSupport.CORAL, "Focus", "1 left", null, null),
            ),
            todayPlan = HomeTodayPlanModel(
                title = HomeTextCopy.todayPlanTitle(),
                summary = "Nothing useful now",
                details = emptyList(),
            ),
            deckOverviewRows = emptyList(),
            showSyncCta = false,
            syncLabel = "Sync AnkiDroid",
            studyLabel = "Study now",
            onSync = {},
            onStudy = {},
            actions = listOf(HomeActionModel("Stats", R.drawable.ic_stats_24, onClick = {})),
            focusTitle = "Focus queue",
            focusActionLabel = null,
            onFocusAction = null,
            emptyTitle = HomeTextCopy.noKanjiQueuedTitle(),
            emptyBody = HomeTextCopy.homeNoKanjiQueuedBody(),
            previewCards = emptyList(),
            updateCheckFailedLine = updateCheckFailedLine,
            onRetryUpdateCheck = onRetryUpdateCheck,
        )
    }
}
