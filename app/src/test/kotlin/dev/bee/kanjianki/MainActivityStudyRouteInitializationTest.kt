package dev.bee.kanjianki

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.core.StudyTaskTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyRouteInitializationTest {
    @Test
    fun flashcardRouteStateIsInitializedAfterRoutePreparation() {
        val activity = createActivity()
        val session = flashcardSession()
        activity.activeSession = session

        activity.renderComposeFlashcardSession(session)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity.flashcardRevealState)
        assertNotNull(activity.flashcardActionBarState)
        assertEquals(session.token, activity.studyAnswerFeedbackState?.sessionToken)
    }

    @Test
    fun writingRouteStateIsInitializedAfterRoutePreparation() {
        val activity = createActivity()
        val session = writingSession()
        activity.activeSession = session

        activity.renderComposeWritingSession(session)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity.writingAnswerPanelState)
        assertEquals(session.token, activity.studyAnswerFeedbackState?.sessionToken)
    }

    @Test
    fun answeredFlashcardRouteRestoresAppliedFeedbackAndTypedAnswer() {
        val activity = createActivity()
        val baseSession = flashcardSession()
        val session = RecordsSchedulerModels.StudySession(
            baseSession.item,
            baseSession.row,
            baseSession.token,
            StudyTaskTypes.TYPING_MEANING,
            baseSession.writingRequired,
            baseSession.prompt,
        )
        activity.activeSession = session
        val feedback = activity.prepareStudyAnswerFeedback(session.token)
        assertTrue(feedback.begin(StudyAnswerOutcome.INCORRECT, selectedAnswer = "strong"))
        assertTrue(feedback.markApplied(session.token))

        activity.composeRoute(selected = MainActivityBase.NAV_HOME_ROUTE, content = {})
        activity.renderComposeFlashcardSession(session)
        shadowOf(Looper.getMainLooper()).idle()

        assertSame(feedback, activity.studyAnswerFeedbackState)
        assertTrue(activity.flashcardRevealState?.isRevealed == true)
        assertEquals("strong", activity.typingAnswerState?.text?.toString())
        assertTrue(activity.studyAnswerFeedbackState?.continueEnabled == true)
    }

    @Test
    fun answeredWritingRouteRestoresAppliedFeedbackAfterActivityStateRecreation() {
        val activity = createActivity()
        val session = writingSession()
        val snapshot = StudyPendingAnswerSnapshot(
            feedback = StudyAnswerFeedbackSnapshot(
                sessionToken = session.token,
                phase = StudyAnswerFeedbackPhase.APPLIED,
                outcome = StudyAnswerOutcome.CORRECT,
                selectedAnswer = StudyRatings.GOOD,
            ),
            kanji = session.item?.kanji.orEmpty(),
            taskType = session.taskType,
            writingRequired = session.writingRequired,
            prompt = session.prompt,
        )
        activity.activeSession = session
        activity.restorePendingStudyAnswer(snapshot)

        activity.renderComposeWritingSession(session)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(activity.writingAnswerPanelState?.visible == true)
        assertEquals(StudyAnswerOutcome.CORRECT, activity.studyAnswerFeedbackState?.outcome)
        assertTrue(activity.studyAnswerFeedbackState?.continueEnabled == true)
    }

    @Test
    fun processRestartRouteRestoresPendingAnsweredFlashcardBeforeSelectingNextItem() {
        val activity = createActivity()
        val baseSession = flashcardSession()
        val session = RecordsSchedulerModels.StudySession(
            baseSession.item,
            baseSession.row,
            "process-restart-token",
            StudyTaskTypes.TYPING_MEANING,
            baseSession.writingRequired,
            baseSession.prompt,
        )
        val preferences = activity.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        activity.store.saveStudyItem(session.item!!.copyBuilder().activeToken("").build())
        StudyPendingAnswerStore(preferences).save(
            StudyPendingAnswerSnapshot(
                feedback = StudyAnswerFeedbackSnapshot(
                    sessionToken = session.token,
                    phase = StudyAnswerFeedbackPhase.APPLIED,
                    outcome = StudyAnswerOutcome.INCORRECT,
                    selectedAnswer = "strong",
                ),
                kanji = session.item!!.kanji,
                taskType = session.taskType,
                writingRequired = session.writingRequired,
                prompt = session.prompt,
            ),
        )
        val ioTasks = QueueingExecutorService()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val restoredActivity = controller.get()
        replaceField(restoredActivity, "io", ioTasks)

        try {
            controller.create().start().resume()
            ioTasks.runAll()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(session.token, restoredActivity.activeSession?.token)
            assertEquals(session.token, restoredActivity.studyAnswerFeedbackState?.sessionToken)
            assertTrue(restoredActivity.studyAnswerFeedbackState?.continueEnabled == true)
            assertTrue(restoredActivity.flashcardRevealState?.isRevealed == true)
            assertEquals("strong", restoredActivity.typingAnswerState?.text?.toString())
        } finally {
            preferences.edit().clear().commit()
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun processRestartRestoresPendingAnsweredRepairWithoutCanonicalStudyItem() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending_study_answer", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        StudyPendingAnswerStore(preferences).save(
            StudyPendingAnswerSnapshot(
                feedback = StudyAnswerFeedbackSnapshot(
                    sessionToken = "repair-process-restart-token",
                    phase = StudyAnswerFeedbackPhase.APPLIED,
                    outcome = StudyAnswerOutcome.INCORRECT,
                    selectedAnswer = StudyRatings.AGAIN,
                ),
                kanji = "修",
                taskType = MainActivityBase.TASK_REPAIR_WRITING,
                writingRequired = true,
                prompt = "Write the similar kanji 修",
            ),
        )
        val ioTasks = QueueingExecutorService()
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val restoredActivity = controller.get()
        replaceField(restoredActivity, "io", ioTasks)

        try {
            controller.create().start().resume()
            ioTasks.runAll()
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals("repair-process-restart-token", restoredActivity.activeSession?.token)
            assertEquals(MainActivityBase.TASK_REPAIR_WRITING, restoredActivity.activeSession?.taskType)
            assertTrue(restoredActivity.studyAnswerFeedbackState?.continueEnabled == true)
            assertTrue(restoredActivity.writingAnswerPanelState?.visible == true)
        } finally {
            preferences.edit().clear().commit()
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
            controller.pause().stop().destroy()
        }
    }

    private fun createActivity(): MainActivity {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        return try {
            Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get().also { activity ->
                activity.cancelPendingHomeRouteLoads()
                activity.intent.removeExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE)
            }
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private fun flashcardSession(): RecordsSchedulerModels.StudySession {
        return RecordsSchedulerModels.StudySession(
            item = studyItem("弱", "flashcard-token"),
            row = null,
            token = "flashcard-token",
            taskType = StudyTaskTypes.KANJI_MEANING,
            writingRequired = false,
            prompt = "prompt text",
        )
    }

    private fun writingSession(): RecordsSchedulerModels.StudySession {
        return RecordsSchedulerModels.StudySession(
            item = studyItem("書", "writing-token"),
            row = null,
            token = "writing-token",
            taskType = StudyTaskTypes.WRITE_KANJI,
            writingRequired = true,
            prompt = "prompt text",
        )
    }

    private fun studyItem(kanji: String, token: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", 1_000L, 1.0, 2.0, 1, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken(token)
            .build()
    }

    private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
        val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(Context::class.java, List::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(
            ApplicationProvider.getApplicationContext<Context>(),
            emptyList<Any>(),
        ) as AnkiDroidGateway
    }

    private fun replaceField(activity: MainActivity, propertyName: String, value: Any) {
        val field = MainActivityBase::class.java.getDeclaredField(propertyName)
        field.isAccessible = true
        field.set(activity, value)
    }

    private class QueueingExecutorService : AbstractExecutorService() {
        private val tasks = ArrayDeque<Runnable>()
        private var shutdown = false

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            val remaining = tasks.toMutableList()
            tasks.clear()
            return remaining
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown && tasks.isEmpty()

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = isTerminated

        override fun execute(command: Runnable) {
            check(!shutdown)
            tasks.addLast(command)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) {
                tasks.removeFirst().run()
            }
        }
    }
}
