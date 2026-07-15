package dev.bee.kanjianki

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.bee.kanjianki.core.RecordsSchedulerModels
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySessionViewModelTest {
    @Test
    fun initialStateIsIdleAndEmpty() {
        val state = StudySessionViewModel().uiState.value

        assertEquals(StudySessionPhase.IDLE, state.phase)
        assertFalse(state.sessionActive)
        assertEquals(StudySessionProgressUiState(), state.progress)
    }

    @Test
    fun mountedFeedbackTransitionsArePublishedAsImmutableSnapshots() {
        val viewModel = StudySessionViewModel()
        val session = session("token-1")
        viewModel.mountSession(session)

        val feedback = viewModel.feedbackFor(session.token)
        assertEquals(StudySessionPhase.ACTIVE, viewModel.uiState.value.phase)
        assertTrue(feedback.begin(StudyAnswerOutcome.CORRECT, "good"))
        assertEquals(StudySessionPhase.SUBMITTING, viewModel.uiState.value.phase)
        assertEquals("good", viewModel.uiState.value.feedback?.selectedAnswer)

        assertTrue(feedback.markApplied(session.token))
        assertEquals(StudySessionPhase.FEEDBACK, viewModel.uiState.value.phase)
        assertTrue(feedback.tryContinue())
        assertEquals(StudySessionPhase.ADVANCING, viewModel.uiState.value.phase)
    }

    @Test
    fun trackerMutationsPublishRealProgress() {
        val viewModel = StudySessionViewModel()

        viewModel.tracker.setTargetCount(3)
        viewModel.tracker.registerTaskShown("kanji_meaning:裂")
        viewModel.tracker.markTaskCompleted("kanji_meaning:裂")

        assertEquals(3, viewModel.uiState.value.progress.targetCount)
        assertEquals(1, viewModel.uiState.value.progress.completedCount)
    }

    @Test
    fun staleFeedbackDoesNotChangeMountedSessionPhase() {
        val state = StudySessionUiState(
            currentSession = session("new-token"),
            phase = StudySessionPhase.ACTIVE,
        )
        val stale = StudyAnswerFeedbackSnapshot(
            sessionToken = "old-token",
            phase = StudyAnswerFeedbackPhase.APPLIED,
            outcome = StudyAnswerOutcome.CORRECT,
            selectedAnswer = "good",
        )

        val reduced = StudySessionReducer.reduce(state, StudySessionEvent.FeedbackChanged(stale))

        assertEquals(StudySessionPhase.ACTIVE, reduced.phase)
        assertEquals(stale, reduced.feedback)
    }

    @Test
    fun loadingAndCompleteAreExplicitPresentationStates() {
        val viewModel = StudySessionViewModel()
        viewModel.mountSession(session("token"))

        viewModel.showLoading()
        assertEquals(StudySessionPhase.LOADING, viewModel.uiState.value.phase)
        viewModel.showComplete()
        assertEquals(StudySessionPhase.COMPLETE, viewModel.uiState.value.phase)
    }

    @Test
    fun autoContinueIsDeliveredAsOneShotEffect() = runTest {
        val viewModel = StudySessionViewModel()
        val effect = async { viewModel.effects.first() }

        viewModel.requestAutoContinue("token")

        assertEquals(StudySessionEffect.AutoContinue("token"), effect.await())
    }

    @Test
    fun viewModelStoreRetainsTheRealSessionAcrossConfigurationOwnerReplacement() {
        val store = ViewModelStore()
        val firstOwner = TestOwner(store)
        val first = ViewModelProvider(firstOwner)[StudySessionViewModel::class.java]
        val session = session("retained-token")
        first.mountSession(session)
        first.tracker.setTargetCount(8)

        val replacementOwner = TestOwner(store)
        val replacement = ViewModelProvider(replacementOwner)[StudySessionViewModel::class.java]

        assertSame(first, replacement)
        assertSame(session, replacement.uiState.value.currentSession)
        assertEquals(8, replacement.uiState.value.progress.targetCount)
        store.clear()
    }

    private class TestOwner(
        override val viewModelStore: ViewModelStore,
    ) : ViewModelStoreOwner

    private fun session(token: String): RecordsSchedulerModels.StudySession =
        RecordsSchedulerModels.StudySession(
            item = null,
            row = null,
            token = token,
            taskType = "kanji_meaning",
            writingRequired = false,
            prompt = "meaning",
        )
}
