package dev.bee.kanjianki.stats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That every chart on the dashboard reaches a screen reader as words.
 *
 * A chart is a `Canvas`: assistive technology cannot read it and neither can any
 * assertion about what was drawn. The only text alternative it has is its
 * `contentDescription`, and the failure mode that matters is not a wrong description
 * but *no* description — a blank one renders identically to a correct one, passes every
 * structural assertion in `StatsRenderAssertions`, and reaches a blind user as an
 * unlabelled graphic with nothing to say what was lost.
 *
 * The descriptions are built as literals four modules away in `:progress-core` and
 * carried through `DesktopStatsModel` and every intermediate mapper untouched, so the
 * blank is not a hypothetical: any one of those hops returning "" would ship. These
 * assertions are the only place in the product where that would be caught, so they
 * assert the fallback fires and the sighted content is unaffected by it.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertEveryChartCarriesATextAlternative() {
    renderStats(
        content = { StatsDashboardScreen(sampleDashboard(), dispatch = {}) },
    ) {
        for (tag in CHART_TAGS) {
            val nodes = onAllNodesWithTag(tag).fetchSemanticsNodes()
            assertTrue(nodes.isNotEmpty(), "$tag did not render, so its description is untested")
            for (index in nodes.indices) {
                val description = onAllNodesWithTag(tag)[index].contentDescriptionOrEmpty()
                assertTrue(
                    description.isNotBlank(),
                    "$tag[$index] draws onto a canvas with no text alternative",
                )
            }
        }
    }
}

/**
 * That a chart whose own summary came through blank still announces its section.
 *
 * This is the whole point of the fallback, and it is asserted by blanking the summaries
 * the model carries rather than by trusting that they are never blank. The expected
 * value is the section title exactly, because a fallback that announced something
 * *other* than the panel the user is on would be worse than the missing sentence.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertAChartWithNoSummaryFallsBackToItsSectionTitle() {
    val blanked = sampleDashboard().let { full ->
        full.copy(
            forecast = full.forecast?.copy(burnDown = lineChart().copy(accessibilitySummary = "")),
            reviews = full.reviews.copy(
                reviewsPerDay = barChart().copy(accessibilitySummary = ""),
                heatmap = heatmap().copy(accessibilitySummary = ""),
            ),
            overview = full.overview.copy(
                reviewsOverTime = lineChart().copy(accessibilitySummary = ""),
                cardTypeBreakdown = distribution().copy(accessibilitySummary = ""),
                correctIncorrectBreakdown = distribution().copy(accessibilitySummary = ""),
            ),
        )
    }
    renderStats(
        content = { StatsDashboardScreen(blanked, dispatch = {}) },
    ) {
        // Four line charts render, one per section that has a trend, so this asserts
        // the set of descriptions rather than a single node: the two that were blanked
        // borrow their own section's title, and the two that were not keep their own
        // summary. Both halves matter — a fallback that fired unconditionally would
        // replace every real description with a section title and still be non-blank.
        val lineDescriptions = onAllNodesWithTag(STATS_LINE_CHART_TEST_TAG)
            .fetchSemanticsNodes()
            .indices
            .map { onAllNodesWithTag(STATS_LINE_CHART_TEST_TAG)[it].contentDescriptionOrEmpty() }
        assertTrue(
            blanked.forecast!!.headline in lineDescriptions,
            "the blanked forecast chart did not borrow its section title: $lineDescriptions",
        )
        assertTrue(
            blanked.overview.title in lineDescriptions,
            "the blanked overview chart did not borrow its section title: $lineDescriptions",
        )
        assertTrue(
            lineChart().accessibilitySummary in lineDescriptions,
            "a chart that has its own summary must keep it: $lineDescriptions",
        )
        assertEquals(
            blanked.reviews.title,
            onNodeWithTag(STATS_BAR_CHART_TEST_TAG).contentDescriptionOrEmpty(),
        )
        assertEquals(
            blanked.reviews.title,
            onNodeWithTag(STATS_HEATMAP_TEST_TAG).contentDescriptionOrEmpty(),
        )
        // And no chart anywhere is left unlabelled, which is the invariant the
        // per-node values above are only examples of.
        for (tag in CHART_TAGS) {
            val nodes = onAllNodesWithTag(tag).fetchSemanticsNodes()
            for (index in nodes.indices) {
                assertTrue(
                    onAllNodesWithTag(tag)[index].contentDescriptionOrEmpty().isNotBlank(),
                    "$tag[$index] is unlabelled even with the fallback in place",
                )
            }
        }
    }
}

/**
 * That the fallback is a description and not a redraw.
 *
 * A guard applied in the wrong place — wrapping the chart, or skipping it — would show
 * up as a chart that stopped rendering rather than as a missing sentence. So the same
 * blanked dashboard is asserted to still lay out every section and every chart kind,
 * which is the sighted half of the same change.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertABlankSummaryChangesNothingThatIsVisible() {
    val blanked = sampleDashboard().let { full ->
        full.copy(reviews = full.reviews.copy(reviewsPerDay = barChart().copy(accessibilitySummary = "")))
    }
    renderStats(
        content = { StatsDashboardScreen(blanked, dispatch = {}) },
    ) {
        onNodeWithTag(STATS_REVIEWS_TEST_TAG).assertExists()
        onNodeWithTag(STATS_BAR_CHART_TEST_TAG).assertExists()
        // The axis labels are the chart's own visible content and come from the same
        // model object whose summary was blanked, so their presence proves the chart
        // body was drawn and not merely that the tagged container survived.
        val text = onNodeWithTag(STATS_BAR_CHART_TEST_TAG).subtreeTextOrEmpty()
        for (label in barChart().labels) {
            assertTrue(label in text, "the bar chart lost its axis label $label: $text")
        }
    }
}

/**
 * That a section's own title is never the empty string it would lend a chart.
 *
 * The fallback is only worth having if the value behind it is real. A section rendered
 * from a model with a blank title would leave its charts unlabelled again, one layer
 * further down, and the guard in `StatsCharts` deliberately declines to invent text —
 * so the honest place to check that is here, against the shipped model.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertEverySectionTitleIsRealEnoughToBorrow() {
    val dashboard = sampleDashboard()
    val titles = listOfNotNull(
        dashboard.forecast?.headline,
        dashboard.overview.title,
        dashboard.reviews.title,
        dashboard.accuracy.title,
        dashboard.progressByLevel.title,
        dashboard.weakness.title,
    )
    assertEquals(6, titles.size, "a section lost its title entirely")
    assertTrue(titles.all { it.isNotBlank() }, "a section title a chart may borrow is blank: $titles")
    renderStats(
        content = { StatsDashboardScreen(dashboard, dispatch = {}) },
    ) {
        // Rendered, not just present in the model: a title held in state but never
        // drawn is not something a screen reader can reach either.
        val text = onNodeWithTag(STATS_DASHBOARD_TEST_TAG).subtreeTextOrEmpty()
        for (title in titles) {
            assertTrue(title in text, "section title $title never reached the screen")
        }
    }
}

/** Every chart tag on the dashboard, so an added chart kind is one entry rather than four. */
private val CHART_TAGS: List<String> = listOf(
    STATS_LINE_CHART_TEST_TAG,
    STATS_BAR_CHART_TEST_TAG,
    STATS_DONUT_CHART_TEST_TAG,
    STATS_HEATMAP_TEST_TAG,
)
