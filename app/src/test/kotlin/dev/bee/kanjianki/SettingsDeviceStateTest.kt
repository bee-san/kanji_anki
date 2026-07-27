package dev.bee.kanjianki

import dev.bee.kanjianki.platform.DeviceSettingKey
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.updatecore.AutoUpdateStatusPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDeviceStateTest {
    @Test
    fun emptyDeviceStoreUsesTheEstablishedFreshInstallDefaults() {
        val state = EmptyReader.settingsDeviceState()

        assertFalse(state.reminder.enabled)
        assertFalse(state.autoSync.configured)
        assertTrue(state.autoUpdate.enabled)
        assertEquals(AutoUpdateStatusPolicy.DEFAULT_LAST_RESULT, state.autoUpdate.lastResult)
        assertFalse(state.debugLogEnabled)
    }

    private object EmptyReader : DeviceSettingsReader {
        override fun contains(key: DeviceSettingKey<*>): Boolean = false

        override fun <T : Any> read(key: DeviceSettingKey<T>): T? = null
    }
}
