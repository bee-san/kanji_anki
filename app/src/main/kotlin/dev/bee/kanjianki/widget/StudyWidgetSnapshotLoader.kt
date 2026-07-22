package dev.bee.kanjianki.widget

import android.content.Context
import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.DailyStudyPlanRequest
import dev.bee.kanjianki.core.ReminderEligibilityPolicy
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.data.StudyStatsQueries
import dev.bee.kanjianki.core.KaniThemeChoice

internal enum class KaniWidgetState {
    NOT_SET_UP,
    ERROR,
    NOTHING_DUE,
    DUE_NOW,
}

internal data class KaniWidgetSnapshot(
    val state: KaniWidgetState,
    val dueCount: Int = 0,
    val streakDays: Int = 0,
    val nextUsefulAtMillis: Long = 0L,
    val last7DayCounts: List<Int> = emptyList(),
    val themeChoice: KaniThemeChoice = KaniThemeChoice.GIRLYPOP,
    /** Due-now items that have never been reviewed (new work inside [dueCount]). */
    val newDueCount: Int = 0,
    /** Size of the next due-later cluster arriving today. */
    val dueLaterCount: Int = 0,
    /** When that cluster finishes arriving ("N more by HH:MM"). */
    val dueLaterByMillis: Long = 0L,
    val bestStreakDays: Int = 0,
)

/** Loads Study Overview and Quick Study facts from the canonical studyable set. */
internal object StudyWidgetSnapshotLoader {
    internal const val STRIP_DAYS = 7

    fun load(context: Context, nowMillis: Long = System.currentTimeMillis()): KaniWidgetSnapshot =
        when (val read = WidgetLocalStoreReader.read(context) { store ->
            val rows = store.activeDashboardRows()
            val eligibleItems = ReminderEligibilityPolicy.eligibleReminderItems(
                store.studyItems(),
                rows,
                store.studyLadderSettings(),
            )
            val streak = store.studyStreak(nowMillis)
            val dueCount = eligibleItems.count { it.dueAtMillis <= nowMillis }
            val plan = DailyStudyPlanPolicy.plan(
                DailyStudyPlanRequest(
                    nowMillis = nowMillis,
                    dueAtMillis = eligibleItems.map { it.dueAtMillis },
                    studiedToday = streak.studiedToday,
                    streak = StudyStreakPolicy.Streak(
                        currentDays = streak.currentDays,
                        bestDays = streak.bestDays,
                        studiedToday = streak.studiedToday,
                        reviewsToday = streak.reviewsToday,
                        lastStudyAtMillis = streak.lastStudyAtMillis,
                    ),
                    newProblemKanjiAvailable = eligibleItems.count { it.totalReviews == 0 },
                    lastSuccessfulSyncAtMillis = store.latestSuccessfulSyncFinishedAt(),
                ),
            )
            val last7Days = StudyStatsQueries(store)
                .reviewDaySummaries(nowMillis, STRIP_DAYS)
                .map { it.total }
            KaniWidgetSnapshot(
                state = if (dueCount > 0) KaniWidgetState.DUE_NOW else KaniWidgetState.NOTHING_DUE,
                dueCount = dueCount,
                streakDays = streak.currentDays,
                nextUsefulAtMillis = plan.nextUsefulReminderAtMillis,
                last7DayCounts = last7Days,
                themeChoice = store.widgetThemeChoice(),
                newDueCount = eligibleItems.count {
                    it.dueAtMillis <= nowMillis && it.totalReviews == 0
                },
                dueLaterCount = plan.dueLookahead.clusterSize,
                dueLaterByMillis = plan.dueLookahead.clusterEndAtMillis,
                bestStreakDays = streak.bestDays,
            )
        }) {
            is WidgetStoreRead.Ready -> read.value
            WidgetStoreRead.NotSetUp -> KaniWidgetSnapshot(
                KaniWidgetState.NOT_SET_UP,
                themeChoice = KaniThemeChoice.SYSTEM,
            )
            WidgetStoreRead.Corrupt -> KaniWidgetSnapshot(
                KaniWidgetState.ERROR,
                themeChoice = KaniThemeChoice.SYSTEM,
            )
        }
}
