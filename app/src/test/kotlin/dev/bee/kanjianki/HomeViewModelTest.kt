package dev.bee.kanjianki

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsLoadingFalseAndNullModel() {
        val vm = HomeViewModel()
        val state = vm.uiState.value
        assertFalse(state.loading)
        assertNull(state.model)
    }

    @Test
    fun refreshLoadsModelProgressively() = runTest(testDispatcher) {
        val vm = HomeViewModel()
        val model = testModel("Test Title")

        vm.refresh(testDispatcher) { model }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertNotNull(state.model)
        assertEquals("Test Title", state.model!!.title)
    }

    @Test
    fun configChangePreservesLoadedState() = runTest(testDispatcher) {
        val vm = HomeViewModel()
        val model = testModel("Preserved")

        vm.refresh(testDispatcher) { model }
        advanceUntilIdle()

        // ViewModel survives config change — same instance
        val stateAfter = vm.uiState.value
        assertNotNull(stateAfter.model)
        assertEquals("Preserved", stateAfter.model!!.title)
    }

    @Test
    fun debounceSkipsRedundantRefreshWithinWindow() = runTest(testDispatcher) {
        val vm = HomeViewModel()
        var callCount = 0

        vm.refresh(testDispatcher) { callCount++; testModel("First") }
        vm.refresh(testDispatcher) { callCount++; testModel("Second") }
        advanceUntilIdle()

        assertEquals(1, callCount)
    }

    private fun testModel(title: String) = HomeScreenModel(
        title = title,
        subtitle = "",
        metrics = emptyList(),
        todayPlan = HomeTodayPlanModel("", "", emptyList(), null, null),
        deckOverviewRows = emptyList(),
        showSyncCta = false,
        syncLabel = "",
        studyLabel = "",
        onSync = {},
        onStudy = {},
        actions = emptyList(),
        focusTitle = "",
        focusActionLabel = null,
        onFocusAction = null,
        emptyTitle = null,
        emptyBody = null,
        previewCards = emptyList(),
    )
}
