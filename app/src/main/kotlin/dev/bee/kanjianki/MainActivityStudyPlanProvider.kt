package dev.bee.kanjianki

import android.os.SystemClock
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.DailyStudyPlan
import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.DailyStudyPlanRequest
import dev.bee.kanjianki.core.StudyProjectionEligibilityPolicy
import dev.bee.kanjianki.core.FocusedStudyPlanPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyPlanSelectionPolicy
import dev.bee.kanjianki.core.StudyStreakPolicy
import dev.bee.kanjianki.data.StudyStatsStore
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.StudyStreakSnapshot
import java.util.Collections
import java.util.Locale

internal class MainActivityStudyPlanProvider(private val activity: MainActivityBase) {
    fun adaptivePlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return adaptivePlan(
            rows,
            items,
            now,
            activity.store.studyStreak(now).currentDays,
            activity.settings(),
        )
    }

    /** Home already owns these values; accepting them avoids duplicate aggregate/settings reads. */
    fun adaptivePlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
        streakDays: Int,
        settings: RecordsSyncModels.Settings,
    ): RecordsSchedulerModels.AdaptiveLoadPlan = withStudyLoadProbe("adaptivePlan") {
        val reviewStats = studyPlanPhase("review-stats") {
            withStudyLoadProbe("adaptivePlan.reviewStatsSince") {
                activity.store.reviewStatsSince(now - MainActivityBase.DAY_MILLIS * 7)
            }
        }
        val studiedKanji = studyPlanPhase(
            phase = "studied-kanji",
            details = { values -> "rows=${values.size}" },
        ) {
            withStudyLoadProbe("adaptivePlan.studiedKanjiSince") {
                activity.store.studiedKanjiSince(activity.startOfDay(now))
            }
        }
        val workload = studyPlanPhase("workload-settings") {
            AdaptiveWorkloadSnapshot(
                activity.store.adaptiveLoadWorkPercent(),
                activity.store.adaptiveLoadMaxItems(),
                activity.store.adaptiveLoadMode(),
            )
        }
        adaptivePlan(
            rows = rows,
            items = items,
            now = now,
            streakDays = streakDays,
            settings = settings,
            reviewStats = reviewStats,
            studiedKanji = studiedKanji,
            workload = workload,
        )
    }

    fun adaptivePlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
        streakDays: Int,
        settings: RecordsSyncModels.Settings,
        reviewStats: RecordsSchedulerModels.ReviewStats,
        studiedKanji: Set<String>,
        workload: AdaptiveWorkloadSnapshot,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        val readingExposure = studyPlanPhase("reading-exposure") {
            withStudyLoadProbe("adaptivePlan.readingExposureRead") {
                ReadingExposureMediaReader().read()
            }
        }
        val workloadPolicy = studyPlanPhase("workload-settings") {
            AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                workload.workPercent,
                workload.mode,
                workload.maxItems,
            )
        }
        return studyPlanPhase("planner-compute") {
            withStudyLoadProbe("adaptivePlan.plannerCompute") {
                AdaptiveLoadPlanner().plan(
                    AdaptiveLoadPlanner.PlanRequest.builder(
                        rows,
                        items,
                        reviewStats,
                        streakDays,
                        studiedKanji,
                        workloadPolicy,
                        now,
                    )
                        .settings(settings)
                        .readingExposure(readingExposure)
                        .build()
                )
            }
        }
    }

    fun dailyStudyPlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): DailyStudyPlan {
        val streak = activity.store.studyStreak(now)
        return dailyStudyPlan(rows, items, now, streak, activity.store.latestSuccessfulSyncFinishedAt())
    }

    /** Home already owns streak/sync status; accepting them avoids repeating the same queries. */
    fun dailyStudyPlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
        streak: StudyStatsStore.StudyStreak,
        lastSuccessfulSyncAtMillis: Long?,
    ): DailyStudyPlan {
        val eligibleItems = StudyProjectionEligibilityPolicy.eligibleStudyItems(
            items,
            rows,
            activity.studyLadderSettings(),
        )
        val autoSync = activity.store.autoSyncSettings()
        return DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = eligibleItems.map { it.dueAtMillis },
                studiedToday = streak.studiedToday,
                streak = StudyStreakPolicy.Streak(
                    currentDays = streak.currentDays,
                    bestDays = streak.bestDays,
                    studiedToday = streak.studiedToday,
                    reviewsToday = streak.reviewsToday,
                    lastStudyAtMillis = streak.lastStudyAtMillis,
                ),
                newProblemKanjiAvailable = if (rows.isEmpty()) 0 else eligibleItems.count { it.totalReviews == 0 },
                lastSuccessfulSyncAtMillis = lastSuccessfulSyncAtMillis,
                autoSyncEnabled = autoSync.enabled,
                consecutiveFailedSyncs = activity.store.consecutiveFailedSyncCount(),
            ),
        )
    }

    fun dailyStudyPlan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
        streak: StudyStreakSnapshot,
        lastSuccessfulSyncAtMillis: Long?,
        ladder: RecordsBase.StudyLadderSettings,
        autoSyncEnabled: Boolean,
        consecutiveFailedSyncs: Int,
    ): DailyStudyPlan {
        val eligibleItems = StudyProjectionEligibilityPolicy.eligibleStudyItems(
            items,
            rows,
            ladder,
        )
        return DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = eligibleItems.map { it.dueAtMillis },
                studiedToday = streak.studiedToday,
                streak = StudyStreakPolicy.Streak(
                    currentDays = streak.currentDays,
                    bestDays = streak.bestDays,
                    studiedToday = streak.studiedToday,
                    reviewsToday = streak.reviewsToday,
                    lastStudyAtMillis = streak.lastStudyAtMillis,
                ),
                newProblemKanjiAvailable = if (rows.isEmpty()) 0 else {
                    eligibleItems.count { it.totalReviews == 0 }
                },
                lastSuccessfulSyncAtMillis = lastSuccessfulSyncAtMillis,
                autoSyncEnabled = autoSyncEnabled,
                consecutiveFailedSyncs = consecutiveFailedSyncs,
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

/** Capture-gated release timings for adaptive-plan subphases. */
internal fun <T> studyPlanPhase(
    phase: String,
    details: (T) -> String = { "" },
    action: () -> T,
): T {
    if (!AppDebugLog.isCapturing()) {
        return action()
    }
    val startedAtNanos = studyPlanMonotonicNanos()
    return try {
        val result = action()
        val detail = details(result).trim()
        AppDebugLog.log(
            String.format(
                Locale.US,
                "study-plan phase=%s duration_ms=%.2f%s",
                traceToken(phase),
                (studyPlanMonotonicNanos() - startedAtNanos) / 1_000_000.0,
                if (detail.isEmpty()) "" else " $detail",
            ),
        )
        result
    } catch (error: Throwable) {
        AppDebugLog.logError(
            String.format(
                Locale.US,
                "study-plan phase=%s failed duration_ms=%.2f",
                traceToken(phase),
                (studyPlanMonotonicNanos() - startedAtNanos) / 1_000_000.0,
            ),
            error,
        )
        throw error
    }
}

private fun studyPlanMonotonicNanos(): Long {
    return runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime())
}
