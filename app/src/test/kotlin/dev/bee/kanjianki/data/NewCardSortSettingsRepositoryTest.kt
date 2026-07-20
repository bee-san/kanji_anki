package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.NewCardSortSettingsPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.SyncSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class NewCardSortSettingsRepositoryTest {
    @Test
    fun saveNewCardSortModeNormalizesAndPersistsThroughStorageBoundary() {
        val storage = FakeSettingsStorage()
        val repository = NewCardSortSettingsRepository(SettingsRepository(storage))

        val request = repository.saveMode("not-a-mode")

        assertEquals(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE, storage.values[SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY])
        assertEquals(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE, request.mode)
        assertEquals(NewCardSortSettingsPolicy.SAVED_MESSAGE, request.message)
    }

    private class FakeSettingsStorage : SettingsStorage {
        val values: MutableMap<String?, String?> = LinkedHashMap()

        override fun get(key: String?): String? = values[key]

        override fun put(key: String?, value: String?) {
            values[key] = value
        }
    }
}
