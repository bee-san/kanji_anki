package dev.bee.kanjianki.widget

import dev.bee.kanjianki.core.WidgetTextCopy
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class KaniWidgetCopyTest {

    @Test
    fun dueNowExpandedSplitsReviewAndNewCountsWhenBothNonZero() {
        withLocale(Locale.ENGLISH) {
            val snapshot = dueSnapshot(dueCount = 15, newDueCount = 3)

            val copy = widgetCopy(snapshot, isExpanded = true)

            assertEquals("12 reviews · 3 new", copy.title)
            assertEquals(WidgetTextCopy.streakLabel(4), copy.body)
            assertEquals(WidgetTextCopy.studyNowLabel(), copy.action)
        }
    }

    @Test
    fun dueNowCompactKeepsSingleCountEvenWithNewWork() {
        withLocale(Locale.ENGLISH) {
            val snapshot = dueSnapshot(dueCount = 15, newDueCount = 3)

            val copy = widgetCopy(snapshot, isExpanded = false)

            assertEquals("15 reviews ready", copy.title)
            assertEquals("", copy.extraLine)
        }
    }

    @Test
    fun dueNowExpandedKeepsSingleCountWhenOnlyOneKindOfWork() {
        withLocale(Locale.ENGLISH) {
            val allReviews = dueSnapshot(dueCount = 15, newDueCount = 0)
            val allNew = dueSnapshot(dueCount = 3, newDueCount = 3)

            assertEquals("15 reviews ready", widgetCopy(allReviews, isExpanded = true).title)
            assertEquals("3 reviews ready", widgetCopy(allNew, isExpanded = true).title)
        }
    }

    @Test
    fun dueNowExpandedAddsDueLaterLineFromLookaheadCluster() {
        withLocale(Locale.ENGLISH) {
            val byMillis = timeAt(18, 0)
            val snapshot = dueSnapshot(dueCount = 5, dueLaterCount = 5, dueLaterByMillis = byMillis)

            assertEquals("5 more by 18:00", widgetCopy(snapshot, isExpanded = true).extraLine)
            assertEquals("", widgetCopy(snapshot, isExpanded = false).extraLine)
        }
    }

    @Test
    fun nothingDueExpandedShowsBestStreak() {
        withLocale(Locale.ENGLISH) {
            val snapshot = KaniWidgetSnapshot(
                state = KaniWidgetState.NOTHING_DUE,
                streakDays = 4,
                bestStreakDays = 21,
            )

            assertEquals("Best: 21 days", widgetCopy(snapshot, isExpanded = true).extraLine)
            assertEquals("", widgetCopy(snapshot, isExpanded = false).extraLine)
        }
    }

    @Test
    fun heatmapHeaderCombinesBrandDueCountAndStreak() {
        withLocale(Locale.ENGLISH) {
            assertEquals(
                "Kani · 3 reviews ready · 4-day streak",
                heatmapHeaderLine(dueSnapshot(dueCount = 3)),
            )
            assertEquals(
                "Kani · All caught up · 4-day streak",
                heatmapHeaderLine(KaniWidgetSnapshot(KaniWidgetState.NOTHING_DUE, streakDays = 4)),
            )
        }
    }

    @Test
    fun notSetUpHasNoExtraLine() {
        withLocale(Locale.ENGLISH) {
            val snapshot = KaniWidgetSnapshot(KaniWidgetState.NOT_SET_UP)

            val copy = widgetCopy(snapshot, isExpanded = true)

            assertEquals(WidgetTextCopy.notSetUpTitle(), copy.title)
            assertEquals("", copy.extraLine)
        }
    }

    private fun dueSnapshot(
        dueCount: Int,
        newDueCount: Int = 0,
        dueLaterCount: Int = 0,
        dueLaterByMillis: Long = 0L,
    ) = KaniWidgetSnapshot(
        state = KaniWidgetState.DUE_NOW,
        dueCount = dueCount,
        streakDays = 4,
        newDueCount = newDueCount,
        dueLaterCount = dueLaterCount,
        dueLaterByMillis = dueLaterByMillis,
    )

    private fun timeAt(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }.timeInMillis

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
