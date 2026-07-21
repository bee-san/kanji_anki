package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.bee.kanjianki.core.HomeTextCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    // The production shell hosts every route inside a vertical scroll container
    // (MainActivityScrollableRouteColumn); mirror that here so below-the-fold home
    // sections can be scrolled to on small test devices.
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
    fun rendersEmptyHomeWithSyncCta() {
        var syncClicked = false

        setHomeContent(
            baseModel(
                showSyncCta = true,
                onSync = { syncClicked = true },
                syncMetricBody = "AnkiDroid",
                emptyTitle = "No kanji queued",
                emptyBody = HomeTextCopy.homeNoKanjiQueuedBody(),
                firstRunOfflineNotice = HomeTextCopy.firstRunOfflineNotice(),
            )
        )

        composeRule.onNodeWithText("Kani").assertIsDisplayed()
        composeRule.onNodeWithTag(homeFirstRunOfflineNoticeTestTag()).assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.firstRunOfflineNotice()).assertIsDisplayed()
        composeRule.onNodeWithText("Focus queue").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("View all").assertCountEquals(0)
        composeRule.onNodeWithText("No kanji queued").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.homeNoKanjiQueuedBody()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(homePrimaryCtaTestTag("Sync AnkiDroid"))
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("Sync AnkiDroid")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule.onNodeWithText("Sync").performScrollTo().assertHasClickAction().performClick()
        composeRule.onNodeWithText("AnkiDroid").performScrollTo().assertHasClickAction().performClick()
        assertTrue(syncClicked)
    }

    @Test
    fun rendersTodayPlanCardAndTriggersAction() {
        var clicked = false

        setHomeContent(
            baseModel(
                showSyncCta = false,
                deckOverviewRows = listOf("Due 2"),
                todayPlan = HomeTodayPlanModel(
                    title = HomeTextCopy.todayPlanTitle(),
                    summary = "4 due now · about 2 min",
                    details = listOf("Needs one calm review", "Keeps the streak safe"),
                    actionLabel = HomeTextCopy.studyNowLabel(),
                    onClick = { clicked = true },
                ),
            )
        )

        composeRule.onNodeWithTag(homeTodayPlanTestTag())
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText("4 due now · about 2 min").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Needs one calm review").performScrollTo().assertIsDisplayed()
        assertTrue(clicked)
    }

    @Test
    fun studyCtaShowsSessionRemainingCount() {
        setHomeContent(
            baseModel(
                showSyncCta = false,
                studyRemainingCount = 5,
            )
        )

        // The Study-now pill shows the focus-session remaining count, not the raw
        // active-card count (the "19 Due while I study 5" bug).
        composeRule.onNodeWithText(HomeTextCopy.studyRemainingCountLabel(5))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Study now, ${HomeTextCopy.studyRemainingCountLabel(5)}")
            .assertHasClickAction()
    }

    @Test
    fun rendersStudyHomeWithPreviewQueueActions() {
        var studyClicked = false
        var focusClicked = false
        var cardClicked = false
        var actionClicked = false
        var metricSyncClicked = false

        setHomeContent(
            baseModel(
                showSyncCta = false,
                onStudy = { studyClicked = true },
                onSync = { metricSyncClicked = true },
                deckOverviewRows = listOf("Due 2", "New 1"),
                focusActionLabel = "View all >",
                onFocusAction = { focusClicked = true },
                actions = listOf(HomeActionModel("Stats", R.drawable.ic_stats_24, onClick = { actionClicked = true })),
                previewCards = listOf(
                    HomeFocusQueueCardModel(
                        kanji = "裂",
                        meaning = "split",
                        sourceEvidence = "From 裂語",
                        reasonLine = "due now",
                        body = "Needs kanji practice.",
                        tags = listOf(HomeFocusQueueTagModel("kanji -> meaning", MainActivityUiSupport.BLUE)),
                        accentColor = MainActivityUiSupport.CORAL,
                        onClick = { cardClicked = true }
                    )
                )
            )
        )

        composeRule.onNodeWithContentDescription("Study now")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule.onNodeWithTag(homeStudyCtaTestTag("Study now"))
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag(homeActionButtonTestTag("Stats"))
            .performScrollTo()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule.onNodeWithText("Focus queue").performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()
        composeRule.onNodeWithText("View all >").performScrollTo().assertHasClickAction().performClick()
        composeRule.onNodeWithText("Sync").performScrollTo().assertHasClickAction().performClick()
        composeRule.onNodeWithText("Deck overview").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Due 2").assertIsDisplayed()
        composeRule.onNodeWithText("New 1").assertIsDisplayed()
        composeRule.onNodeWithTag(homeFocusQueueCardTestTag("裂")).performScrollTo().performClick()
        assertTrue(studyClicked)
        assertTrue(actionClicked)
        assertTrue(focusClicked)
        assertTrue(metricSyncClicked)
        assertTrue(cardClicked)
    }

    @Test
    fun rendersOfflineUpdateCheckFailedBannerWithRetry() {
        var retried = false

        setHomeContent(
            baseModel(
                showSyncCta = false,
                updateCheckFailedLine = HomeTextCopy.updateCheckFailedLine(),
                onRetryUpdateCheck = { retried = true },
            )
        )

        composeRule.onNodeWithTag(homeUpdateCheckFailedBannerTestTag())
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.updateCheckFailedLine())
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.retryLabel())
            .performScrollTo()
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertTrue(retried)
    }

    @Test
    fun healthyHomeHidesOfflineUpdateCheckFailedBanner() {
        setHomeContent(baseModel(showSyncCta = false))

        composeRule.onAllNodesWithText(HomeTextCopy.updateCheckFailedLine()).assertCountEquals(0)
        composeRule.onAllNodesWithTag(homeFirstRunOfflineNoticeTestTag()).assertCountEquals(0)
    }

    private fun baseModel(
        showSyncCta: Boolean,
        onSync: () -> Unit = {},
        onStudy: () -> Unit = {},
        deckOverviewRows: List<String> = emptyList(),
        focusActionLabel: String? = null,
        onFocusAction: (() -> Unit)? = null,
        emptyTitle: String? = null,
        emptyBody: String? = null,
        actions: List<HomeActionModel> = listOf(HomeActionModel("Stats", R.drawable.ic_stats_24, onClick = {})),
        previewCards: List<HomeFocusQueueCardModel> = emptyList(),
        syncMetricBody: String = "Ready",
        studyRemainingCount: Int = 0,
        updateCheckFailedLine: String? = null,
        onRetryUpdateCheck: (() -> Unit)? = null,
        firstRunOfflineNotice: String? = null,
        todayPlan: HomeTodayPlanModel = HomeTodayPlanModel(
            title = HomeTextCopy.todayPlanTitle(),
            summary = "Nothing useful now",
            details = emptyList(),
        ),
    ): HomeScreenModel {
        return HomeScreenModel(
            title = "Kani",
            subtitle = "Repair weak kanji from AnkiDroid.",
            metrics = listOf(
                HomeMetricModel(R.drawable.ic_sync_24, MainActivityUiSupport.TEAL, "Sync", "Today", syncMetricBody, onSync),
                HomeMetricModel(R.drawable.ic_flame_24, MainActivityUiSupport.GOLD, "Streak", "2 days", "Done today", null),
                HomeMetricModel(R.drawable.ic_target_24, MainActivityUiSupport.CORAL, "Focus", "1 left", null, null)
            ),
            todayPlan = todayPlan,
            deckOverviewRows = deckOverviewRows,
            showSyncCta = showSyncCta,
            syncLabel = "Sync AnkiDroid",
            studyLabel = "Study now",
            onSync = onSync,
            onStudy = onStudy,
            actions = actions,
            focusTitle = "Focus queue",
            focusActionLabel = focusActionLabel,
            onFocusAction = onFocusAction,
            emptyTitle = emptyTitle,
            emptyBody = emptyBody,
            previewCards = previewCards,
            studyRemainingCount = studyRemainingCount,
            updateCheckFailedLine = updateCheckFailedLine,
            onRetryUpdateCheck = onRetryUpdateCheck,
            firstRunOfflineNotice = firstRunOfflineNotice,
        )
    }
}
