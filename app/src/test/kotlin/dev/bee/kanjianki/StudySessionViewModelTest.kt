package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudySessionViewModelTest {

    @Test
    fun initialStateIsInactive() {
        val vm = StudySessionViewModel()
        val state = vm.uiState.value
        assertFalse(state.sessionActive)
        assertEquals(0, state.targetCount)
        assertEquals(0, state.completedCount)
        assertEquals(0, state.correctCount)
        assertNull(state.currentItem)
    }

    @Test
    fun startSessionSetsActiveAndTarget() {
        val vm = StudySessionViewModel()
        vm.startSession(10)
        val state = vm.uiState.value
        assertTrue(state.sessionActive)
        assertEquals(10, state.targetCount)
        assertEquals(0, state.completedCount)
    }

    @Test
    fun recordCorrectAnswerIncrementsBoth() {
        val vm = StudySessionViewModel()
        vm.startSession(5)
        vm.recordAnswer(correct = true)
        val state = vm.uiState.value
        assertEquals(1, state.completedCount)
        assertEquals(1, state.correctCount)
    }

    @Test
    fun recordIncorrectAnswerIncrementsOnlyCompleted() {
        val vm = StudySessionViewModel()
        vm.startSession(5)
        vm.recordAnswer(correct = false)
        val state = vm.uiState.value
        assertEquals(1, state.completedCount)
        assertEquals(0, state.correctCount)
    }

    @Test
    fun multipleAnswersAccumulate() {
        val vm = StudySessionViewModel()
        vm.startSession(10)
        vm.recordAnswer(correct = true)
        vm.recordAnswer(correct = true)
        vm.recordAnswer(correct = false)
        val state = vm.uiState.value
        assertEquals(3, state.completedCount)
        assertEquals(2, state.correctCount)
    }

    @Test
    fun endSessionPreservesCountsButDeactivates() {
        val vm = StudySessionViewModel()
        vm.startSession(5)
        vm.recordAnswer(correct = true)
        vm.endSession()
        val state = vm.uiState.value
        assertFalse(state.sessionActive)
        assertEquals(1, state.completedCount)
        assertEquals(1, state.correctCount)
    }

    @Test
    fun resetClearsEverything() {
        val vm = StudySessionViewModel()
        vm.startSession(5)
        vm.recordAnswer(correct = true)
        vm.reset()
        val state = vm.uiState.value
        assertFalse(state.sessionActive)
        assertEquals(0, state.targetCount)
        assertEquals(0, state.completedCount)
    }

    @Test
    fun configChangePreservesState() {
        val vm = StudySessionViewModel()
        vm.startSession(8)
        vm.recordAnswer(correct = true)
        vm.recordAnswer(correct = false)

        // ViewModel survives config change — same instance, state intact
        val stateAfter = vm.uiState.value
        assertTrue(stateAfter.sessionActive)
        assertEquals(8, stateAfter.targetCount)
        assertEquals(2, stateAfter.completedCount)
        assertEquals(1, stateAfter.correctCount)
    }
}
