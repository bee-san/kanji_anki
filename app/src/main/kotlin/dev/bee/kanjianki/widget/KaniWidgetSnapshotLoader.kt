package dev.bee.kanjianki.widget

import android.content.Context
import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.DailyStudyPlanRequest
import dev.bee.kanjianki.core.ReminderEligibilityPolicy
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.data.StudyStatsQueries
import dev.bee.kanjianki.theme.KaniThemeChoice

internal enum class KaniWidgetState {
    NOT_SET_UP,
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
    /** Ascending daily review totals for the heatmap style (up to 35 days). */
    val last35DayCounts: List<Int> = emptyList(),
)

/**
 * Loads the same studyable set used by reminders, without ever creating Kani's
 * database merely because a launcher requested a widget render.
 */
internal object KaniWidgetSnapshotLoader {
    /** 5 heatmap rows x 7 columns. */
    internal const val HEATMAP_DAYS = 35
    internal const val STRIP_DAYS = 7

    fun load(context: Context, nowMillis: Long = System.currentTimeMillis()): KaniWidgetSnapshot {
        val appContext = context.applicationContext
        if (!appContext.getDatabasePath(LocalStoreSchema.DB_NAME).exists()) {
            return KaniWidgetSnapshot(KaniWidgetState.NOT_SET_UP)
        }

        return runCatching {
            LocalStore(appContext).use { store ->
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
                val last35Days: List<Int> = runCatching {
                    val summaries = StudyStatsQueries(store).reviewDaySummaries(nowMillis, HEATMAP_DAYS)
                    summaries.map { it.total }
                }.getOrElse { emptyList() }
                val last7Days: List<Int> = if (last35Days.size > STRIP_DAYS) {
                    last35Days.takeLast(STRIP_DAYS)
                } else {
                    last35Days
                }
                val themeChoice = KaniThemeChoice.fromStorageKey(
                    store.getStringSetting(KaniThemeChoice.SETTING_KEY, null),
                )
                val newDueCount = eligibleItems.count {
                    it.dueAtMillis <= nowMillis && it.totalReviews == 0
                }
                KaniWidgetSnapshot(
                    state = if (dueCount > 0) KaniWidgetState.DUE_NOW else KaniWidgetState.NOTHING_DUE,
                    dueCount = dueCount,
                    streakDays = streak.currentDays,
                    nextUsefulAtMillis = plan.nextUsefulReminderAtMillis,
                    last7DayCounts = last7Days,
                    themeChoice = themeChoice,
                    newDueCount = newDueCount,
                    dueLaterCount = plan.dueLookahead.clusterSize,
                    dueLaterByMillis = plan.dueLookahead.clusterEndAtMillis,
                    bestStreakDays = streak.bestDays,
                    last35DayCounts = last35Days,
                )
            }
        }.getOrElse {
            // A half-created/corrupt database must not crash the launcher host. Opening
            // Kani gives the normal app flow a chance to surface and recover the issue.
            KaniWidgetSnapshot(KaniWidgetState.NOT_SET_UP)
        }
    }
}
