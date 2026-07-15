package dev.bee.kanjianki

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds Home screen state that survives configuration changes and provides
 * progressive loading with debounced refresh. The activity loads sections
 * via [refresh] and the Compose layer observes [uiState].
 *
 * This is the proof-of-concept pattern (Goal 137). Full migration of all
 * home model building from [MainActivityHome.buildHomeScreenModel] into this
 * ViewModel is deferred until the coroutine IO path is validated in production.
 */
internal class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var lastRefreshAtMillis = 0L

    fun refresh(ioDispatcher: CoroutineDispatcher, loader: suspend () -> HomeScreenModel) {
        val now = System.currentTimeMillis()
        if (now - lastRefreshAtMillis < DEBOUNCE_MS && refreshJob?.isActive == true) {
            return
        }
        lastRefreshAtMillis = now
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val model = withContext(ioDispatcher) { loader() }
            _uiState.value = HomeUiState(loading = false, model = model)
        }
    }

    companion object {
        const val DEBOUNCE_MS = 500L
    }
}

data class HomeUiState(
    val loading: Boolean = false,
    val model: HomeScreenModel? = null,
)
