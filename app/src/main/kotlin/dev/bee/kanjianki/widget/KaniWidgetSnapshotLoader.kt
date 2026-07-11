package dev.bee.kanjianki.widget

import android.content.Context
import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.DailyStudyPlanRequest
import dev.bee.kanjianki.core.ReminderEligibilityPolicy
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema

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
)

/**
 * Loads the same studyable set used by reminders, without ever creating Kani's
 * database merely because a launcher requested a widget render.
 */
internal object KaniWidgetSnapshotLoader {
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
                KaniWidgetSnapshot(
                    state = if (dueCount > 0) KaniWidgetState.DUE_NOW else KaniWidgetState.NOTHING_DUE,
                    dueCount = dueCount,
                    streakDays = streak.currentDays,
                    nextUsefulAtMillis = plan.nextUsefulReminderAtMillis,
                )
            }
        }.getOrElse {
            // A half-created/corrupt database must not crash the launcher host. Opening
            // Kani gives the normal app flow a chance to surface and recover the issue.
            KaniWidgetSnapshot(KaniWidgetState.NOT_SET_UP)
        }
    }
}
