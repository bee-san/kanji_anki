package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DailyStudyPlanPolicyTest {
    @Test
    fun dueNowTakesPriorityAndKeepsAHelpfulEstimate() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)
            val request = DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = listOf(
                    utc(2026, Calendar.MAY, 15, 7, 55),
                    utc(2026, Calendar.MAY, 15, 7, 45),
                    utc(2026, Calendar.MAY, 15, 7, 30),
                    utc(2026, Calendar.MAY, 15, 7, 15),
                    utc(2026, Calendar.MAY, 15, 10, 30),
                ),
                streak = StudyStreakPolicy.Streak(0, 0, false, 0, 0L),
                lastSuccessfulSyncAtMillis = utc(2026, Calendar.MAY, 15, 7, 0),
            )

            val plan = DailyStudyPlanPolicy.plan(request)

            assertEquals(LocalDayPolicy.localDayStart(now), plan.dateLocalDay)
            assertEquals(4, plan.dueNow)
            assertEquals(1, plan.dueLater)
            assertEquals(RecommendedAction.STUDY_NOW, plan.recommendedAction)
            assertEquals(2, plan.estimatedMinutes)
            assertEquals(SyncStatus.CURRENT, plan.syncStatus)
            assertEquals(now, plan.nextUsefulReminderAtMillis)
            assertEquals(4, plan.dueLookahead.dueNow)
            assertEquals(1, plan.dueLookahead.dueSoon)
            assertEquals(utc(2026, Calendar.MAY, 15, 10, 30), plan.dueLookahead.nextClusterAtMillis)
            assertEquals(now, plan.dueLookahead.recommendedReminderAtMillis)
            assertEquals(listOf("4 due now"), plan.reasons)
            assertTrue(plan.streakStatus == StreakStatus.NOT_STARTED)
        }
    }

    @Test
    fun nearbyDueRepeatsClusterIntoOneUsefulReminder() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)
            val request = DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = listOf(
                    utc(2026, Calendar.MAY, 15, 8, 20),
                    utc(2026, Calendar.MAY, 15, 8, 45),
                    utc(2026, Calendar.MAY, 15, 10, 0),
                    utc(2026, Calendar.MAY, 15, 14, 0),
                ),
                lastSuccessfulSyncAtMillis = now - 3_600_000L,
            )

            val plan = DailyStudyPlanPolicy.plan(request)

            assertEquals(4, plan.dueLater)
            assertEquals(RecommendedAction.WAIT_UNTIL_LATER, plan.recommendedAction)
            assertEquals(utc(2026, Calendar.MAY, 15, 10, 0), plan.nextUsefulReminderAtMillis)
            assertEquals(4, plan.dueLookahead.dueSoon)
            assertEquals(utc(2026, Calendar.MAY, 15, 8, 20), plan.dueLookahead.nextClusterAtMillis)
            assertEquals(3, plan.dueLookahead.clusterSize)
            assertEquals(utc(2026, Calendar.MAY, 15, 10, 0), plan.dueLookahead.recommendedReminderAtMillis)
            assertEquals(listOf("4 learning repeats later"), plan.reasons)
        }
    }

    @Test
    fun studiedTodayWithoutDueWorkIsNothingUsefulNow() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)
            val request = DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = emptyList(),
                studiedToday = true,
                streak = StudyStreakPolicy.Streak(3, 6, true, 2, now - 3_600_000L),
                lastSuccessfulSyncAtMillis = now - 1_800_000L,
            )

            val plan = DailyStudyPlanPolicy.plan(request)

            assertEquals(0, plan.dueNow)
            assertEquals(0, plan.dueLater)
            assertEquals(StreakStatus.SAFE, plan.streakStatus)
            assertEquals(RecommendedAction.NOTHING_USEFUL_NOW, plan.recommendedAction)
            assertEquals(SyncStatus.CURRENT, plan.syncStatus)
            assertEquals(0, plan.estimatedMinutes)
            assertEquals(0L, plan.nextUsefulReminderAtMillis)
            assertEquals(0, plan.dueLookahead.clusterSize)
            assertEquals(listOf("nothing useful now"), plan.reasons)
        }
    }

    @Test
    fun activeStreakWithoutTodayReviewPromptsOneReview() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)
            val request = DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = emptyList(),
                studiedToday = false,
                streak = StudyStreakPolicy.Streak(4, 9, false, 0, now - 86_400_000L),
                lastSuccessfulSyncAtMillis = now - 1_800_000L,
            )

            val plan = DailyStudyPlanPolicy.plan(request)

            assertEquals(0, plan.dueNow)
            assertEquals(0, plan.dueLater)
            assertEquals(StreakStatus.NEEDS_ONE_REVIEW, plan.streakStatus)
            assertEquals(RecommendedAction.STUDY_ONCE_FOR_STREAK, plan.recommendedAction)
            assertEquals(now, plan.nextUsefulReminderAtMillis)
            assertEquals(SyncStatus.CURRENT, plan.syncStatus)
            assertEquals(listOf("streak needs one review"), plan.reasons)
        }
    }

    @Test
    fun missingSyncEvidenceRequestsSyncWhenProgressCannotBeJudged() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)
            val request = DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = emptyList(),
                streak = null,
                lastSuccessfulSyncAtMillis = null,
                syncFreshnessMillis = null,
            )

            val plan = DailyStudyPlanPolicy.plan(request)

            assertEquals(0, plan.dueNow)
            assertEquals(0, plan.dueLater)
            assertEquals(StreakStatus.NOT_STARTED, plan.streakStatus)
            assertEquals(SyncStatus.SYNC_NEEDED_TO_JUDGE_PROGRESS, plan.syncStatus)
            assertEquals(RecommendedAction.SYNC_FIRST, plan.recommendedAction)
            assertEquals(now, plan.nextUsefulReminderAtMillis)
            assertEquals(0, plan.estimatedMinutes)
            assertEquals(listOf("sync needed before Kani can judge progress"), plan.reasons)
        }
    }

    @Test
    fun estimateSaturatesWhenUntrustedCountsAndDurationsAreHuge() {
        withUtcZone {
            val plan = DailyStudyPlanPolicy.plan(
                DailyStudyPlanRequest(
                    nowMillis = utc(2026, Calendar.MAY, 15, 8, 0),
                    newProblemKanjiAvailable = Int.MAX_VALUE,
                    lastSuccessfulSyncAtMillis = utc(2026, Calendar.MAY, 15, 7, 0),
                    estimatedSecondsPerItem = Int.MAX_VALUE,
                ),
            )

            assertEquals(Int.MAX_VALUE, plan.estimatedMinutes)
        }
    }

    private fun withUtcZone(body: () -> Unit) {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            body()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(year, month, day, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
