package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.KaniThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class KaniThemeChoiceRepositoryTest {
    @Test
    fun missingThemeChoiceDefaultsToGirlypop() {
        val repository = KaniThemeChoiceRepository(SqliteSettingsStore(FakeSettingsStorage()))

        assertEquals(KaniThemeChoice.GIRLYPOP, repository.currentChoice())
    }

    @Test
    fun allChoicesRoundTripThroughStorage() {
        for (choice in KaniThemeChoice.entries) {
            val storage = FakeSettingsStorage()
            val repository = KaniThemeChoiceRepository(SqliteSettingsStore(storage))

            val saved = repository.saveChoice(choice)

            assertEquals(choice, saved)
            assertEquals(choice.storageKey, storage.values[KaniThemeChoice.SETTING_KEY])
            assertEquals(choice, repository.currentChoice())
        }
    }

    @Test
    fun invalidStoredThemeChoiceFallsBackToGirlypop() {
        val storage = FakeSettingsStorage(KaniThemeChoice.SETTING_KEY to "not-a-theme")
        val repository = KaniThemeChoiceRepository(SqliteSettingsStore(storage))

        assertEquals(KaniThemeChoice.GIRLYPOP, repository.currentChoice())
    }

    private class FakeSettingsStorage(
        vararg entries: Pair<String, String?>,
    ) : SettingsStorage {
        val values: MutableMap<String?, String?> = entries.toMap().toMutableMap()

        override fun get(key: String?): String? = values[key]

        override fun put(key: String?, value: String?) {
            values[key] = value
        }
    }
}
