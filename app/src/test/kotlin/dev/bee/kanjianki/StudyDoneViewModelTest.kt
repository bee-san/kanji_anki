package dev.bee.kanjianki

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.bee.kanjianki.core.RecordsSchedulerModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudyDoneViewModelTest {
    @Test
    fun retainedStateSurvivesUiOwnerRecreationWithoutRetainingCallbacks() {
        val store = ViewModelStore()
        val savedState = SavedStateHandle()
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StudyDoneViewModel(savedState) as T
            }
        }
        val firstOwner = TestOwner(store)
        val first = ViewModelProvider(firstOwner, factory)[StudyDoneViewModel::class.java]
        val originalModel = doneModel()

        first.install(
            plan = null,
            model = originalModel,
            reason = StudyRouteCompletionReason.FOCUS_COMPLETE,
        )
        first.showDialog(initialCount = 4)
        first.updateDialogRequestText("3")
        first.cachedStudyMoreAvailability = 7

        val recreatedOwner = TestOwner(store)
        val recreated = ViewModelProvider(recreatedOwner, factory)[StudyDoneViewModel::class.java]
        val rebuilt = recreated.presentation!!.toScreenModel(
            studyMoreDialog = null,
            onStudyMore = Runnable {},
            onContinueAll = Runnable {},
            onBackHome = Runnable {},
        )

        assertSame(first, recreated)
        assertEquals(4, recreated.dialogInitialCount)
        assertEquals("3", recreated.dialogRequestText)
        assertEquals(7, recreated.cachedStudyMoreAvailability)
        assertEquals(StudyRouteCompletionReason.FOCUS_COMPLETE, recreated.completionReason)
        assertEquals(originalModel.title, rebuilt.title)
        assertEquals(originalModel.availableStudyMoreNewCards, rebuilt.availableStudyMoreNewCards)
        assertNotSame(originalModel.onStudyMore, rebuilt.onStudyMore)
        store.clear()
    }

    @Test
    fun callbackFreeStateSurvivesSavedStateHandleReconstruction() {
        val savedState = SavedStateHandle()
        val first = StudyDoneViewModel(savedState)
        val plan = RecordsSchedulerModels.AdaptiveLoadPlan(
            true,
            80,
            7,
            0,
            listOf("裂", "列"),
            2,
            false,
            "Complete",
        )

        first.install(
            plan = plan,
            model = doneModel(),
            reason = StudyRouteCompletionReason.HARD_CAP,
        )
        first.showDialog(initialCount = 4)
        first.updateDialogRequestText("2")

        val savedValues = savedState.keys().associateWith { key -> savedState.get<Any?>(key) }
        val restoredSavedState = SavedStateHandle(savedValues)
        val restored = StudyDoneViewModel(restoredSavedState)

        assertEquals(StudyRouteCompletionReason.HARD_CAP, restored.completionReason)
        assertEquals("Done", restored.presentation?.title)
        assertEquals(listOf("2 cards"), restored.presentation?.summaryLines)
        assertEquals(4, restored.dialogInitialCount)
        assertEquals("2", restored.dialogRequestText)
        assertEquals(true, restored.renderedPlan?.autoMode)
        assertEquals(listOf("裂", "列"), restored.renderedPlan?.focusKanji)

        restored.clear()
        val clearedValues = restoredSavedState.keys()
            .associateWith { key -> restoredSavedState.get<Any?>(key) }
        val cleared = StudyDoneViewModel(SavedStateHandle(clearedValues))
        assertNull(cleared.presentation)
        assertNull(cleared.completionReason)
        assertNull(cleared.dialogInitialCount)
    }

    private fun doneModel() = StudyDoneScreenModel(
        modeLabel = "Practice",
        title = "Done",
        headline = null,
        body = "Finished",
        summaryLines = listOf("2 cards"),
        showDoneActions = true,
        availableStudyMoreNewCards = 7,
        showBackHome = false,
        backHomePrimary = false,
        onStudyMore = Runnable {},
        onContinueAll = Runnable {},
        onBackHome = Runnable {},
    )

    private class TestOwner(
        override val viewModelStore: ViewModelStore,
    ) : ViewModelStoreOwner
}
