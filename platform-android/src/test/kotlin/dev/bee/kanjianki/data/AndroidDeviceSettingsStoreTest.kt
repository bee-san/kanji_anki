package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.platform.DeviceSettingKeys
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDeviceSettingsStoreTest {
    private lateinit var context: Context
    private lateinit var store: AndroidDeviceSettingsStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AndroidDeviceSettingsStore.resetPersistenceFailureForTests()
        preferences().edit().clear().commit()
        store = AndroidDeviceSettingsStore(context)
    }

    @After
    fun tearDown() {
        AndroidDeviceSettingsStore.resetPersistenceFailureForTests()
        preferences().edit().clear().commit()
    }

    @Test
    fun typedValuesRoundTripAndRemoveAtomically() {
        store.edit {
            assertFalse(contains(DeviceSettingKeys.reminderEnabled))
            put(DeviceSettingKeys.reminderEnabled, true)
            put(DeviceSettingKeys.reminderHour, 9)
            put(DeviceSettingKeys.autoSyncNextRunAt, 123L)
            put(DeviceSettingKeys.providerEndpoint, "http://127.0.0.1:8765")
            assertEquals(true, read(DeviceSettingKeys.reminderEnabled))
        }

        assertTrue(store.contains(DeviceSettingKeys.reminderEnabled))
        assertEquals(true, store.read(DeviceSettingKeys.reminderEnabled))
        assertEquals(9, store.read(DeviceSettingKeys.reminderHour))
        assertEquals(123L, store.read(DeviceSettingKeys.autoSyncNextRunAt))
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

    @Test
    fun mismatchedPersistedTypeFailsOpenAsAbsent() {
        preferences().edit()
            .putString(DeviceSettingKeys.reminderHour.storageName, "nine")
            .commit()

        assertTrue(store.contains(DeviceSettingKeys.reminderHour))
        assertNull(store.read(DeviceSettingKeys.reminderHour))
    }

    @Test
    fun backgroundThreadCommitIsDurableBeforeReturning() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit {
                store.edit { put(DeviceSettingKeys.autoSyncNextRunAt, 123L) }
            }.get(5, TimeUnit.SECONDS)

            assertEquals(123L, store.read(DeviceSettingKeys.autoSyncNextRunAt))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun failedDurableCommitFailsClosedForTheRestOfTheProcess() {
        val failingStore = AndroidDeviceSettingsStore(context) { editor ->
            editor.commit()
            false
        }

        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            failingStore.edit { put(DeviceSettingKeys.reminderHour, 9) }
        }
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            AndroidDeviceSettingsStore(context).read(DeviceSettingKeys.reminderHour)
        }
    }

    private fun preferences() = context.getSharedPreferences(
        AndroidDeviceSettingsStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
}
