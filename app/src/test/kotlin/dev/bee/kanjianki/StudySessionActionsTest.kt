package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySessionActionsTest {
    @Test
    fun activateStudySessionSavesRegistersAndStartsInOrder() {
        val events = mutableListOf<String>()
        val item = item("語").withToken("token-1")
        val session = RecordsSchedulerModels.StudySession(
            item,
            row("語"),
            "token-1",
            "kanji_meaning",
            false,
            "language",
        )
        val writer = RecordingWriter(events)
        val registrar = RecordingRegistrar(events)
        val starter = RecordingStarter(events)

        val taskKey = StudySessionActions.activateStudySession(session, 1234L, writer, registrar, starter)

        assertEquals("session:kanji_meaning:語:token-1", taskKey)
        assertEquals(listOf("saveItem", "register", "start"), events)
        assertSame(item, writer.item)
        assertEquals(taskKey, registrar.taskKey)
        assertEquals(taskKey, starter.taskKey)
        assertEquals("語", starter.kanji)
        assertEquals("kanji_meaning", starter.taskType)
        assertEquals(1234L, starter.nowMillis)
    }

    @Test
    fun activateStudySessionRejectsNullItemSessions() {
        val session = RecordsSchedulerModels.StudySession(
            null,
            row("語"),
            "token-1",
            "kanji_meaning",
            false,
            "language",
        )

        assertThrows(NullPointerException::class.java) {
            StudySessionActions.activateStudySession(
                session,
                1234L,
                { _: RecordsStudyModels.StudyItem -> },
                { _: String -> },
                { _, _, _, _ -> },
            )
        }
    }

    @Test
    fun plannedStudySessionInitializesPlanBeforeChoosingSession() {
        val tracker = StudySessionTracker()
        val items = listOf(item("語"), item("謎"))
        val rows = listOf(row("語"), row("謎"))

        val session = StudySessionActions.plannedStudySession(
            BridgeScheduler(),
            tracker,
            items,
            rows,
            2_000L,
            0L,
            null,
            RecordsSyncModels.Settings.kikuDefaults(),
            RecordsBase.StudyLadderSettings.defaults(),
        )

        assertNotNull(session)
        val nonNullSession = session!!
        assertTrue(tracker.pendingPlannedSessionTaskKeys().contains(nonNullSession.taskType + ":" + nonNullSession.item!!.kanji))
        assertEquals(2, tracker.pendingPlannedSessionTaskKeys().size)
    }

    @Test
    fun plannedStudySessionRequeuesDueLearningRepeatBeforeRemainingPlan() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("kanji_meaning:裂", "word_reading:謎"))
        tracker.markPlannedSessionTaskCompleted("kanji_meaning", "裂")
        val dueRepeat = item("裂")
            .copyBuilder()
            .state("learning")
            .dueAtMillis(2_000L)
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .build()
        val pendingReview = item("謎")
            .copyBuilder()
            .rung(RecordsBase.LadderRung.WORD_READING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .build()

        val session = StudySessionActions.plannedStudySession(
            BridgeScheduler(),
            tracker,
            listOf(dueRepeat, pendingReview),
            listOf(row("裂"), row("謎")),
            2_000L,
            0L,
            null,
            RecordsSyncModels.Settings.kikuDefaults(),
            RecordsBase.StudyLadderSettings.defaults(),
        )

        assertNotNull(session)
        val nonNullSession = session!!
        assertEquals("裂", nonNullSession.item!!.kanji)
        assertEquals("kanji_meaning", nonNullSession.taskType)
    }

    @Test
    fun plannedStudySessionRequeuesDueLearningRepeatAfterLapseDemotesRung() {
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("word_reading:裂", "kanji_meaning:謎"))
        tracker.markPlannedSessionTaskCompleted("word_reading", "裂")
        val dueRepeat = item("裂")
            .copyBuilder()
            .state("learning")
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .dueAtMillis(2_000L)
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .build()
        val pendingReview = item("謎")
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .build()

        val session = StudySessionActions.plannedStudySession(
            BridgeScheduler(),
            tracker,
            listOf(dueRepeat, pendingReview),
            listOf(row("裂"), row("謎")),
            2_000L,
            0L,
            null,
            RecordsSyncModels.Settings.kikuDefaults(),
            RecordsBase.StudyLadderSettings.defaults(),
        )

        assertNotNull(session)
        val nonNullSession = session!!
        assertEquals("裂", nonNullSession.item!!.kanji)
        assertEquals("kanji_meaning", nonNullSession.taskType)
    }

    @Test
    fun plannedStudySessionServesFutureLearningRepeatWhenNothingElseIsDue() {
        // PS1 learn-ahead: when the only remaining work is this session's own
        // learning-step repeat due a few minutes out (within the 20-minute
        // horizon), it is served rather than ending the run. The other planned
        // card is not yet due (future review beyond the study-ahead of 0), so
        // learn-ahead is what keeps the run alive.
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("kanji_meaning:裂", "word_reading:謎"))
        tracker.markPlannedSessionTaskCompleted("kanji_meaning", "裂")
        val futureRepeat = item("裂")
            .copyBuilder()
            .state("learning")
            .dueAtMillis(2_000L + 5L * 60_000L) // 5 minutes out, inside the horizon
            .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
            .build()
        val notYetDueReview = item("謎")
            .copyBuilder()
            .rung(RecordsBase.LadderRung.WORD_READING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .dueAtMillis(2_000L + 10L * 60_000L) // future, not serveable at study-ahead 0
            .build()

        val session = StudySessionActions.plannedStudySession(
            BridgeScheduler(),
            tracker,
            listOf(futureRepeat, notYetDueReview),
            listOf(row("裂"), row("謎")),
            2_000L,
            0L,
            null,
            RecordsSyncModels.Settings.kikuDefaults(),
            RecordsBase.StudyLadderSettings.defaults(),
        )

        assertNotNull(session)
        val nonNullSession = session!!
        assertEquals("裂", nonNullSession.item!!.kanji)
        assertEquals("kanji_meaning", nonNullSession.taskType)
    }

    @Test
    fun plannedStudySessionServesDueNowWorkBeforeFutureLearnAheadRepeat() {
        // Learn-ahead is a fallback: a card due now (here the pending review) is
        // served before a same-session repeat that is only due a few minutes out.
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("kanji_meaning:裂", "word_reading:謎"))
        tracker.markPlannedSessionTaskCompleted("kanji_meaning", "裂")
        val futureRepeat = item("裂")
            .copyBuilder()
            .state("learning")
            .dueAtMillis(2_000L + 5L * 60_000L)
            .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
            .build()
        val dueNowReview = item("謎")
            .copyBuilder()
            .rung(RecordsBase.LadderRung.WORD_READING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .dueAtMillis(1_000L) // due now
            .build()

        val session = StudySessionActions.plannedStudySession(
            BridgeScheduler(),
            tracker,
            listOf(futureRepeat, dueNowReview),
            listOf(row("裂"), row("謎")),
            2_000L,
            0L,
            null,
            RecordsSyncModels.Settings.kikuDefaults(),
            RecordsBase.StudyLadderSettings.defaults(),
        )

        assertNotNull(session)
        val nonNullSession = session!!
        assertEquals("謎", nonNullSession.item!!.kanji)
        assertEquals("word_reading", nonNullSession.taskType)
    }

    @Test
    fun plannedStudySessionKeepsLearningRepeatBeyondHorizonBehindRemainingPlan() {
        // A learning repeat whose next step delay exceeds the learn-ahead
        // horizon (e.g. a custom step longer than 20 minutes) is NOT served
        // early; the remaining plan is served instead.
        val tracker = StudySessionTracker()
        tracker.initializeSessionPlan(listOf("kanji_meaning:裂", "word_reading:謎"))
        tracker.markPlannedSessionTaskCompleted("kanji_meaning", "裂")
        val farFutureRepeat = item("裂")
            .copyBuilder()
            .state("learning")
            .dueAtMillis(2_000L + 30L * 60_000L) // 30 minutes out, beyond the horizon
            .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
            .build()
        val pendingReview = item("謎")
            .copyBuilder()
            .rung(RecordsBase.LadderRung.WORD_READING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .build()

        val session = StudySessionActions.plannedStudySession(
            BridgeScheduler(),
            tracker,
            listOf(farFutureRepeat, pendingReview),
            listOf(row("裂"), row("謎")),
            2_000L,
            0L,
            null,
            RecordsSyncModels.Settings.kikuDefaults(),
            RecordsBase.StudyLadderSettings.defaults(),
        )

        assertNotNull(session)
        val nonNullSession = session!!
        assertEquals("謎", nonNullSession.item!!.kanji)
        assertEquals("word_reading", nonNullSession.taskType)
    }

    private fun item(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", 1000L, 1.0, 2.0, 1, 0, 0, 0, "", 1000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
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

    private class RecordingWriter(private val events: MutableList<String>) : StudySessionActions.StudyItemWriter {
        var item: RecordsStudyModels.StudyItem? = null

        override fun saveStudyItem(item: RecordsStudyModels.StudyItem) {
            events.add("saveItem")
            this.item = item
        }
    }

    private class RecordingRegistrar(private val events: MutableList<String>) : StudySessionActions.TaskRegistrar {
        var taskKey: String? = null

        override fun registerStudyTaskShown(taskKey: String) {
            events.add("register")
            this.taskKey = taskKey
        }
    }

    private class RecordingStarter(private val events: MutableList<String>) : StudySessionActions.ActiveTaskStarter {
        var taskKey: String? = null
        var kanji: String? = null
        var taskType: String? = null
        var nowMillis: Long = 0

        override fun startActiveStudyTask(taskKey: String, kanji: String, taskType: String, nowMillis: Long) {
            events.add("start")
            this.taskKey = taskKey
            this.kanji = kanji
            this.taskType = taskType
            this.nowMillis = nowMillis
        }
    }
}
