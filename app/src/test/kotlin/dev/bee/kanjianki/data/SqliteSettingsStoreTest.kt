package dev.bee.kanjianki.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SqliteSettingsStoreTest {
    @Test
    fun missingNumericValuesReturnFallbacks() {
        val repository = SqliteSettingsStore(FakeSettingsStorage())

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
        val repository = SqliteSettingsStore(storage)

        assertEquals(11, repository.getInt("int", 11))
        assertEquals(12L, repository.getLong("long", 12L))
        assertEquals(0.87, repository.getDouble("double", 0.87), 0.000001)
    }

    @Test
    fun stringFallbackUsesOnlyMissingStorageValues() {
        val repository = SqliteSettingsStore(FakeSettingsStorage("empty" to ""))

        assertEquals("fallback", repository.getString("missing", "fallback"))
        assertEquals("", repository.getString("empty", "fallback"))
        assertNull(repository.getString("missing", null))
    }

    @Test
    fun writesUseExistingStringFormats() {
        val storage = FakeSettingsStorage()
        val repository = SqliteSettingsStore(storage)

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

    @Test
    fun bulkStorageLoadsOneSnapshotForPresentAndMissingKeys() {
        val storage = BulkSettingsStorage("present" to "17")
        val repository = SqliteSettingsStore(storage)

        assertEquals(17, repository.getInt("present", 0))
        assertEquals(9, repository.getInt("missing", 9))
        assertEquals("fallback", repository.getString("also-missing", "fallback"))

        assertEquals(1, storage.getAllCalls)
        assertEquals(0, storage.getCalls)
    }

    @Test
    fun writesInvalidateThisAndOtherRepositorySnapshots() {
        val storage = BulkSettingsStorage("value" to "old")
        val first = SqliteSettingsStore(storage)
        val second = SqliteSettingsStore(storage)

        assertEquals("old", first.getString("value", null))
        assertEquals(1, storage.getAllCalls)

        second.putString("value", "new")

        assertEquals("new", first.getString("value", null))
        assertEquals(2, storage.getAllCalls)
    }

    @Test
    fun explicitInvalidationPublishesDirectTransactionalWrites() {
        val storage = BulkSettingsStorage("value" to "before")
        val repository = SqliteSettingsStore(storage)
        assertEquals("before", repository.getString("value", null))

        storage.values["value"] = "after"
        repository.invalidate()

        assertEquals("after", repository.getString("value", null))
        assertEquals(2, storage.getAllCalls)
    }

    @Test
    fun transactionOwnerBypassesSharedSnapshotForReadAfterWriteAndRollback() {
        val storage = TransactionalBulkSettingsStorage("value" to "committed")
        val repository = SqliteSettingsStore(storage)

        assertEquals("committed", repository.getString("value", null))
        assertEquals(1, storage.getAllCalls)

        storage.beginTransaction()
        repository.putString("value", "uncommitted")

        assertEquals("uncommitted", repository.getString("value", null))
        assertEquals("transaction reads use the single-key storage view", 1, storage.getCalls)
        assertEquals("an uncommitted map is never bulk-cached", 1, storage.getAllCalls)

        storage.rollbackTransaction()

        assertEquals("committed", repository.getString("value", null))
        assertEquals(2, storage.getAllCalls)
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

    private class BulkSettingsStorage(
        vararg entries: Pair<String, String>,
    ) : SettingsStorage {
        val values = entries.toMap().toMutableMap()
        var getCalls = 0
        var getAllCalls = 0

        override fun get(key: String?): String? {
            getCalls += 1
            return values[key]
        }

        override fun getAll(): Map<String, String> {
            getAllCalls += 1
            return LinkedHashMap(values)
        }

        override fun put(key: String?, value: String?) {
            if (key != null) {
                values[key] = value.orEmpty()
            }
        }
    }

    private class TransactionalBulkSettingsStorage(
        vararg entries: Pair<String, String>,
    ) : SettingsStorage {
        private val committedValues = entries.toMap().toMutableMap()
        private var transactionValues: MutableMap<String, String>? = null
        var getCalls = 0
        var getAllCalls = 0

        fun beginTransaction() {
            check(transactionValues == null)
            transactionValues = committedValues.toMutableMap()
        }

        fun rollbackTransaction() {
            check(transactionValues != null)
            transactionValues = null
        }

        override fun isTransactionOwner(): Boolean = transactionValues != null

        override fun get(key: String?): String? {
            getCalls += 1
            return (transactionValues ?: committedValues)[key]
        }

        override fun getAll(): Map<String, String> {
            getAllCalls += 1
            return LinkedHashMap(transactionValues ?: committedValues)
        }

        override fun put(key: String?, value: String?) {
            if (key == null) {
                return
            }
            (transactionValues ?: committedValues)[key] = value.orEmpty()
        }
    }
}
