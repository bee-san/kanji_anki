package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyLadderRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PS1 learn-ahead: when the only work left in an active run is that run's own
 * learning-step repeats due within the learn-ahead horizon, the session keeps
 * serving them instead of rendering the done screen.
 *
 * The done-screen branch in [MainActivityStudyQueueCoordinator] renders done
 * only when `atHardCap(...)` AND
 * `dueCompletedLearningRepeatTaskKeys(items, now + learnAhead)` is empty. Both
 * are public on [StudySessionTracker], so this composed decision is exercised
 * directly here, alongside the [StudySessionActions] serving path.
 */
class StudySessionLearnAheadTest {
    private val now = 2_000L
    private val minute = 60_000L
    private val horizonMillis = StudyLadderRules.LEARN_AHEAD_MILLIS

    @Test
    fun completedTargetWithDueRepeatsDoesNotRenderDone() {
        // Target-3 session, all three answered `again` (now in learning, due
        // 1..3 min out). The hard cap is reached, but the same-session repeats
        // are due within the learn-ahead horizon, so the done screen is
        // suppressed and the earliest repeat is served instead.
        val tracker = StudySessionTracker()
        val kanji = listOf("裂", "謎", "壁")
        tracker.initializeSessionPlan(kanji.map { "kanji_meaning:$it" })
        kanji.forEach { completePlanned(tracker, "kanji_meaning", it) }

        val items = kanji.mapIndexed { index, k ->
            learningRepeat(k, dueAt = now + (index + 1) * minute)
        }

        assertTrue("hard cap should be reached", tracker.atHardCap(false))
        val pending = tracker.dueCompletedLearningRepeatTaskKeys(items, now + horizonMillis)
        assertFalse("same-session repeats keep the run alive", pending.isEmpty())
        // Served earliest-due first.
        assertEquals("kanji_meaning:裂", pending.first())

        val session = StudySessionActions.plannedStudySession(
            BridgeScheduler(),
            tracker,
            items,
            kanji.map { row(it) },
            now,
            0L,
            null,
            RecordsSyncModels.Settings.kikuDefaults(),
            RecordsBase.StudyLadderSettings.defaults(),
        )
        assertNotNull("the run keeps serving its own repeat", session)
        assertEquals("裂", session!!.item!!.kanji)
    }

    @Test
    fun repeatStepsGrowProgressOneOccurrenceAtATimeAndGraduationAddsNothing() {
        val tracker = StudySessionTracker()
        val initial = baseItem("裂")
            .copyBuilder()
            .state("new")
            .totalReviews(0)
            .dueAtMillis(now)
            .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
            .activeToken("initial-token")
            .build()

        val initialSession = plannedSession(tracker, initial)
        assertNotNull(initialSession)
        assertProgress(tracker, completed = 0, target = 1)
        completeSession(tracker, initialSession!!)

        // Failing the initial appearance persists only the next learning step.
        // Reconciliation immediately grows 1 / 1 to 1 / 2, including while
        // the step is merely being served early by learn-ahead.
        val firstRepeat = learningRepeat("裂", dueAt = now + minute)
            .copyBuilder()
            .learningStep(0)
            .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
            .activeToken("repeat-token-1")
            .build()
        val firstRepeatSession = plannedSession(tracker, firstRepeat)
        assertNotNull(firstRepeatSession)
        val nonNullFirstRepeatSession = firstRepeatSession!!
        assertProgress(tracker, completed = 1, target = 2)

        // Rendering the same pending occurrence again is idempotent.
        val rerenderedFirstRepeat = plannedSession(tracker, firstRepeat)
        assertNotNull(rerenderedFirstRepeat)
        assertEquals(nonNullFirstRepeatSession.token, rerenderedFirstRepeat!!.token)
        assertProgress(tracker, completed = 1, target = 2)

        completeSession(tracker, nonNullFirstRepeatSession)

        // Passing the first step schedules exactly one further occurrence. It
        // increments the numerator and grows the target only for that next step.
        val secondRepeat = learningRepeat("裂", dueAt = now + 10 * minute)
            .copyBuilder()
            .learningStep(1)
            .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
            .activeToken("repeat-token-2")
            .build()
        val secondRepeatSession = plannedSession(tracker, secondRepeat)
        assertNotNull(secondRepeatSession)
        assertProgress(tracker, completed = 2, target = 3)
        completeSession(tracker, secondRepeatSession!!)

        // Graduation leaves no persisted learning occurrence, so it adds no
        // fourth unit of work. Reconciliation remains idempotent at 3 / 3.
        val graduated = graduatedReview("裂")
        assertEquals(null, plannedSession(tracker, graduated))
        assertProgress(tracker, completed = 3, target = 3)
        assertEquals(null, plannedSession(tracker, graduated))
        assertProgress(tracker, completed = 3, target = 3)
    }

    @Test
    fun completingTheSameRepeatTokenTwiceIsIdempotent() {
        // Exact session tokens still protect against a duplicate submission of
        // one appearance, while a later step receives a distinct token.
        val tracker = StudySessionTracker()
        val key = "session:kanji_meaning:裂:token-裂"
        tracker.registerTaskShown(key)
        tracker.markTaskCompleted(key)
        val afterFirst = tracker.completedCount()
        val hardCapAfterFirst = tracker.atHardCap(false)

        // Re-serve and complete the same key again.
        tracker.registerTaskShown(key)
        tracker.markTaskCompleted(key)

        assertEquals(afterFirst, tracker.completedCount())
        assertEquals(hardCapAfterFirst, tracker.atHardCap(false))
    }

    @Test
    fun graduatedRepeatsLetTheRunRenderDone() {
        // Once the repeats graduate (leave learning/relearning), no same-session
        // repeat is pending, so the completed-target run renders done.
        val tracker = StudySessionTracker()
        val kanji = listOf("裂", "謎", "壁")
        tracker.initializeSessionPlan(kanji.map { "kanji_meaning:$it" })
        kanji.forEach { completePlanned(tracker, "kanji_meaning", it) }

        // All three graduated to review phase.
        val graduated = kanji.map { graduatedReview(it) }

        assertTrue(tracker.atHardCap(false))
        assertTrue(
            "no pending repeat -> done allowed",
            tracker.dueCompletedLearningRepeatTaskKeys(graduated, now + horizonMillis).isEmpty(),
        )
    }

    @Test
    fun learningRepeatBeyondHorizonDoesNotBlockDone() {
        // A learning repeat whose next step delay exceeds the learn-ahead
        // horizon (custom step > 20 min) legitimately leaves the session; the
        // done screen is not blocked by it (PS2 handles home display until due).
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("kanji_meaning:裂"))
        completePlanned(tracker, "kanji_meaning", "裂")

        val farRepeat = listOf(learningRepeat("裂", dueAt = now + 30 * minute))

        assertTrue(tracker.atHardCap(false))
        assertTrue(
            tracker.dueCompletedLearningRepeatTaskKeys(farRepeat, now + horizonMillis).isEmpty(),
        )
    }

    @Test
    fun ordinaryQueueBuildingKeepsConfiguredStudyAhead() {
        // A fresh, not-in-session learning card due in 5 minutes must NOT be
        // served with the default study-ahead of 0: only same-session completed
        // repeats get the widened learn-ahead horizon.
        val tracker = StudySessionTracker()
        val freshLearning = learningRepeat("裂", dueAt = now + 5 * minute)

        val session = StudySessionActions.plannedStudySession(
            BridgeScheduler(),
            tracker,
            listOf(freshLearning),
            listOf(row("裂")),
            now,
            0L, // configured study-ahead = 0
            null,
            RecordsSyncModels.Settings.kikuDefaults(),
            RecordsBase.StudyLadderSettings.defaults(),
        )

        // Nothing due now and no same-session repeat: no session.
        assertEquals(null, session)
        assertProgress(tracker, completed = 0, target = 0)
    }

    private fun completePlanned(tracker: StudySessionTracker, taskType: String, kanji: String) {
        val sessionKey = "session:$taskType:$kanji:token-$kanji"
        tracker.registerTaskShown(sessionKey)
        tracker.markTaskCompleted(sessionKey)
        tracker.markPlannedSessionTaskCompleted(taskType, kanji)
    }

    private fun completeSession(
        tracker: StudySessionTracker,
        session: RecordsSchedulerModels.StudySession,
    ) {
        val key = StudySessionTracker.sessionTaskKey(session)
        tracker.registerTaskShown(key)
        tracker.markTaskCompleted(key)
        tracker.markPlannedSessionTaskCompleted(session.taskType, session.item!!.kanji)
    }

    private fun plannedSession(
        tracker: StudySessionTracker,
        item: RecordsStudyModels.StudyItem,
    ) = StudySessionActions.plannedStudySession(
        BridgeScheduler(),
        tracker,
        listOf(item),
        listOf(row(item.kanji)),
        now,
        0L,
        null,
        RecordsSyncModels.Settings.kikuDefaults(),
        RecordsBase.StudyLadderSettings.defaults(),
    )

    private fun assertProgress(tracker: StudySessionTracker, completed: Int, target: Int) {
        assertEquals("completed", completed, tracker.completedCount())
        assertEquals("target", target, tracker.targetCount())
    }

    private fun learningRepeat(kanji: String, dueAt: Long): RecordsStudyModels.StudyItem {
        return baseItem(kanji)
            .copyBuilder()
            .state("learning")
            .dueAtMillis(dueAt)
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .build()
    }

    private fun graduatedReview(kanji: String): RecordsStudyModels.StudyItem {
        return baseItem(kanji)
            .copyBuilder()
            .state("review")
            .dueAtMillis(now + 5 * StudyLadderRules.DAY)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .build()
    }

    private fun baseItem(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", 1_000L, 1.0, 2.0, 1, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("token-$kanji")
            .build()
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            null,
            "meaning",
            "",
            kanji,
            1,
            "reason",
            "Needs practice",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>(),
        )
    }
}
