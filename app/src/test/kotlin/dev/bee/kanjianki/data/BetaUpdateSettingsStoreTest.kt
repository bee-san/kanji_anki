package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.platform.DeviceSettingKeys
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BetaUpdateSettingsStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearDeviceSettings()
    }

    @After
    fun tearDown() {
        clearDeviceSettings()
    }

    @Test
    fun betaUpdatesDefaultOffAndPersistWhenEnabled() {
        AndroidDeviceSettingsStore(context).let { store ->
            assertFalse(store.read(DeviceSettingKeys.betaUpdatesEnabled) ?: false)
            store.edit { put(DeviceSettingKeys.betaUpdatesEnabled, true) }
            assertTrue(store.read(DeviceSettingKeys.betaUpdatesEnabled) == true)
        }

        val reopened = AndroidDeviceSettingsStore(context)
        assertTrue(reopened.read(DeviceSettingKeys.betaUpdatesEnabled) == true)
    }

    private fun clearDeviceSettings() {
        context.getSharedPreferences(
            AndroidDeviceSettingsStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }
}
