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
                    emptyTitle = "No kanji queued",
                    emptyBody = "Sync AnkiDroid to find problem cards."
                )
            )
        }

        composeRule.onNodeWithText("Kani").assertIsDisplayed()
        composeRule.onNodeWithText("Sync AnkiDroid").assertIsDisplayed()
        composeRule.onNodeWithText("Focus queue").assertIsDisplayed()
        composeRule.onAllNodesWithText("View all").assertCountEquals(0)
        composeRule.onNodeWithText("No kanji queued").assertIsDisplayed()
        composeRule.onNodeWithTag(homePrimaryCtaTestTag("Sync AnkiDroid"))
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("Sync AnkiDroid")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertTrue(syncClicked)
    }

    @Test
    fun rendersStudyHomeWithPreviewQueueActions() {
        var studyClicked = false
        var focusClicked = false
        var cardClicked = false
        var actionClicked = false

        composeRule.setContent {
            HomeScreen(
                model = baseModel(
                    showSyncCta = false,
                    onStudy = { studyClicked = true },
                    deckOverviewRows = listOf("Due 2", "New 1"),
                    focusActionLabel = "View all",
                    onFocusAction = { focusClicked = true },
                    actions = listOf(HomeActionModel("Stats", R.drawable.ic_stats_24) { actionClicked = true }),
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
        composeRule.onNodeWithText("View all").performClick()
        composeRule.onNodeWithText("Deck overview").assertIsDisplayed()
        composeRule.onNodeWithText("Due 2").assertIsDisplayed()
        composeRule.onNodeWithText("New 1").assertIsDisplayed()
        composeRule.onNodeWithTag(homeFocusQueueCardTestTag("裂")).performClick()
        assertTrue(studyClicked)
        assertTrue(actionClicked)
        assertTrue(focusClicked)
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
        actions: List<HomeActionModel> = listOf(HomeActionModel("Stats", R.drawable.ic_stats_24) {}),
        previewCards: List<HomeFocusQueueCardModel> = emptyList(),
    ): HomeScreenModel {
        return HomeScreenModel(
            title = "Kani",
            subtitle = "Repair weak kanji from AnkiDroid.",
            metrics = listOf(
                HomeMetricModel(R.drawable.ic_sync_24, MainActivityUiSupport.TEAL, "Sync", "Today", "Ready", onSync),
                HomeMetricModel(R.drawable.ic_flame_24, MainActivityUiSupport.GOLD, "Streak", "2 days", "Done today", null),
                HomeMetricModel(R.drawable.ic_target_24, MainActivityUiSupport.CORAL, "Focus", "1 left", null, null)
            ),
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
