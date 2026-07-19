package dev.bee.kanjianki.widget

import android.content.Context
import dev.bee.kanjianki.data.StudyStatsQueries
import dev.bee.kanjianki.theme.KaniThemeChoice

internal enum class ActivityWidgetState {
    NOT_SET_UP,
    ERROR,
    NO_HISTORY,
    HISTORY,
}

internal data class ActivityWidgetSnapshot(
    val state: ActivityWidgetState,
    val last35DayCounts: List<Int> = emptyList(),
    val streakDays: Int = 0,
    val reviewsToday: Int = 0,
    val last7DayTotal: Int = 0,
    val last35DayTotal: Int = 0,
    val bestStreakDays: Int = 0,
    val themeChoice: KaniThemeChoice = KaniThemeChoice.GIRLYPOP,
)

internal object ActivityWidgetSnapshotLoader {
    internal const val HISTORY_DAYS = 35
    private const val RECENT_DAYS = 7

    fun load(context: Context, nowMillis: Long = System.currentTimeMillis()): ActivityWidgetSnapshot =
        when (val read = WidgetLocalStoreReader.read(context) { store ->
            val counts = StudyStatsQueries(store)
                .reviewDaySummaries(nowMillis, HISTORY_DAYS)
                .map { it.total }
            val streak = store.studyStreak(nowMillis)
            val total = counts.sum()
            ActivityWidgetSnapshot(
                state = if (total > 0) ActivityWidgetState.HISTORY else ActivityWidgetState.NO_HISTORY,
                last35DayCounts = counts,
                streakDays = streak.currentDays,
                reviewsToday = counts.lastOrNull() ?: 0,
                last7DayTotal = counts.takeLast(RECENT_DAYS).sum(),
                last35DayTotal = total,
                bestStreakDays = streak.bestDays,
                themeChoice = store.widgetThemeChoice(),
            )
        }) {
            is WidgetStoreRead.Ready -> read.value
            WidgetStoreRead.NotSetUp -> ActivityWidgetSnapshot(ActivityWidgetState.NOT_SET_UP)
            WidgetStoreRead.Corrupt -> ActivityWidgetSnapshot(ActivityWidgetState.ERROR)
        }
}
