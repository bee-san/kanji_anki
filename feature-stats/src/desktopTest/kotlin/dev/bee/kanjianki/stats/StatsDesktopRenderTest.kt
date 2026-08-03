package dev.bee.kanjianki.stats

import kotlin.test.Test

/**
 * Runs the shared Stats dashboard render assertions on the desktop JVM. Its Android
 * twin runs the identical list under Robolectric — the cross-platform analytics
 * parity Goal 197 asks for.
 */
class StatsDesktopRenderTest {
    @Test
    fun everySectionRendersWhenTheDashboardIsFull() {
        assertEverySectionRendersWhenTheDashboardIsFull()
    }

    @Test
    fun openingAMostMissedKanjiGoesToItsDetail() {
        assertOpeningAMostMissedKanjiGoesToItsDetail()
    }

    @Test
    fun theForecastAndHeatmapAreAbsentRatherThanEmptyWhenTheModelOmitsThem() {
        assertTheForecastAndHeatmapAreAbsentRatherThanEmptyWhenTheModelOmitsThem()
    }

    @Test
    fun aWeaknessSectionWithNoFocusScoreStillRenders() {
        assertAWeaknessSectionWithNoFocusScoreStillRenders()
    }

    @Test
    fun theShippedStatsResourcesResolveOnThisHost() {
        assertTheShippedStatsResourcesResolveOnThisHost()
    }

    @Test
    fun theStatsTestTagsAreDistinct() {
        assertTheStatsTestTagsAreDistinct()
    }
}
