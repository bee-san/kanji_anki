package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DailyReminderDecisionPolicyTest {
    @Test
    fun noUsefulWorkDoesNotScheduleDueReminder() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)
            val decision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    plan = plan(
                        nowMillis = now,
                        recommendedAction = RecommendedAction.NOTHING_USEFUL_NOW,
                        reasons = listOf("nothing useful now"),
                        streakStatus = StreakStatus.SAFE,
                        syncStatus = SyncStatus.CURRENT,
                    ),
                ),
            )

            assertFalse(decision.shouldSchedule)
            assertEquals(null, decision.family)
            assertEquals(0L, decision.triggerAtMillis)
            assertEquals("Nothing useful now", decision.title)
            assertEquals("Nothing useful now.", decision.body)
            assertEquals(listOf("plan:nothing-useful-now"), decision.reasonIds)
            assertEquals("nothing useful now", decision.humanReason)
        }
    }

    @Test
    fun studiedTodaySuppressesStreakReminder() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)
            val decision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    plan = plan(
                        nowMillis = now,
                        recommendedAction = RecommendedAction.NOTHING_USEFUL_NOW,
                        streakStatus = StreakStatus.SAFE,
                        reasons = listOf("nothing useful now"),
                    ),
                ),
            )

            assertFalse(decision.shouldSchedule)
            assertEquals(null, decision.family)
            assertEquals(listOf("plan:nothing-useful-now"), decision.reasonIds)
            assertTrue(decision.humanReason.contains("nothing useful now"))
        }
    }

    @Test
    fun quietHoursApproachingSchedulesOneStreakReminder() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 21, 15)
            val decision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    quietHoursStartMinuteOfDay = 22 * 60,
                    quietHoursLeadMinutes = 60,
                    plan = plan(
                        nowMillis = now,
                        recommendedAction = RecommendedAction.STUDY_ONCE_FOR_STREAK,
                        streakStatus = StreakStatus.NEEDS_ONE_REVIEW,
                        nextUsefulReminderAtMillis = now,
                        reasons = listOf("streak needs one review"),
                    ),
                ),
            )

            assertTrue(decision.shouldSchedule)
            assertEquals(ReminderFamily.STREAK, decision.family)
            assertEquals(now, decision.triggerAtMillis)
            assertEquals("Keep your streak", decision.title)
            assertTrue(decision.body.contains("streak alive"))
            assertTrue(decision.reasonIds.contains("plan:streak-needs-one-review"))
            assertTrue(decision.reasonIds.contains("reminder:quiet-hours-soon"))
        }
    }

    @Test
    fun dismissedTodaySuppressesSameFamilyReminder() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)
            val decision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    dismissedFamiliesToday = setOf(ReminderFamily.DUE),
                    plan = plan(
                        nowMillis = now,
                        dueNow = 4,
                        recommendedAction = RecommendedAction.STUDY_NOW,
                        nextUsefulReminderAtMillis = now,
                        reasons = listOf("4 due now"),
                    ),
                ),
            )

            assertFalse(decision.shouldSchedule)
            assertEquals(ReminderFamily.DUE, decision.family)
            assertEquals(0L, decision.triggerAtMillis)
            assertTrue(decision.reasonIds.contains("reminder:dismissed-today"))
            assertTrue(decision.humanReason.contains("dismissed today"))
        }
    }

    @Test
    fun dueClusterProducesOneDueReminderWithReasonString() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)
            val nextUseful = utc(2026, Calendar.MAY, 15, 10, 0)
            val decision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    plan = plan(
                        nowMillis = now,
                        dueLater = 4,
                        recommendedAction = RecommendedAction.WAIT_UNTIL_LATER,
                        nextUsefulReminderAtMillis = nextUseful,
                        dueLookahead = DueLookaheadWindow(
                            dueNow = 0,
                            dueSoon = 4,
                            nextClusterAtMillis = utc(2026, Calendar.MAY, 15, 8, 20),
                            clusterSize = 3,
                            recommendedReminderAtMillis = nextUseful,
                        ),
                        reasons = listOf("4 learning repeats later"),
                    ),
                ),
            )

            assertTrue(decision.shouldSchedule)
            assertEquals(ReminderFamily.DUE, decision.family)
            assertEquals(nextUseful, decision.triggerAtMillis)
            assertEquals("Study later", decision.title)
            assertTrue(decision.body.contains("4 learning repeats later"))
            assertTrue(decision.body.contains("Next useful time:"))
            assertTrue(decision.reasonIds.contains("plan:due-later-cluster"))
        }
    }

    @Test
    fun dueReminderCapSuppressesRepeatReminders() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)
            val decision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    dueRemindersShownToday = 2,
                    dueReminderCapPerDay = 2,
                    plan = plan(
                        nowMillis = now,
                        dueNow = 2,
                        recommendedAction = RecommendedAction.STUDY_NOW,
                        nextUsefulReminderAtMillis = now,
                        reasons = listOf("2 due now"),
                    ),
                ),
            )

            assertFalse(decision.shouldSchedule)
            assertEquals(ReminderFamily.DUE, decision.family)
            assertEquals(0L, decision.triggerAtMillis)
            assertTrue(decision.reasonIds.contains("reminder:due-cap-reached"))
            assertTrue(decision.humanReason.contains("due reminder cap"))
        }
    }

    @Test
    fun studyNowWithBlankReasonsUsesNewProblemFallbackCopy() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)
            val decision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    plan = plan(
                        nowMillis = now,
                        dueNow = 0,
                        newProblemKanjiAvailable = 1,
                        recommendedAction = RecommendedAction.STUDY_NOW,
                        reasons = listOf(" "),
                    ),
                ),
            )

            assertTrue(decision.shouldSchedule)
            assertEquals(ReminderFamily.DUE, decision.family)
            assertEquals(now, decision.triggerAtMillis)
            assertEquals("Study now", decision.title)
            assertEquals("1 new problem kanji available. Open Kani to review them.", decision.body)
            assertEquals(listOf("plan:new-problem-kanji"), decision.reasonIds)
            assertEquals("1 new problem kanji available", decision.humanReason)
        }
    }

    @Test
    fun waitUntilLaterWithBlankReasonsUsesSingularFallbackCopy() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)
            val nextUseful = utc(2026, Calendar.MAY, 15, 10, 0)
            val decision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    plan = plan(
                        nowMillis = now,
                        dueLater = 1,
                        recommendedAction = RecommendedAction.WAIT_UNTIL_LATER,
                        nextUsefulReminderAtMillis = nextUseful,
                        reasons = listOf(" "),
                    ),
                ),
            )

            assertTrue(decision.shouldSchedule)
            assertEquals(ReminderFamily.DUE, decision.family)
            assertEquals(nextUseful, decision.triggerAtMillis)
            assertEquals("Study later", decision.title)
            assertEquals("1 learning repeat later. Next useful time: 10:00.", decision.body)
            assertEquals(listOf("plan:due-later-cluster"), decision.reasonIds)
            assertEquals("1 learning repeat later", decision.humanReason)
        }
    }

    @Test
    fun streakReminderCoversSafeAndCapFallbacks() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)

            val safeDecision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    plan = plan(
                        nowMillis = now,
                        recommendedAction = RecommendedAction.STUDY_ONCE_FOR_STREAK,
                        streakStatus = StreakStatus.SAFE,
                        reasons = listOf(" "),
                    ),
                ),
            )
            assertFalse(safeDecision.shouldSchedule)
            assertEquals(null, safeDecision.family)
            assertEquals("Nothing useful now", safeDecision.title)
            assertEquals("Nothing useful now.", safeDecision.body)
            assertEquals(listOf("plan:nothing-useful-now"), safeDecision.reasonIds)
            assertEquals("nothing useful now", safeDecision.humanReason)

            val cappedDecision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    streakRemindersShownToday = 1,
                    streakReminderCapPerDay = 1,
                    plan = plan(
                        nowMillis = now,
                        recommendedAction = RecommendedAction.STUDY_ONCE_FOR_STREAK,
                        streakStatus = StreakStatus.NEEDS_ONE_REVIEW,
                        reasons = listOf(" "),
                    ),
                ),
            )
            assertFalse(cappedDecision.shouldSchedule)
            assertEquals(ReminderFamily.STREAK, cappedDecision.family)
            assertEquals(0L, cappedDecision.triggerAtMillis)
            assertEquals("Keep your streak", cappedDecision.title)
            assertEquals("Streak needs one review. Open Kani now to keep your streak alive.", cappedDecision.body)
            assertEquals(
                listOf("plan:streak-needs-one-review", "reminder:streak-cap-reached"),
                cappedDecision.reasonIds,
            )
            assertTrue(cappedDecision.humanReason.contains("streak reminder cap reached"))
        }
    }

    @Test
    fun syncReminderCoversCurrentStateFallbacksAndCaps() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)

            val currentDecision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    plan = plan(
                        nowMillis = now,
                        recommendedAction = RecommendedAction.SYNC_FIRST,
                        syncStatus = SyncStatus.CURRENT,
                        reasons = listOf(" "),
                    ),
                ),
            )
            assertFalse(currentDecision.shouldSchedule)
            assertEquals(null, currentDecision.family)
            assertEquals("Nothing useful now", currentDecision.title)
            assertEquals("Nothing useful now.", currentDecision.body)
            assertEquals(listOf("plan:nothing-useful-now"), currentDecision.reasonIds)

            val neededDecision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    plan = plan(
                        nowMillis = now,
                        recommendedAction = RecommendedAction.SYNC_FIRST,
                        syncStatus = SyncStatus.SYNC_NEEDED_TO_JUDGE_PROGRESS,
                        reasons = listOf(" "),
                    ),
                ),
            )
            assertTrue(neededDecision.shouldSchedule)
            assertEquals(ReminderFamily.SYNC, neededDecision.family)
            assertEquals("Sync Kani", neededDecision.title)
            assertEquals(
                "Sync needed before Kani can judge progress. Open Kani and sync now.",
                neededDecision.body,
            )
            assertEquals(listOf("plan:sync-needed"), neededDecision.reasonIds)

            val manualDecision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    plan = plan(
                        nowMillis = now,
                        recommendedAction = RecommendedAction.SYNC_FIRST,
                        syncStatus = SyncStatus.NO_MANUAL_SYNC_YET,
                        reasons = listOf(" "),
                    ),
                ),
            )
            assertTrue(manualDecision.shouldSchedule)
            assertEquals(ReminderFamily.SYNC, manualDecision.family)
            assertEquals(
                "No manual sync yet. Open Kani and sync now.",
                manualDecision.body,
            )

            val cappedDecision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    syncRemindersShownToday = 1,
                    syncReminderCapPerDay = 1,
                    plan = plan(
                        nowMillis = now,
                        recommendedAction = RecommendedAction.SYNC_FIRST,
                        syncStatus = SyncStatus.UNKNOWN,
                        reasons = listOf(" "),
                    ),
                ),
            )
            assertFalse(cappedDecision.shouldSchedule)
            assertEquals(ReminderFamily.SYNC, cappedDecision.family)
            assertEquals(0L, cappedDecision.triggerAtMillis)
            assertEquals("Sync Kani", cappedDecision.title)
            assertEquals("Sync freshness unknown. Open Kani and sync now.", cappedDecision.body)
            assertEquals(
                listOf("plan:sync-needed", "reminder:sync-cap-reached"),
                cappedDecision.reasonIds,
            )
            assertTrue(cappedDecision.humanReason.contains("sync reminder cap reached"))
        }
    }

    @Test
    fun quietHoursHandleInvalidConfigurationsAndRollOverAfterStart() {
        withUtcZone {
            val now = utc(2026, Calendar.MAY, 15, 23, 15)

            val invalidDecision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    quietHoursStartMinuteOfDay = 22 * 60,
                    quietHoursLeadMinutes = 0,
                    plan = plan(
                        nowMillis = now,
                        dueNow = 1,
                        recommendedAction = RecommendedAction.STUDY_NOW,
                        nextUsefulReminderAtMillis = utc(2026, Calendar.MAY, 16, 1, 0),
                        reasons = listOf("1 due now"),
                    ),
                ),
            )
            assertTrue(invalidDecision.shouldSchedule)
            assertEquals(ReminderFamily.DUE, invalidDecision.family)
            assertEquals(utc(2026, Calendar.MAY, 16, 1, 0), invalidDecision.triggerAtMillis)
            assertEquals("Study now", invalidDecision.title)
            assertEquals("1 due now. Open Kani to review them.", invalidDecision.body)

            val rolledDecision = DailyReminderDecisionPolicy.decide(
                DailyReminderDecisionRequest(
                    nowMillis = now,
                    quietHoursStartMinuteOfDay = 22 * 60,
                    quietHoursLeadMinutes = 60,
                    plan = plan(
                        nowMillis = now,
                        dueLater = 1,
                        recommendedAction = RecommendedAction.WAIT_UNTIL_LATER,
                        nextUsefulReminderAtMillis = utc(2026, Calendar.MAY, 16, 22, 30),
                        reasons = listOf("1 learning repeat later"),
                    ),
                ),
            )
            assertTrue(rolledDecision.shouldSchedule)
            assertEquals(ReminderFamily.DUE, rolledDecision.family)
            assertEquals(utc(2026, Calendar.MAY, 16, 21, 0), rolledDecision.triggerAtMillis)
            assertEquals("Study later", rolledDecision.title)
            assertEquals("1 learning repeat later. Next useful time: 21:00.", rolledDecision.body)
            assertTrue(rolledDecision.reasonIds.contains("reminder:quiet-hours-soon"))
        }
    }

    private fun plan(
        nowMillis: Long,
        dueNow: Int = 0,
        dueLater: Int = 0,
        newProblemKanjiAvailable: Int = 0,
        streakStatus: StreakStatus = StreakStatus.NOT_STARTED,
        estimatedMinutes: Int = 0,
        recommendedAction: RecommendedAction,
        nextUsefulReminderAtMillis: Long = 0L,
        dueLookahead: DueLookaheadWindow = DueLookaheadWindow(
            dueNow = dueNow,
            dueSoon = dueLater,
            nextClusterAtMillis = nextUsefulReminderAtMillis,
            clusterSize = dueLater,
            recommendedReminderAtMillis = nextUsefulReminderAtMillis,
        ),
        syncStatus: SyncStatus = SyncStatus.CURRENT,
        reasons: List<String> = emptyList(),
    ): DailyStudyPlan {
        return DailyStudyPlan(
            dateLocalDay = LocalDayPolicy.localDayStart(nowMillis),
            dueNow = dueNow,
            dueLater = dueLater,
            newProblemKanjiAvailable = newProblemKanjiAvailable,
            streakStatus = streakStatus,
            estimatedMinutes = estimatedMinutes,
            recommendedAction = recommendedAction,
            nextUsefulReminderAtMillis = nextUsefulReminderAtMillis,
            dueLookahead = dueLookahead,
            syncStatus = syncStatus,
            reasons = reasons,
        )
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
