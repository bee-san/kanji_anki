package dev.bee.kanjianki.stats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.StatsRange
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The stats dashboard's render assertions, run on both hosts.
 *
 * Structure and reachability of the six sections and their charts, plus the one
 * interaction (opening a most-missed kanji). The analytics numbers themselves are the
 * host mapping's and are tested there; here we prove the shared surface lays out every
 * section and dispatches the one action it owns.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertEverySectionRendersWhenTheDashboardIsFull() {
    renderStats(
        content = { StatsDashboardScreen(sampleDashboard(), dispatch = {}) },
    ) {
        for (tag in listOf(
            STATS_FORECAST_TEST_TAG,
            STATS_OVERVIEW_TEST_TAG,
            STATS_REVIEWS_TEST_TAG,
            STATS_ACCURACY_TEST_TAG,
            STATS_LEVEL_TEST_TAG,
            STATS_WEAKNESS_TEST_TAG,
        )) {
            onNodeWithTag(tag).assertExists()
        }
        // Every chart kind draws. Line and donut recur across sections, so assert at
        // least one of each rather than exactly one.
        assertTrue(onAllNodesWithTag(STATS_LINE_CHART_TEST_TAG).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithTag(STATS_DONUT_CHART_TEST_TAG).fetchSemanticsNodes().isNotEmpty())
        onNodeWithTag(STATS_BAR_CHART_TEST_TAG).assertExists()
        onNodeWithTag(STATS_HEATMAP_TEST_TAG).assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertOpeningAMostMissedKanjiGoesToItsDetail() {
    val recorded = mutableListOf<KaniAction>()
    renderStats(
        content = { StatsDashboardScreen(sampleDashboard(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(statsMissedKanjiTestTag("脱")).performScrollTo().performClick()
        assertEquals(
            listOf<KaniAction>(
                KaniAction.Navigation.Open(KaniDestination.Detail(kanji = "脱", fromBrowse = true)),
            ),
            recorded,
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheForecastAndHeatmapAreAbsentRatherThanEmptyWhenTheModelOmitsThem() {
    // A collection too new for a forecast, or a range with no heatmap, drops those
    // pieces rather than rendering a blank chart.
    val bare = sampleDashboard().copy(
        forecast = null,
        reviews = sampleDashboard().reviews.copy(heatmap = null),
    )
    renderStats(
        content = { StatsDashboardScreen(bare, dispatch = {}) },
    ) {
        onNodeWithTag(STATS_DASHBOARD_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(STATS_FORECAST_TEST_TAG).assertDoesNotExist()
        onNodeWithTag(STATS_HEATMAP_TEST_TAG).assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAWeaknessSectionWithNoFocusScoreStillRenders() {
    val noFocus = sampleDashboard().copy(
        weakness = sampleDashboard().weakness.copy(focusScoreAvailable = false),
    )
    renderStats(
        content = { StatsDashboardScreen(noFocus, dispatch = {}) },
    ) {
        onNodeWithTag(STATS_WEAKNESS_TEST_TAG).assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheShippedStatsResourcesResolveOnThisHost() {
    // Exercises this module's own resources: a range label that resolved under Skiko
    // but not through Android's asset loader would fail here.
    var labels = emptyList<String>()
    renderStats(
        content = {
            val copy = rememberStatsCopy()
            labels = StatsRange.entries.map(copy::rangeLabel) + copy.emptyTitle + copy.emptyBody
        },
    ) {
        assertTrue(labels.all { it.isNotBlank() }, "every shipped stats string resolves: $labels")
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheStatsTestTagsAreDistinct() {
    val tags = listOf(
        STATS_DASHBOARD_TEST_TAG,
        STATS_FORECAST_TEST_TAG,
        STATS_OVERVIEW_TEST_TAG,
        STATS_REVIEWS_TEST_TAG,
        STATS_ACCURACY_TEST_TAG,
        STATS_LEVEL_TEST_TAG,
        STATS_WEAKNESS_TEST_TAG,
        STATS_LINE_CHART_TEST_TAG,
        STATS_BAR_CHART_TEST_TAG,
        STATS_DONUT_CHART_TEST_TAG,
        STATS_HEATMAP_TEST_TAG,
    ) + listOf("脱", "説").map(::statsMissedKanjiTestTag)
    assertEquals(tags.size, tags.distinct().size, "tags must be unique: $tags")
    assertEquals("kani-stats-missed-脱", statsMissedKanjiTestTag("脱"))
}
