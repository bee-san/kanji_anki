package dev.bee.kanjianki.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSettingsStoreTest {
    @Test
    fun typedKeysCoverHostLocalSettingFamiliesWithoutSecretValues() {
        assertEquals(DeviceSettingValueType.BOOLEAN, DeviceSettingKeys.reminderEnabled.valueType)
        assertEquals(DeviceSettingValueType.INT, DeviceSettingKeys.windowWidth.valueType)
        assertEquals(DeviceSettingValueType.LONG, DeviceSettingKeys.autoSyncNextRunAt.valueType)
        assertEquals(DeviceSettingValueType.STRING, DeviceSettingKeys.providerAuthReference.valueType)
        assertEquals(DeviceSettingValueType.BOOLEAN, DeviceSettingKeys.betaUpdatesEnabled.valueType)
        assertEquals(
            DeviceSettingValueType.BOOLEAN,
            DeviceSettingKeys.flashcardSwipeGestureEnabled.valueType,
        )
        assertTrue(DeviceSettingKeys.providerAuthReference.storageName.endsWith("_reference"))
        assertFalse(DeviceSettingKeys.providerAuthReference.storageName.contains("api_key"))
    }

    @Test
    fun storeContractSupportsAtomicTypedEditsAndRemoval() {
        val store = InMemoryDeviceSettingsStore()

        store.edit {
            put(DeviceSettingKeys.reminderEnabled, true)
            put(DeviceSettingKeys.reminderHour, 8)
            put(DeviceSettingKeys.autoSyncNextRunAt, 42L)
            put(DeviceSettingKeys.providerEndpoint, "http://127.0.0.1:8765")
        }

        assertTrue(store.contains(DeviceSettingKeys.reminderEnabled))
        assertEquals(true, store.read(DeviceSettingKeys.reminderEnabled))
        assertEquals(8, store.read(DeviceSettingKeys.reminderHour))
        assertEquals(42L, store.read(DeviceSettingKeys.autoSyncNextRunAt))
        assertEquals(
            "http://127.0.0.1:8765",
            store.read(DeviceSettingKeys.providerEndpoint),
        )
        val snapshot = store.snapshot()

        store.edit { remove(DeviceSettingKeys.reminderEnabled) }

        assertFalse(store.contains(DeviceSettingKeys.reminderEnabled))
        assertNull(store.read(DeviceSettingKeys.reminderEnabled))
        assertEquals(true, snapshot.read(DeviceSettingKeys.reminderEnabled))
    }

    private class InMemoryDeviceSettingsStore : DeviceSettingsStore {
        private val values = linkedMapOf<String, Any>()

        override fun contains(key: DeviceSettingKey<*>): Boolean =
            values.containsKey(key.storageName)

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> read(key: DeviceSettingKey<T>): T? =
            values[key.storageName] as T?

        override fun snapshot(): DeviceSettingsReader {
            val captured = LinkedHashMap(values)
            return object : DeviceSettingsReader {
                override fun contains(key: DeviceSettingKey<*>): Boolean =
                    captured.containsKey(key.storageName)

                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> read(key: DeviceSettingKey<T>): T? =
                    captured[key.storageName] as T?
            }
        }

        override fun edit(block: DeviceSettingsEditor.() -> Unit) {
            val staged = LinkedHashMap(values)
            object : DeviceSettingsEditor {
                override fun contains(key: DeviceSettingKey<*>): Boolean =
                    staged.containsKey(key.storageName)

                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> read(key: DeviceSettingKey<T>): T? =
                    staged[key.storageName] as T?

                override fun <T : Any> put(key: DeviceSettingKey<T>, value: T) {
                    staged[key.storageName] = value
                }

                override fun remove(key: DeviceSettingKey<*>) {
                    staged.remove(key.storageName)
                }
            }.block()
            values.clear()
            values.putAll(staged)
        }
    }
}
