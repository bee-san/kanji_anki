package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.AdaptiveRouteState
import dev.bee.kanjianki.core.AdaptiveRouteStateCodec
import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyTaskTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class StudySessionTrackerTest {
    @Test
    fun stagedCopyIsIsolatedUntilCommitted() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("kanji_meaning:裂"))

        val staged = tracker.copyForStaging()
        staged.setTargetCount(2)
        staged.registerTaskShown("kanji_meaning:謎")
        staged.startActiveTask("task", "謎", BridgeScheduler.TASK_KANJI_MEANING, 10L, false)

        assertEquals(1, tracker.targetCount())
        assertEquals(listOf("kanji_meaning:裂"), tracker.pendingPlannedSessionTaskKeys())
        assertFalse(tracker.hasActiveTask())

        assertTrue(tracker.replaceStateFrom(staged))

        assertEquals(2, tracker.targetCount())
        assertEquals(listOf("kanji_meaning:裂"), tracker.pendingPlannedSessionTaskKeys())
        assertTrue(tracker.hasActiveTask())
    }

    @Test
    fun stateEquivalenceIgnoresRevisionButDetectsMeaningfulChanges() {
        val tracker = StudySessionTracker()
        val completedKey = "session:kanji_meaning:裂:token"
        tracker.setTargetCount(1)
        tracker.markTaskCompleted(completedKey)
        val staged = tracker.copyForStaging()

        tracker.registerTaskShown(completedKey)

        assertTrue("duplicate publication changes revision only", tracker.hasSameStateAs(staged))
        staged.setTargetCount(2)
        assertFalse("target reconciliation is meaningful state", tracker.hasSameStateAs(staged))

        val active = StudySessionTracker()
        active.startActiveTask("task", "裂", BridgeScheduler.TASK_KANJI_MEANING, 10L, false)
        val sameActive = active.copyForStaging()
        assertTrue("identical active-task fields are equivalent", active.hasSameStateAs(sameActive))
        val differentActive = StudySessionTracker()
        differentActive.startActiveTask("task", "裂", BridgeScheduler.TASK_KANJI_MEANING, 11L, false)
        assertFalse("active-task timing is meaningful state", active.hasSameStateAs(differentActive))
    }

    @Test
    fun stagedCommitCannotClobberCanonicalMutationWaitingToPublish() {
        val blockFirstPublication = AtomicBoolean(false)
        val mutationCommitted = CountDownLatch(1)
        val allowPublication = CountDownLatch(1)
        val tracker = StudySessionTracker(
            onChanged = {
                if (blockFirstPublication.compareAndSet(true, false)) {
                    mutationCommitted.countDown()
                    allowPublication.await(5, TimeUnit.SECONDS)
                }
            },
        )
        tracker.setTargetCount(7)
        val staged = tracker.copyForStaging()
        staged.startActiveTask("stale-task", "裂", BridgeScheduler.TASK_KANJI_MEANING, 10L, false)
        blockFirstPublication.set(true)

        val mutation = Thread {
            tracker.markTaskCompleted("session:kanji_meaning:字:canonical-token")
        }
        mutation.start()
        assertTrue(mutationCommitted.await(5, TimeUnit.SECONDS))

        try {
            assertFalse(tracker.replaceStateFrom(staged))
        } finally {
            allowPublication.countDown()
            mutation.join(5_000L)
        }

        assertFalse("canonical mutation thread must finish", mutation.isAlive)
        assertEquals(1, tracker.completedCount())
        assertFalse("stale staged active task must not replace canonical state", tracker.hasActiveTask())
    }

    @Test
    fun activeTaskLifecycleIgnoresEmptyAndDuplicateKeys() {
        val tracker = StudySessionTracker()

        tracker.startActiveTask("", "裂", BridgeScheduler.TASK_KANJI_MEANING, -5L, false)
        assertFalse(tracker.hasActiveTask())

        tracker.startActiveTask("task", null, null, -5L, false)
        assertTrue(tracker.hasActiveTask())
        tracker.startActiveTask("task", "ignored", BridgeScheduler.TASK_WORD_READING, 50L, false)
        assertTrue(tracker.hasActiveTask())

        tracker.abandonActiveTask()
        assertFalse(tracker.hasActiveTask())
    }

    @Test
    fun activeTaskTracksVisibleElapsedTimeWithManualClock() {
        val task = StudySessionTracker.ActiveStudyTask(
            "task",
            null,
            null,
            -5L,
        )

        assertEquals("", task.kanji)
        assertEquals("", task.taskType)
        assertEquals(0L, task.startedAtMillis)

        task.pause(10L)
        assertEquals(0L, task.activeElapsedMillis)
        task.resume(10L)
        task.resume(12L)
        task.pause(25L)
        assertEquals(15L, task.activeElapsedMillis)
        task.pause(30L)
        assertEquals(15L, task.activeElapsedMillis)
        task.resume(40L)
        task.pause(35L)
        assertEquals(15L, task.activeElapsedMillis)
    }

    @Test
    fun preparedCompletionChangesProgressOnlyAfterCommitAndCanRollback() {
        var elapsed = 100L
        val tracker = StudySessionTracker { elapsed }
        tracker.setTargetCount(1)
        tracker.startActiveTask("task", "裂", BridgeScheduler.TASK_KANJI_MEANING, 10L, false)

        val first = tracker.prepareActiveTask("task", "good", 20L, true)
        assertNotNull(first)
        assertTrue(tracker.hasActiveTask())
        assertEquals(0, tracker.completedCount())

        tracker.rollbackPreparedTask(first)
        assertTrue(tracker.hasActiveTask())
        assertEquals(0, tracker.completedCount())

        elapsed = 130L
        val second = tracker.prepareActiveTask("task", "good", 30L, true)
        tracker.commitPreparedTask(second)
        assertFalse(tracker.hasActiveTask())
        assertEquals(1, tracker.completedCount())
    }

    @Test
    fun sessionPlanSkipsCompletedTaskAndResetsForNewRun() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("kanji_meaning:裂", "word_reading:謎"))

        assertEquals("kanji_meaning:裂", tracker.nextPlannedSessionTaskKey())

        tracker.markPlannedSessionTaskCompleted("kanji_meaning", "裂")
        assertEquals("word_reading:謎", tracker.nextPlannedSessionTaskKey())

        tracker.markPlannedSessionTaskCompleted("word_reading", "謎")
        assertEquals("", tracker.nextPlannedSessionTaskKey())

        tracker.resetProgress()
        assertEquals("", tracker.nextPlannedSessionTaskKey())
    }

    @Test
    fun scheduledLearningRepeatsGrowProgressOnePersistedStepAtATime() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("word_reading:done", "kanji_meaning:pending"))
        tracker.markTaskCompleted("session:word_reading:done:initial-token")
        tracker.markPlannedSessionTaskCompleted("word_reading", "done")

        // The lapse changed rung, but completion-by-kanji still identifies
        // this as the original session card's next relearning occurrence.
        val repeat = learningRepeat("done")
        val repeatKeys = tracker.dueCompletedLearningRepeatTaskKeys(listOf(repeat), 1_000L)
        tracker.initializeSessionPlan(
            listOf("kanji_meaning:done", "kanji_meaning:pending"),
            repeatKeys,
        )
        assertEquals(listOf("kanji_meaning:pending"), tracker.pendingPlannedSessionTaskKeys())
        assertEquals(listOf("kanji_meaning:done"), repeatKeys)
        assertEquals(1, tracker.completedCount())
        assertEquals(3, tracker.targetCount())

        // Re-rendering against the same persisted step is idempotent.
        tracker.initializeSessionPlan(
            listOf("kanji_meaning:done", "kanji_meaning:pending"),
            repeatKeys + repeatKeys,
        )
        assertEquals(3, tracker.targetCount())

        // Exact session tokens make each answered appearance count once.
        tracker.markTaskCompleted("session:kanji_meaning:done:first-repeat-token")
        tracker.markTaskCompleted("session:kanji_meaning:done:first-repeat-token")
        assertEquals(2, tracker.completedCount())

        // A later learning step grows the target only once it is persisted.
        tracker.initializeSessionPlan(
            listOf("kanji_meaning:done", "kanji_meaning:pending"),
            repeatKeys,
        )
        assertEquals(4, tracker.targetCount())

        tracker.markTaskCompleted("session:kanji_meaning:done:second-repeat-token")
        assertEquals(3, tracker.completedCount())

        // Graduation schedules no further repeat, so it adds no workload.
        tracker.initializeSessionPlan(
            listOf("kanji_meaning:done", "kanji_meaning:pending"),
            emptyList(),
        )
        assertEquals(4, tracker.targetCount())
    }

    @Test
    fun adaptiveRepairRepeatUsesTheRoutedRepairTaskKey() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("word_reading:done"))
        tracker.markTaskCompleted("session:word_reading:done:initial-token")
        tracker.markPlannedSessionTaskCompleted(StudyTaskTypes.WORD_READING, "done")

        val repeatKeys = tracker.dueCompletedLearningRepeatTaskKeys(
            listOf(adaptiveRepairRepeat("done")),
            1_000L,
        )

        assertEquals(listOf("type_reading:done"), repeatKeys)
    }

    @Test
    fun emptySessionPlanClearsStalePendingKeys() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("kanji_meaning:first", "kanji_meaning:second"))

        tracker.initializeSessionPlan(emptyList())

        assertEquals("", tracker.nextPlannedSessionTaskKey())
        assertEquals(emptyList<String>(), tracker.pendingPlannedSessionTaskKeys())
        assertEquals(0, tracker.targetCount())
    }

    @Test
    fun sessionPlanPrunesPartialOverlapWhilePreservingSurvivorOrder() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(
            listOf("kanji_meaning:first", "kanji_meaning:second", "kanji_meaning:third")
        )

        tracker.initializeSessionPlan(
            listOf("kanji_meaning:third", "kanji_meaning:second", "kanji_meaning:new", "kanji_meaning:new")
        )

        assertEquals(
            listOf("kanji_meaning:second", "kanji_meaning:third", "kanji_meaning:new"),
            tracker.pendingPlannedSessionTaskKeys(),
        )
        assertEquals(3, tracker.targetCount())
    }

    @Test
    fun sessionPlanReplacesDisjointPendingKeysAndKeepsCompletedTarget() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("kanji_meaning:done", "kanji_meaning:old"))
        tracker.markTaskCompleted("session:kanji_meaning:done:token")
        tracker.markPlannedSessionTaskCompleted("kanji_meaning", "done")

        // A sync or settings change can replace the due queue while Study remains
        // open. Keeping the stale key would make selection return null even though
        // the current scheduler plan contains a runnable task. The progress target
        // must shrink to completed + current pending at the same time.
        tracker.initializeSessionPlan(listOf("kanji_meaning:new", "word_reading:newer"))

        assertEquals("kanji_meaning:new", tracker.nextPlannedSessionTaskKey())
        assertEquals(
            listOf("kanji_meaning:new", "word_reading:newer"),
            tracker.pendingPlannedSessionTaskKeys(),
        )
        assertEquals(1, tracker.completedCount())
        assertEquals(3, tracker.targetCount())
    }

    @Test
    fun pendingTaskSelectionSkipsCapacityRejectionsAndKeepsTrackedWork() {
        val tracker = StudySessionTracker()
        assertTrue(tracker.includePendingTask("existing"))
        tracker.setTargetCount(Int.MAX_VALUE)
        val visited = ArrayList<String>()

        val selected = firstTrackablePendingTaskIndex(
            listOf("overflow", "existing", "later-overflow"),
        ) { key ->
            visited.add(key)
            tracker.admitPendingTask(key)
        }

        assertEquals(1, selected)
        assertEquals(listOf("overflow", "existing", "later-overflow"), visited)
        assertEquals(Int.MAX_VALUE, tracker.targetCount())
        assertEquals(
            -1,
            firstTrackablePendingTaskIndex(listOf("overflow"), tracker::admitPendingTask),
        )
    }

    private fun learningRepeat(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            "learning",
            1_000L,
            1.0,
            2.0,
            1,
            0,
            0,
            0,
            "",
            1_000L,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .build()
    }

    private fun adaptiveRepairRepeat(kanji: String): RecordsStudyModels.StudyItem {
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.CONTEXTUAL_READING,
            activeRepairTasks = listOf(StudyTaskTypes.TYPE_READING),
            repairStepMinutes = listOf(10),
            repairDueAtMillis = 1_000L,
            coreDueAtMillis = 10_000L,
        )
        return learningRepeat(kanji).copyBuilder()
            .rung(RecordsBase.LadderRung.WORD_READING)
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .build()
    }

}
