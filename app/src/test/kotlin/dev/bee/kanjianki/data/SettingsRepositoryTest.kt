package dev.bee.kanjianki.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun missingNumericValuesReturnFallbacks() {
        val repository = SettingsRepository(FakeSettingsStorage())

        assertEquals(7, repository.getInt("missing-int", 7))
        assertEquals(8L, repository.getLong("missing-long", 8L))
        assertEquals(0.92, repository.getDouble("missing-double", 0.92), 0.000001)
    }

    @Test
    fun malformedNumericValuesReturnFallbacks() {
        val storage = FakeSettingsStorage(
            "int" to "not an int",
            "long" to "not a long",
            "double" to "not a double",
        )
        val repository = SettingsRepository(storage)

        assertEquals(11, repository.getInt("int", 11))
        assertEquals(12L, repository.getLong("long", 12L))
        assertEquals(0.87, repository.getDouble("double", 0.87), 0.000001)
    }

    @Test
    fun stringFallbackUsesOnlyMissingStorageValues() {
        val repository = SettingsRepository(FakeSettingsStorage("empty" to ""))

        assertEquals("fallback", repository.getString("missing", "fallback"))
        assertEquals("", repository.getString("empty", "fallback"))
        assertNull(repository.getString("missing", null))
    }

    @Test
    fun writesUseExistingStringFormats() {
        val storage = FakeSettingsStorage()
        val repository = SettingsRepository(storage)

        repository.putInt("int", 42)
        repository.putLong("long", 123456789L)
        repository.putDouble("double", 0.9)
        repository.putString("string", "value")
        repository.putString("null-string", null)

        assertEquals("42", storage.values["int"])
        assertEquals("123456789", storage.values["long"])
        assertEquals("0.9000", storage.values["double"])
        assertEquals("value", storage.values["string"])
        assertEquals("", storage.values["null-string"])
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
