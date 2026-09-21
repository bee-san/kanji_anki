package dev.bee.kanjianki.home

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import dev.bee.kanjianki.presentation.BrowseResults
import dev.bee.kanjianki.presentation.BrowseRow
import dev.bee.kanjianki.presentation.FocusCard
import dev.bee.kanjianki.presentation.FocusQueue
import dev.bee.kanjianki.presentation.HomeDashboard
import dev.bee.kanjianki.presentation.HomeRecommendation
import dev.bee.kanjianki.presentation.TodayPlan
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.ui.KaniUiTokens
import kotlin.test.assertTrue

/**
 * That every Browse control is large enough to hit.
 *
 * Browse is the screen with the most secondary controls in one place — two filter
 * toggles, two bulk-selection buttons, and a studied checkbox on every row — and all
 * five kinds take a Material default under [KaniUiTokens.MinTouchTarget] unless told
 * otherwise. The row checkbox is the one that matters most: it is per-row, so a user
 * marking a list of kanji hits it repeatedly, and it sits next to a row that opens a
 * whole detail screen, so a near-miss is not a no-op but a navigation.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertEveryBrowseControlIsBigEnoughToHit() {
    val results = BrowseResults.of(
        listOf(
            BrowseRow(kanji = "脱", meaning = UiText.Literal("take off")),
            BrowseRow(kanji = "橋", meaning = UiText.Literal("bridge")),
        ),
    )
    renderHome(
        content = { BrowseScreen(results, browseCopy(), TestUiTextResolver, dispatch = {}) },
    ) {
        val tags = listOf(
            BROWSE_SEARCH_TEST_TAG,
            BROWSE_SIMILAR_FILTER_TEST_TAG,
            BROWSE_SHOW_SUSPENDED_TEST_TAG,
            BROWSE_SELECT_ALL_TEST_TAG,
            BROWSE_DESELECT_ALL_TEST_TAG,
            browseStudiedTestTag("脱"),
            browseStudiedTestTag("橋"),
        )
        for (tag in tags) {
            assertTargetIsBigEnough(tag)
        }
    }
}

/**
 * That the dashboard's own controls are large enough to hit.
 *
 * These are three separate composables the host stacks onto one screen, so they are
 * rendered separately here rather than assembled: the sizes are each surface's own
 * property, and a check that depended on the host's arrangement would stop covering
 * them the moment the arrangement changed.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertEveryDashboardControlIsBigEnoughToHit() {
    val copy = dashboardCopy()
    renderHome(
        content = {
            HomePrimaryAction(
                home = HomeDashboard(focus = FocusQueue(hasImportedKanji = true), studyRemainingCount = 12),
                copy = copy,
                dispatch = {},
            )
        },
    ) {
        assertTargetIsBigEnough(HOME_PRIMARY_TEST_TAG)
    }
    renderHome(
        content = {
            HomeTodayCard(
                plan = TodayPlan(
                    recommendation = HomeRecommendation.STUDY_NOW,
                    summary = UiText.Literal("18 cards are due"),
                ),
                copy = copy,
                resolver = TestUiTextResolver,
                dispatch = {},
            )
        },
    ) {
        assertTargetIsBigEnough(HOME_TODAY_ACTION_TEST_TAG)
    }
    renderHome(
        content = {
            FocusQueuePanel(
                queue = FocusQueue(cards = listOf(FocusCard(kanji = "脱", meaning = UiText.Literal("take off")))),
                copy = copy,
                resolver = TestUiTextResolver,
                dispatch = {},
            )
        },
    ) {
        assertTargetIsBigEnough(FOCUS_QUEUE_VIEW_ALL_TEST_TAG)
    }
}

/**
 * That the control offered while a sync runs is large enough to hit.
 *
 * Stopping a sync is the one control on this screen with a deadline: it is useful only
 * for as long as the sync is still going, and it is reached by someone who has already
 * decided they want it to stop. It also renders on a state no other Home assertion
 * visits, which is how a control gets missed by a screenful-at-a-time sweep.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertTheSyncCancelIsBigEnoughToHit() {
    renderHome(
        content = { SyncProgressCard(copy = homeCopy(), dispatch = {}) },
    ) {
        assertTargetIsBigEnough(SYNC_CANCEL_TEST_TAG)
    }
}

/**
 * Asserts the node at [tag] is at least [KaniUiTokens.MinTouchTarget] tall.
 *
 * Measured from the laid-out node rather than read off the modifier chain: a `heightIn`
 * that is present but sits inside a `Row` whose own height is fixed produces no taller
 * node, and source-reading would not notice. The 1dp slack absorbs the density division
 * the fixed-window harness performs, so a logical 44dp measuring 43.999 does not fail.
 *
 * No scroll-into-view first: the bounds of a laid-out node are the same whether or not it
 * is on screen, and requiring a scrollable ancestor would make the check depend on how
 * each module's harness wraps its content rather than on the control's own size.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertTargetIsBigEnough(tag: String) {
    val nodes = onAllNodesWithTag(tag).fetchSemanticsNodes()
    assertTrue(nodes.isNotEmpty(), "$tag did not render, so its size is untested")
    val bounds = onAllNodesWithTag(tag)[0].getBoundsInRoot()
    val height = bounds.bottom - bounds.top
    assertTrue(
        height.value + TARGET_SLACK >= KaniUiTokens.MinTouchTarget.value,
        "$tag is $height tall, under the ${KaniUiTokens.MinTouchTarget} target",
    )
}

private const val TARGET_SLACK: Float = 1f
