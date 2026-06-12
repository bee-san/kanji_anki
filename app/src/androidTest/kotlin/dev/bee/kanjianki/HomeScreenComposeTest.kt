package dev.bee.kanjianki

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.HomeTextCopy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersEmptyHomeWithSyncCta() {
        var syncClicked = false

        composeRule.setContent {
            HomeScreen(
                model = baseModel(
                    showSyncCta = true,
                    onSync = { syncClicked = true },
                    syncMetricBody = "AnkiDroid",
                    emptyTitle = "No kanji queued",
                    emptyBody = HomeTextCopy.homeNoKanjiQueuedBody()
                )
            )
        }

        composeRule.onNodeWithText("Kani").assertIsDisplayed()
        composeRule.onNodeWithText("Sync AnkiDroid").assertIsDisplayed()
        composeRule.onNodeWithText("Focus queue").assertIsDisplayed()
        composeRule.onAllNodesWithText("View all").assertCountEquals(0)
        composeRule.onNodeWithText("No kanji queued").assertIsDisplayed()
        composeRule.onNodeWithText(HomeTextCopy.homeNoKanjiQueuedBody()).assertIsDisplayed()
        composeRule.onNodeWithTag(homePrimaryCtaTestTag("Sync with AnkiDroid"))
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("Sync with AnkiDroid")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule.onNodeWithText("Sync").assertHasClickAction().performClick()
        composeRule.onNodeWithText("AnkiDroid").assertHasClickAction().performClick()
        assertTrue(syncClicked)
    }

    @Test
    fun rendersTodayPlanCardAndTriggersAction() {
        var clicked = false

        composeRule.setContent {
            HomeScreen(
                model = baseModel(
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
        }

        composeRule.onNodeWithTag(homeTodayPlanTestTag())
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText("4 due now · about 2 min").assertIsDisplayed()
        composeRule.onNodeWithText("Needs one calm review").assertIsDisplayed()
        assertTrue(clicked)
    }

    @Test
    fun rendersStudyHomeWithPreviewQueueActions() {
        var studyClicked = false
        var focusClicked = false
        var cardClicked = false
        var actionClicked = false
        var metricSyncClicked = false

        composeRule.setContent {
            HomeScreen(
                model = baseModel(
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
                            tags = listOf(HomeFocusQueueTagModel("kanji -> meaning", Color(0xFF6E5CE6))),
                            accentColor = Color(0xFFFF4C76),
                            onClick = { cardClicked = true }
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithContentDescription("Study now")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule.onNodeWithTag(homeStudyCtaTestTag("Study now"))
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag(homeActionButtonTestTag("Stats"))
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule.onNodeWithText("Focus queue").assertIsDisplayed().assertHasClickAction().performClick()
        composeRule.onNodeWithText("View all >").assertHasClickAction().performClick()
        composeRule.onNodeWithText("Sync").assertHasClickAction().performClick()
        composeRule.onNodeWithContentDescription("Study").performClick()
        composeRule.onNodeWithText("Deck overview").assertIsDisplayed()
        composeRule.onNodeWithText("Due 2").assertIsDisplayed()
        composeRule.onNodeWithText("New 1").assertIsDisplayed()
        composeRule.onNodeWithTag(homeFocusQueueCardTestTag("裂")).performClick()
        assertTrue(studyClicked)
        assertTrue(actionClicked)
        assertTrue(focusClicked)
        assertTrue(metricSyncClicked)
        assertTrue(cardClicked)
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
            previewCards = previewCards
        )
    }
}
