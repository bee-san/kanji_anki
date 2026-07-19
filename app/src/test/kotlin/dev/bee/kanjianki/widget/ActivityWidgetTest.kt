package dev.bee.kanjianki.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.WidgetTextCopy
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityWidgetTest {
    private val history = ActivityWidgetSnapshot(
        state = ActivityWidgetState.HISTORY,
        last35DayCounts = (1..35).map { it % 11 },
        streakDays = 5,
        reviewsToday = 2,
        last7DayTotal = 24,
        last35DayTotal = 87,
        bestStreakDays = 21,
    )

    @Test
    fun responsiveSizesCoverCompactRegularAndWideActivitySurfaces() {
        assertEquals(
            setOf(
                DpSize(120.dp, 72.dp),
                DpSize(120.dp, 120.dp),
                DpSize(250.dp, 130.dp),
            ),
            ActivityWidget.RESPONSIVE_SIZES,
        )
        assertEquals(ActivityWidgetTier.COMPACT, activityWidgetTier(120f, 72f))
        assertEquals(ActivityWidgetTier.REGULAR, activityWidgetTier(120f, 120f))
        assertEquals(ActivityWidgetTier.WIDE, activityWidgetTier(250f, 130f))
    }

    @Test
    fun fontScaleDropsTertiaryCopyBeforeShrinkingReadableText() {
        val compact = activityWidgetLayout(ActivityWidgetTier.COMPACT, fontScale = 1f)
        val enlargedCompact = activityWidgetLayout(ActivityWidgetTier.COMPACT, fontScale = 1.3f)
        val regular = activityWidgetLayout(ActivityWidgetTier.REGULAR, fontScale = 1f)
        val enlarged = activityWidgetLayout(ActivityWidgetTier.REGULAR, fontScale = 1.3f)
        val accessibility = activityWidgetLayout(ActivityWidgetTier.REGULAR, fontScale = 2f)

        assertFalse(compact.showAction)
        assertFalse(compact.useCompactHero)
        assertTrue(compact.stackAction)
        assertTrue(compact.showStreak)
        assertTrue(enlargedCompact.showAction)
        assertTrue(enlargedCompact.useCompactHero)
        assertTrue(regular.showBestStreak)
        assertTrue(regular.showStreak)
        assertTrue(regular.showAction)
        assertTrue(regular.stackAction)
        assertTrue(activityWidgetLayout(ActivityWidgetTier.WIDE, fontScale = 1f).showAction)
        assertTrue(regular.useCompactHero)
        assertFalse(enlarged.showStreak)
        assertTrue(enlarged.showGrid)
        assertTrue(enlarged.useSevenDayGrid)
        assertTrue(enlarged.useCompactHero)
        assertFalse(enlarged.showBestStreak)
        assertFalse(accessibility.showBestStreak)
        assertFalse(accessibility.showStreak)
        assertFalse(accessibility.showGrid)
        assertTrue(accessibility.useCompactHero)
        assertTrue(accessibility.actionFontSp >= 13f)
        assertTrue(accessibility.supportFontSp >= 12f)
    }

    @Test
    fun regularSurfaceUsesACompactSummaryWithAVisibleStatsCue() {
        withLocale(Locale.ENGLISH) {
            val layout = activityWidgetLayout(ActivityWidgetTier.REGULAR, fontScale = 1f)
            val copy = activityWidgetVisibleCopy(
                history,
                activityWidgetPresentation(history, ActivityWidgetTier.REGULAR),
                layout,
            )

            assertTrue(layout.useCompactHero)
            assertTrue(layout.showAction)
            assertEquals("87 reviews", copy.title)
            assertEquals(WidgetTextCopy.statsLabel(), copy.action)
        }
    }

    @Test
    fun historyMetadataKeepsAReadableGapBetweenCurrentAndBestStreaks() {
        assertTrue(ACTIVITY_METADATA_GAP_DP >= 8)
    }

    @Test
    fun enlargedFontUsesWholeReviewCopyAndACompactGrid() {
        withLocale(Locale.ENGLISH) {
            val layout = activityWidgetLayout(ActivityWidgetTier.REGULAR, fontScale = 1.3f)
            val presentation = activityWidgetPresentation(history, ActivityWidgetTier.REGULAR)
            val historyCopy = activityWidgetVisibleCopy(
                history,
                presentation,
                layout,
            )
            val empty = ActivityWidgetSnapshot(
                state = ActivityWidgetState.NO_HISTORY,
                last35DayCounts = List(35) { 0 },
            )
            val emptyCopy = activityWidgetVisibleCopy(
                empty,
                activityWidgetPresentation(empty, ActivityWidgetTier.REGULAR),
                layout,
            )

            assertEquals("24 reviews", historyCopy.title)
            assertEquals("0 reviews", emptyCopy.title)
            assertTrue(layout.showGrid)
            assertTrue(layout.useSevenDayGrid)
            assertEquals(
                history.last35DayCounts.takeLast(7),
                activityWidgetVisibleCells(presentation, layout).map { it.count },
            )
            val contentDescription = activityWidgetContentDescription(history, presentation, layout)
            assertTrue(contentDescription.contains("24 reviews in 7 days"))
            assertFalse(contentDescription.contains("87 reviews in 35 days"))
            assertTrue(
                activityWidgetVisibleCells(
                    presentation,
                    activityWidgetLayout(ActivityWidgetTier.REGULAR, fontScale = 2f),
                ).isEmpty(),
            )
        }
    }

    @Test
    fun sevenDayGridNormalizesIntensityAgainstItsVisibleCells() {
        val hiddenSpike = history.copy(
            last35DayCounts = listOf(100) + List(27) { 0 } + listOf(0, 1, 2, 3, 4, 5, 6),
            last7DayTotal = 21,
            last35DayTotal = 121,
        )
        val layout = activityWidgetLayout(ActivityWidgetTier.REGULAR, fontScale = 1.3f)
        val visibleCells = activityWidgetVisibleCells(
            activityWidgetPresentation(hiddenSpike, ActivityWidgetTier.REGULAR),
            layout,
        )

        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), visibleCells.map { it.count })
        assertEquals(ActivityIntensity.MEDIUM, visibleCells[3].intensity)
        assertEquals(ActivityIntensity.HIGH, visibleCells.last().intensity)
    }

    @Test
    fun compactUsesSevenChronologicalDaysAndRegularUsesAllThirtyFive() {
        val compact = activityWidgetPresentation(history, ActivityWidgetTier.COMPACT)
        val regular = activityWidgetPresentation(history, ActivityWidgetTier.REGULAR)

        assertEquals(history.last35DayCounts.takeLast(7), compact.cells.map { it.count })
        assertEquals(7, compact.cells.size)
        assertEquals(history.last35DayCounts, regular.cells.map { it.count })
        assertEquals(35, regular.cells.size)
        assertTrue(compact.cells.last().isToday)
        assertTrue(regular.cells.last().isToday)
        assertEquals(1, compact.cells.count { it.isToday })
        assertEquals(1, regular.cells.count { it.isToday })
    }

    @Test
    fun activityIntensityHasFourStableDiscreteLevels() {
        assertEquals(ActivityIntensity.EMPTY, activityIntensity(0, 10))
        assertEquals(ActivityIntensity.LOW, activityIntensity(1, 10))
        assertEquals(ActivityIntensity.MEDIUM, activityIntensity(5, 10))
        assertEquals(ActivityIntensity.HIGH, activityIntensity(10, 10))
        assertEquals(ActivityIntensity.LOW, activityIntensity(1, 0))
    }

    @Test
    fun historyShowsCurrentAndBestStreakAndOnlyStatsDestination() {
        withLocale(Locale.ENGLISH) {
            val presentation = activityWidgetPresentation(history, ActivityWidgetTier.WIDE)

            assertEquals(KaniWidgetDestination.STATS, presentation.destination)
            assertEquals("87 reviews in 35 days", presentation.title)
            assertEquals("5-day streak", presentation.streak)
            assertEquals("Best: 21 days", presentation.bestStreak)
            assertEquals(WidgetTextCopy.openStatsLabel(), presentation.action)
            assertTrue(presentation.contentDescription.contains("87 reviews in 35 days"))
            assertTrue(presentation.contentDescription.contains("2 today"))
            assertTrue(presentation.contentDescription.contains("Best: 21 days"))
            assertFalse(presentation.contentDescription.contains("due", ignoreCase = true))
            assertFalse(presentation.contentDescription.contains(WidgetTextCopy.studyNowLabel()))
        }
    }

    @Test
    fun noHistoryRemainsUsefulAndOpensStatsWithoutInventingActivity() {
        withLocale(Locale.ENGLISH) {
            val empty = ActivityWidgetSnapshot(
                state = ActivityWidgetState.NO_HISTORY,
                last35DayCounts = List(35) { 0 },
            )

            val presentation = activityWidgetPresentation(empty, ActivityWidgetTier.REGULAR)

            assertEquals(KaniWidgetDestination.STATS, presentation.destination)
            assertEquals("No activity yet", presentation.title)
            assertEquals(35, presentation.cells.size)
            assertTrue(presentation.cells.all { it.intensity == ActivityIntensity.EMPTY })
            assertTrue(presentation.cells.last().isToday)
            assertEquals(WidgetTextCopy.openStatsLabel(), presentation.action)
            assertFalse(presentation.contentDescription.contains("review ready"))
        }
    }

    @Test
    fun unavailableStatesOpenHomeAndExposeNoFakeCellsOrStatsAction() {
        withLocale(Locale.ENGLISH) {
            val setup = activityWidgetPresentation(
                ActivityWidgetSnapshot(ActivityWidgetState.NOT_SET_UP),
                ActivityWidgetTier.REGULAR,
            )
            val error = activityWidgetPresentation(
                ActivityWidgetSnapshot(ActivityWidgetState.ERROR),
                ActivityWidgetTier.REGULAR,
            )

            listOf(setup, error).forEach { presentation ->
                assertEquals(KaniWidgetDestination.HOME, presentation.destination)
                assertTrue(presentation.cells.isEmpty())
                assertEquals(WidgetTextCopy.openKaniLabel(), presentation.action)
            }
            assertEquals(WidgetTextCopy.notSetUpTitle(), setup.title)
            assertEquals(WidgetTextCopy.errorTitle(), error.title)
            assertFalse(error.contentDescription.contains("87"))
        }
    }

    @Test
    fun japaneseActivitySummaryAndAccessibilityCopyAreLocalized() {
        withLocale(Locale.JAPANESE) {
            val presentation = activityWidgetPresentation(history, ActivityWidgetTier.WIDE)

            assertEquals("35日間で復習87件", presentation.title)
            assertEquals("5日連続", presentation.streak)
            assertEquals("最長21日", presentation.bestStreak)
            assertEquals("統計を開く", presentation.action)
            assertTrue(presentation.contentDescription.contains("35日間で復習87件"))
            assertTrue(presentation.contentDescription.contains("今日2件"))
        }
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
