package dev.bee.kanjianki

import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StudyContinueUiDiagnosticsTest {
    private val events = CopyOnWriteArrayList<StudyContinueUiEvent>()

    @Before
    fun setUp() {
        StudyContinueUiDiagnostics.resetForTests()
        StudyContinueUiDiagnostics.setObserverForTests(events::add)
    }

    @After
    fun tearDown() {
        StudyContinueUiDiagnostics.resetForTests()
        events.clear()
    }

    @Test
    fun mountedEventFormatsPrivacySafeUiStateExactly() {
        val feedback = submittingFeedback()
        val route = route(
            token = feedback.sessionToken,
            phase = StudySessionPhase.SUBMITTING,
            version = 17L,
            feedback = feedback,
        )
        val recorder = StudyContinueUiRecorder(
            action = StudyContinueAction(feedback, { route }) { false },
            mountId = 41L,
        )

        recorder.mounted()

        assertEquals(
            "study-continue-ui mount_id=41 event=mounted token_id=43cebd38 enabled=false " +
                "feedback_phase=SUBMITTING route_phase=SUBMITTING route_version=17 " +
                "route_token_match=true bounds_px=unavailable outcome=none",
            events.single().format(),
        )
    }

    @Test
    fun repeatedCompositionAndLayoutStateIsDeduplicatedAroundOneAcceptedClick() {
        val feedback = submittingFeedback()
        var route = route(
            token = feedback.sessionToken,
            phase = StudySessionPhase.SUBMITTING,
            version = 17L,
            feedback = feedback,
        )
        val recorder = StudyContinueUiRecorder(
            action = StudyContinueAction(feedback, { route }) {
                val continued = feedback.tryContinue()
                route = route(
                    token = feedback.sessionToken,
                    phase = StudySessionPhase.ADVANCING,
                    version = 19L,
                    feedback = feedback,
                )
                continued
            },
            mountId = 42L,
        )
        val bounds = StudyContinueUiBounds(left = 10, top = 20, right = 210, bottom = 82)

        recorder.mounted()
        recorder.stateChanged()
        recorder.stateChanged()
        recorder.boundsChanged(bounds)
        recorder.boundsChanged(bounds)
        assertTrue(feedback.markApplied(feedback.sessionToken))
        route = route(
            token = feedback.sessionToken,
            phase = StudySessionPhase.FEEDBACK,
            version = 18L,
            feedback = feedback,
        )
        recorder.stateChanged()
        recorder.stateChanged()
        recorder.clicked()
        recorder.stateChanged()
        recorder.unmounted()

        assertEquals(
            listOf(
                StudyContinueUiStage.MOUNTED,
                StudyContinueUiStage.STATE_CHANGED,
                StudyContinueUiStage.STATE_CHANGED,
                StudyContinueUiStage.CLICK_ENTRY,
                StudyContinueUiStage.CLICK_COMPLETED,
                StudyContinueUiStage.UNMOUNTED,
            ),
            events.map { it.stage },
        )
        val applied = events[2]
        assertTrue(applied.enabled)
        assertEquals(StudyAnswerFeedbackPhase.APPLIED, applied.feedbackPhase)
        assertEquals(StudySessionPhase.FEEDBACK, applied.routePhase)
        assertEquals(bounds, applied.bounds)

        val click = events[3]
        assertEquals(StudyContinueUiOutcome.NONE, click.outcome)
        assertEquals(StudyAnswerFeedbackPhase.APPLIED, click.feedbackPhase)

        val completed = events[4]
        assertEquals(StudyContinueUiOutcome.ACCEPTED, completed.outcome)
        assertEquals(StudyAnswerFeedbackPhase.CONTINUED, completed.feedbackPhase)
        assertEquals(StudySessionPhase.ADVANCING, completed.routePhase)
        assertFalse(completed.enabled)
    }

    @Test
    fun rejectedCallbackIsReportedWithoutChangingTheAppliedGate() {
        val feedback = submittingFeedback()
        assertTrue(feedback.markApplied(feedback.sessionToken))
        val route = route(
            token = feedback.sessionToken,
            phase = StudySessionPhase.FEEDBACK,
            version = 21L,
            feedback = feedback,
        )
        val recorder = StudyContinueUiRecorder(
            action = StudyContinueAction(feedback, { route }) { false },
            mountId = 43L,
        )

        recorder.mounted()
        recorder.clicked()

        val completed = events.single { it.stage == StudyContinueUiStage.CLICK_COMPLETED }
        assertEquals(StudyContinueUiOutcome.REJECTED, completed.outcome)
        assertEquals(StudyAnswerFeedbackPhase.APPLIED, completed.feedbackPhase)
        assertTrue(completed.enabled)
    }

    @Test
    fun callbackErrorIsReportedAndRethrown() {
        val feedback = submittingFeedback()
        assertTrue(feedback.markApplied(feedback.sessionToken))
        val route = route(
            token = feedback.sessionToken,
            phase = StudySessionPhase.FEEDBACK,
            version = 22L,
            feedback = feedback,
        )
        val failure = IllegalStateException("continue failed")
        val recorder = StudyContinueUiRecorder(
            action = StudyContinueAction(feedback, { route }) { throw failure },
            mountId = 44L,
        )

        recorder.mounted()
        val thrown = assertThrows(IllegalStateException::class.java) {
            recorder.clicked()
        }
        assertTrue(thrown === failure)

        val completed = events.single { it.stage == StudyContinueUiStage.CLICK_COMPLETED }
        assertEquals(StudyContinueUiOutcome.ERROR, completed.outcome)
        assertEquals(StudyAnswerFeedbackPhase.APPLIED, completed.feedbackPhase)
        assertTrue(completed.enabled)
    }

    @Test
    fun eventsNeverRetainOrFormatStudyContentOrRawTokens() {
        val feedback = StudyAnswerFeedbackState("private-session-token").apply {
            begin(StudyAnswerOutcome.INCORRECT, "private user answer")
        }
        val route = route(
            token = feedback.sessionToken,
            phase = StudySessionPhase.SUBMITTING,
            version = 3L,
            feedback = feedback,
        )
        val recorder = StudyContinueUiRecorder(
            action = StudyContinueAction(feedback, { route }) { false },
            mountId = 7L,
        )

        recorder.mounted()
        recorder.boundsChanged(StudyContinueUiBounds(1, 2, 301, 64))

        events.forEach { event ->
            val line = event.format()
            listOf(
                "private-session-token",
                "private user answer",
                "kanji=",
                "prompt=",
                "answer=",
                "token=",
                "session_token",
            ).forEach { forbidden ->
                assertFalse("must not contain $forbidden", line.contains(forbidden))
            }
            assertEquals("43cebd38", event.tokenId)
        }
        val forbiddenFieldTypes = setOf(
            StudyAnswerFeedbackSnapshot::class.java,
            StudyAnswerFeedbackState::class.java,
            StudyRouteSnapshot::class.java,
            StudyContinueAction::class.java,
        )
        assertTrue(
            StudyContinueUiEvent::class.java.declaredFields.none { field ->
                field.type in forbiddenFieldTypes
            },
        )
    }

    private fun submittingFeedback(): StudyAnswerFeedbackState =
        StudyAnswerFeedbackState("private-session-token").apply {
            begin(StudyAnswerOutcome.CORRECT, "private user answer")
        }

    private fun route(
        token: String,
        phase: StudySessionPhase,
        version: Long,
        feedback: StudyAnswerFeedbackState,
    ): StudyRouteSnapshot = StudyRouteSnapshot(
        version = StudyRouteVersion(version),
        sessionGeneration = StudySessionGeneration(2L),
        sessionToken = token,
        phase = phase,
        feedback = feedback.snapshot(),
        progress = StudySessionProgressUiState(
            targetCount = 7,
            completedCount = 6,
            activeTask = phase != StudySessionPhase.ADVANCING,
        ),
    )
}
