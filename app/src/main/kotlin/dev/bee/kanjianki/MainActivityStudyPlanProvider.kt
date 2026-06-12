package dev.bee.kanjianki

import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.DailyStudyPlan
import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.DailyStudyPlanRequest
import dev.bee.kanjianki.core.FocusedStudyPlanPolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyPlanSelectionPolicy
import dev.bee.kanjianki.core.StudyStreakPolicy
import java.util.Collections

internal class MainActivityStudyPlanProvider(private val activity: MainActivityBase) {
    fun adaptivePlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return AdaptiveLoadPlanner().plan(
            AdaptiveLoadPlanner.PlanRequest.builder(
                rows,
                items,
                activity.store.reviewStatsSince(now - MainActivityBase.DAY_MILLIS * 7),
                activity.store.studyStreak(now).currentDays,
                activity.store.studiedKanjiSince(activity.startOfDay(now)),
                AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                    activity.store.adaptiveLoadWorkPercent(),
                    activity.store.adaptiveLoadMode(),
                    activity.store.adaptiveLoadMaxItems(),
                ),
                now,
            )
                .settings(activity.settings())
                .build()
        )
    }

    fun dailyStudyPlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): DailyStudyPlan {
        val streak = activity.store.studyStreak(now)
        return DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = items.map { it.dueAtMillis },
                studiedToday = streak.studiedToday,
                streak = StudyStreakPolicy.Streak(
                    currentDays = streak.currentDays,
                    bestDays = streak.bestDays,
                    studiedToday = streak.studiedToday,
                    reviewsToday = streak.reviewsToday,
                    lastStudyAtMillis = streak.lastStudyAtMillis,
                ),
                newProblemKanjiAvailable = if (rows.isEmpty()) 0 else items.count { it.totalReviews == 0 },
                lastSuccessfulSyncAtMillis = activity.store.latestSync()?.finishedAt,
            ),
        )
    }

    fun studyPlanForMode(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        var studiedToday: Set<String> = Collections.emptySet()
        var adaptive: RecordsSchedulerModels.AdaptiveLoadPlan? = null
        if (activity.studyMoreNewCardKanji.isEmpty()) {
            if (activity.continueAllKanjiSession) {
                studiedToday = activity.store.studiedKanjiSince(activity.startOfDay(now))
            } else {
                adaptive = adaptivePlan(rows, items, now)
            }
        }
        return StudyPlanSelectionPolicy.select(
            activity.studyMoreNewCardKanji,
            activity.continueAllKanjiSession,
            rows,
            items,
            studiedToday,
            now,
            adaptive,
        )
    }

    fun studyMoreNewCardsPlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return FocusedStudyPlanPolicy.studyMoreNewCardsPlan(activity.studyMoreNewCardKanji, rows, items, now)
    }

    fun allCurrentProblemKanjiPlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return FocusedStudyPlanPolicy.allCurrentProblemKanjiPlan(
            rows,
            items,
            activity.store.studiedKanjiSince(activity.startOfDay(now)),
            now,
        )
    }
}
