package dev.bee.kanjianki

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowseSelectionWriteViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun queuedWriteSurvivesUiOwnerRecreationAndReplaysCompletion() = runTest(dispatcher) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val persisted = mutableListOf<BrowseSelectionMutation>()
        val store = ViewModelStore()
        val factory = browseSelectionFactory(application) { _, mutation ->
            persisted += mutation
        }
        val beforeRotation = TestOwner(store)
        val first = ViewModelProvider(beforeRotation, factory)[BrowseSelectionWriteViewModel::class.java]
        val route = HomeRouteRestoration.browse("裂", onlySimilarKanji = false, allKanjiScope = false)
        val mutation = BrowseSelectionMutation.Single("裂", suspended = true, changedAtMillis = 123L)

        assertTrue(first.submit(route, mutation))
        val afterRotation = TestOwner(store)
        val recreated = ViewModelProvider(afterRotation, factory)[BrowseSelectionWriteViewModel::class.java]
        assertSame(first, recreated)

        advanceUntilIdle()

        assertEquals(listOf(mutation), persisted)
        assertEquals(route, recreated.latestCompletion.value?.browseRoute)
        assertEquals(1L, recreated.latestCompletion.value?.writeId)
        store.clear()
    }

    @Test
    fun writesRemainSerializedInSubmissionOrder() = runTest(dispatcher) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val persisted = mutableListOf<BrowseSelectionMutation>()
        val store = ViewModelStore()
        val owner = TestOwner(store)
        val viewModel = ViewModelProvider(
            owner,
            browseSelectionFactory(application) { _, mutation -> persisted += mutation },
        )[BrowseSelectionWriteViewModel::class.java]
        val route = HomeRouteRestoration.browse("", onlySimilarKanji = false, allKanjiScope = false)
        val first = BrowseSelectionMutation.Single("裂", suspended = true, changedAtMillis = 1L)
        val second = BrowseSelectionMutation.Bulk(
            kanji = listOf("裂", "列"),
            suspended = false,
            changedAtMillis = 2L,
        )

        assertTrue(viewModel.submit(route, first))
        assertTrue(viewModel.submit(route, second))
        advanceUntilIdle()

        assertEquals(listOf(first, second), persisted)
        assertEquals(2L, viewModel.latestCompletion.value?.writeId)
        store.clear()
    }

    @Test
    fun browseDraftSurvivesUiOwnerRecreationAndResetsForAnotherRoute() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = ViewModelStore()
        val factory = browseSelectionFactory(application) { _, _ -> }
        val first = ViewModelProvider(
            TestOwner(store),
            factory,
        )[BrowseSelectionWriteViewModel::class.java]
        val route = HomeRouteRestoration.browse("裂", false, false)
        val otherRoute = HomeRouteRestoration.browse("語", false, false)

        assertEquals("裂", first.draftFor(route, "裂"))
        first.updateDraft(route, "draft")

        val recreated = ViewModelProvider(
            TestOwner(store),
            factory,
        )[BrowseSelectionWriteViewModel::class.java]

        assertSame(first, recreated)
        assertEquals("draft", recreated.draftFor(route, "裂"))
        assertEquals("語", recreated.draftFor(otherRoute, "語"))
        store.clear()
    }

    private fun browseSelectionFactory(
        application: Application,
        persist: (android.content.Context, BrowseSelectionMutation) -> Unit,
    ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return BrowseSelectionWriteViewModel(application, dispatcher, persist) as T
        }
    }

    private class TestOwner(
        override val viewModelStore: ViewModelStore,
    ) : ViewModelStoreOwner
}
