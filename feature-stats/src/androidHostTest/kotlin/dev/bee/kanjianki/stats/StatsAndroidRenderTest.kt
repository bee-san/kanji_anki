package dev.bee.kanjianki.stats

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the shared Stats dashboard render assertions on the Android host target. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsAndroidRenderTest {
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

    @Test
    fun everyChartCarriesATextAlternative() {
        assertEveryChartCarriesATextAlternative()
    }

    @Test
    fun aChartWithNoSummaryFallsBackToItsSectionTitle() {
        assertAChartWithNoSummaryFallsBackToItsSectionTitle()
    }

    @Test
    fun aBlankSummaryChangesNothingThatIsVisible() {
        assertABlankSummaryChangesNothingThatIsVisible()
    }

    @Test
    fun everySectionTitleIsRealEnoughToBorrow() {
        assertEverySectionTitleIsRealEnoughToBorrow()
    }
}
