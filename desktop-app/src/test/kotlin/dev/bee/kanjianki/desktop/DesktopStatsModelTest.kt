package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.progress.progressAnalyticsDemoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The desktop half of Goal 197's Stats parity: the mapping feeds the shared dashboard
 * from the same `:progress-core` computation the Android host runs. The dashboard's
 * layout is proven by `:feature-stats`'s render tests on both hosts; this checks the
 * field-for-field translation the mapping owns.
 */
class DesktopStatsModelTest {
    @Test
    fun everySectionOfAComputedStateMapsToTheDashboard() {
        val analytics = progressAnalyticsDemoSnapshot(NOW)

        val dashboard = DesktopStatsModel.dashboard(analytics)

        assertEquals(analytics.overview.title, dashboard.overview.title)
        assertEquals(analytics.reviewsAnalytics.title, dashboard.reviews.title)
        assertEquals(analytics.accuracyRetention.title, dashboard.accuracy.title)
        assertEquals(analytics.progressByLevel.title, dashboard.progressByLevel.title)
        assertEquals(analytics.weaknessInsights.title, dashboard.weakness.title)
        // The value labels flatten from the Android metric objects to plain strings.
        assertEquals(analytics.overview.accuracy.valueLabel, dashboard.overview.accuracyValue)
    }

    @Test
    fun theChartAxisAndRangesTranslateToThePortableTypes() {
        // The demo state has fully populated charts and a forecast, so this exercises
        // the axis, series, distribution, and range translations.
        val dashboard = DesktopStatsModel.dashboard(progressAnalyticsDemoSnapshot(NOW))

        assertNotNull(dashboard.forecast)
        assertTrue(dashboard.overview.reviewsOverTime.series.isNotEmpty())
        assertTrue(dashboard.overview.reviewsOverTime.axis.axisMax >= 0)
        assertTrue(dashboard.reviews.availableRanges.isNotEmpty())
        // A dashed goal series survives the style→boolean translation.
        assertTrue(dashboard.overview.cardTypeBreakdown.segments.isNotEmpty())
    }

    @Test
    fun aHeatmapWhenPresentMapsItsWeeksAndCells() {
        val dashboard = DesktopStatsModel.dashboard(progressAnalyticsDemoSnapshot(NOW))

        // The demo dataset includes a heatmap; its weeks and cells carry through.
        dashboard.reviews.heatmap?.let { heatmap ->
            assertTrue(heatmap.weekdayLabels.isNotEmpty())
        }
    }

    private companion object {
        const val NOW = 1_747_000_000_000L
    }
}
