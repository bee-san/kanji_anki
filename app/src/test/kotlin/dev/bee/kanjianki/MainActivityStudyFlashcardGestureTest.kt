package dev.bee.kanjianki

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.core.StudyTaskTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.ArrayDeque
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyFlashcardGestureTest {
    @Test
    fun swipesFromRevealedAnswerPanelAdvanceEvenWhenReleaseLeavesCardBounds() {
        assertSwipeAdvancesWhenReleaseLeavesCardBounds(
            tokenSuffix = "left",
            releaseX = -80f,
            expectedRating = StudyRatings.AGAIN,
        )
        assertSwipeAdvancesWhenReleaseLeavesCardBounds(
            tokenSuffix = "right",
            releaseX = 480f,
            expectedRating = StudyRatings.GOOD,
        )
    }

    @Test
    fun recognitionFailSwipeAsksForCauseAndDismissDoesNotSubmit() {
        val token = "flashcard-token-recognition-cause"
        val activity = createActivity()
        val reviewIo = QueueingExecutorService()
        replaceField(activity, "io", reviewIo)
        activity.activeSession = RecordsSchedulerModels.StudySession(
            item = studyItem("認", token, RecordsBase.LadderRung.KANJI_MEANING),
            row = null,
            token = token,
            taskType = StudyTaskTypes.KANJI_MEANING,
            writingRequired = false,
            prompt = "",
        )
        StudySessionActions.activateStudySession(
            activity.activeSession!!,
            System.currentTimeMillis(),
            activity.store::saveStudyItem,
            activity::registerStudyTaskShown,
            activity::startActiveStudyTask,
        )
        activity.studyAnswerPanel = LinearLayout(activity)
        activity.flashcardHeroPanel = LinearLayout(activity)
        activity.revealFlashcardAnswer()
        activity.setFlashcardGestureBounds(0f, 0f, 400f, 640f)
        val causeState = RecognitionFailureCauseState()
        activity.recognitionFailureCauseState = causeState
        val beforeReviewCount = reviewLogCount(activity)
        val downTime = SystemClock.uptimeMillis()

        activity.handleFlashcardGesture(motionEvent(MotionEvent.ACTION_DOWN, 210f, 520f, downTime, downTime))
        assertTrue(
            activity.handleFlashcardGesture(
                motionEvent(MotionEvent.ACTION_UP, -80f, 520f, downTime, downTime + 120L),
            ),
        )

        assertTrue(causeState.visible)
        assertEquals(0, reviewIo.pendingCount())
        assertEquals(beforeReviewCount, reviewLogCount(activity))

        causeState.dismiss()

        assertFalse(causeState.visible)
        assertEquals(0, reviewIo.pendingCount())
        assertEquals(beforeReviewCount, reviewLogCount(activity))
    }

    private fun assertSwipeAdvancesWhenReleaseLeavesCardBounds(
        tokenSuffix: String,
        releaseX: Float,
        expectedRating: String,
    ) {
        val token = "flashcard-token-$tokenSuffix"
        val kanji = if (tokenSuffix == "left") "弱" else "強"
        val activity = createActivity()
        val reviewIo = QueueingExecutorService()
        replaceField(activity, "io", reviewIo)
        activity.activeSession = RecordsSchedulerModels.StudySession(
            item = studyItem(kanji, token, RecordsBase.LadderRung.WORD_READING),
            row = null,
            token = token,
            taskType = StudyTaskTypes.WORD_READING,
            writingRequired = false,
            prompt = "",
        )
        StudySessionActions.activateStudySession(
            activity.activeSession!!,
            System.currentTimeMillis(),
            activity.store::saveStudyItem,
            activity::registerStudyTaskShown,
            activity::startActiveStudyTask,
        )
        activity.studyAnswerPanel = LinearLayout(activity)
        activity.flashcardHeroPanel = LinearLayout(activity)
        activity.revealFlashcardAnswer()
        assertTrue(activity.flashcardAnswerRevealed)
        activity.setFlashcardGestureBounds(0f, 0f, 400f, 640f)

        val downTime = SystemClock.uptimeMillis()
        // Start the gesture inside the revealed answer panel area.
        val answerPanelStartX = 210f
        val answerPanelStartY = 520f
        val beforeReviewCount = reviewLogCount(activity)

        assertFalse(
            activity.handleFlashcardGesture(
                motionEvent(
                    action = MotionEvent.ACTION_DOWN,
                    x = answerPanelStartX,
                    y = answerPanelStartY,
                    downTime = downTime,
                    eventTime = downTime,
                )
            )
        )
        assertTrue(activity.flashcardTouchTracking)
        assertTrue(
            activity.handleFlashcardGesture(
                motionEvent(
                    action = MotionEvent.ACTION_UP,
                    x = releaseX,
                    y = answerPanelStartY,
                    downTime = downTime,
                    eventTime = downTime + 120L,
                )
            )
        )
        // The swipe queues the review write on the background executor; the click/
        // gesture handler itself never touches the database.
        assertEquals(beforeReviewCount, reviewLogCount(activity))
        reviewIo.runNext()
        assertEquals(beforeReviewCount + 1, reviewLogCount(activity))
        assertEquals(expectedRating, reviewRating(activity, token))
    }

    private fun createActivity(): MainActivity {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        return try {
            Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
        val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(Context::class.java, List::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(
            ApplicationProvider.getApplicationContext<Context>(),
            emptyList<Any>(),
        ) as AnkiDroidGateway
    }

    private fun motionEvent(
        action: Int,
        x: Float,
        y: Float,
        downTime: Long,
        eventTime: Long,
    ): MotionEvent {
        return MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
    }

    private fun reviewLogCount(activity: MainActivity): Int {
        activity.store.readableDatabase.rawQuery("SELECT COUNT(*) FROM review_log", null).use { cursor ->
            check(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun reviewRating(activity: MainActivity, token: String): String {
        activity.store.readableDatabase.rawQuery(
            "SELECT rating FROM review_log WHERE token=?",
            arrayOf(token),
        ).use { cursor ->
            check(cursor.moveToFirst())
            return cursor.getString(0)
        }
    }

    private fun studyItem(
        kanji: String,
        token: String,
        rung: RecordsBase.LadderRung,
    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", 1_000L, 1.0, 2.0, 1, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(rung)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken(token)
            .build()
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

        override fun isShutdown(): Boolean {
            return shutdown
        }

        override fun isTerminated(): Boolean {
            return shutdown && tasks.isEmpty()
        }

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
            return isTerminated()
        }

        override fun execute(command: Runnable) {
            if (shutdown) {
                throw IllegalStateException("executor is shut down")
            }
            tasks.addLast(command)
        }

        fun runNext() {
            tasks.removeFirst().run()
        }

        fun pendingCount(): Int = tasks.size
    }
}
