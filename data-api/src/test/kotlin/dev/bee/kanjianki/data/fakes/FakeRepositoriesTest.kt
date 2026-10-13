package dev.bee.kanjianki.data.fakes

import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.data.StoredSyncState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRepositoriesTest {
    @Test
    fun focusedHandlersReturnConfiguredResultsAndCaptureCommands() = runTest {
        var onlySimilarKanji: Boolean? = null
        val home = FakeHomeRepository().apply {
            searchHandler = { _, onlySimilar ->
                onlySimilarKanji = onlySimilar
                StoreResult.ok(emptyList())
            }
        }
        val settings = FakeSettingsRepository()
        val sync = FakeSyncRepository().apply {
            storedStateHandler = {
                StoreResult.ok(StoredSyncState(false, emptyList(), emptySet(), emptyList(), null))
            }
        }

        assertTrue(home.searchInventory("痛").isOk())
        assertFalse(onlySimilarKanji ?: true)
        settings.save(SettingsSaveCommand.StudyAhead(15))
        assertEquals(
            listOf(SettingsSaveCommand.StudyAhead(15)),
            settings.saveCommands,
        )
        assertFalse(sync.loadStoredState().valueOrNull()?.hasCollectionMirror ?: true)
    }
}
