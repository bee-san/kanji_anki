package dev.bee.kanjianki

import androidx.lifecycle.ViewModel
import dev.bee.kanjianki.core.RecordsSchedulerModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds study session state that must survive configuration changes. The
 * activity delegates session progress tracking here; the ViewModel outlives
 * rotation/theme changes. Compose layers observe [uiState] with
 * `collectAsState()`.
 *
 * This is the proof-of-concept pattern (Goal 136). Full migration of all
 * mutable study fields from [MainActivityStudy] is deferred until the pattern
 * is validated in production.
 */
internal class StudySessionViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(StudySessionUiState())
    val uiState: StateFlow<StudySessionUiState> = _uiState.asStateFlow()

    fun startSession(targetCount: Int) {
        _uiState.value = StudySessionUiState(
            sessionActive = true,
            targetCount = targetCount,
            completedCount = 0,
            correctCount = 0,
        )
    }

    fun recordAnswer(correct: Boolean) {
        val current = _uiState.value
        _uiState.value = current.copy(
            completedCount = current.completedCount + 1,
            correctCount = current.correctCount + if (correct) 1 else 0,
        )
    }

    fun updateCurrentItem(item: RecordsSchedulerModels.StudySession?) {
        _uiState.value = _uiState.value.copy(currentItem = item)
    }

    fun endSession() {
        _uiState.value = _uiState.value.copy(sessionActive = false)
    }

    fun reset() {
        _uiState.value = StudySessionUiState()
    }
}

data class StudySessionUiState(
    val sessionActive: Boolean = false,
    val targetCount: Int = 0,
    val completedCount: Int = 0,
    val correctCount: Int = 0,
    val currentItem: RecordsSchedulerModels.StudySession? = null,
)
