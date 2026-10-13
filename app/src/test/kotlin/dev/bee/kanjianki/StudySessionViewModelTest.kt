package dev.bee.kanjianki

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.bee.kanjianki.core.RecordsSchedulerModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StudySessionViewModelTest {
    @Test
    fun viewModelStoreRetainsTheRealSessionAcrossConfigurationOwnerReplacement() {
        val store = ViewModelStore()
        val firstOwner = TestOwner(store)
        val first = ViewModelProvider(firstOwner)[StudySessionViewModel::class.java]
        val mounted = session("retained-token")
        first.mountSession(mounted)
        first.tracker.setTargetCount(8)

        val replacementOwner = TestOwner(store)
        val replacement = ViewModelProvider(replacementOwner)[StudySessionViewModel::class.java]

        assertSame(first, replacement)
        assertSame(mounted, replacement.uiState.value.currentSession)
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
