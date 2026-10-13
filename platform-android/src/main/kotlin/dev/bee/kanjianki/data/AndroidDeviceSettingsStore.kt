package dev.bee.kanjianki.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import dev.bee.kanjianki.platform.DeviceSettingKey
import dev.bee.kanjianki.platform.DeviceSettingValueType
import dev.bee.kanjianki.platform.DeviceSettingsEditor
import dev.bee.kanjianki.platform.DeviceSettingsReader
import dev.bee.kanjianki.platform.DeviceSettingsStore
import java.util.WeakHashMap

class AndroidDeviceSettingsStore(
    context: Context,
    private val commitEditor: (SharedPreferences.Editor) -> Boolean = SharedPreferences.Editor::commit,
) : DeviceSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val durabilityState = synchronized(EDIT_LOCK) {
        DURABILITY_STATES.getOrPut(preferences) { DurabilityState() }
    }

    override fun contains(key: DeviceSettingKey<*>): Boolean =
        healthy { preferences.contains(key.storageName) }

    override fun <T : Any> read(key: DeviceSettingKey<T>): T? {
        return healthy { readValue(preferences.all, key) }
    }

    override fun snapshot(): DeviceSettingsReader =
        healthy { AndroidSnapshot(HashMap(preferences.all)) }

    @SuppressLint("ApplySharedPref")
    override fun edit(block: DeviceSettingsEditor.() -> Unit) {
        synchronized(EDIT_LOCK) {
            checkHealthy()
            val editor = preferences.edit()
            val deviceEditor = AndroidEditor(editor, HashMap(preferences.all))
            deviceEditor.block()
            if (deviceEditor.changed) {
                if (!commitEditor(editor)) {
                    val failure = IllegalStateException(
                        "Unable to persist device settings; restart required",
                    )
                    durabilityState.failure = failure
                    throw failure
                }
            }
        }
    }

    private class AndroidSnapshot(
        private val values: Map<String, *>,
    ) : DeviceSettingsReader {
        override fun contains(key: DeviceSettingKey<*>): Boolean =
            values.containsKey(key.storageName)

        override fun <T : Any> read(key: DeviceSettingKey<T>): T? =
            readValue(values, key)
    }

    private class AndroidEditor(
        private val editor: SharedPreferences.Editor,
        private val values: MutableMap<String, Any?>,
    ) : DeviceSettingsEditor {
        var changed = false
            private set

        override fun contains(key: DeviceSettingKey<*>): Boolean =
            values.containsKey(key.storageName)

        override fun <T : Any> read(key: DeviceSettingKey<T>): T? =
            readValue(values, key)

        override fun <T : Any> put(key: DeviceSettingKey<T>, value: T) {
            when (key.valueType) {
                DeviceSettingValueType.BOOLEAN ->
                    editor.putBoolean(key.storageName, requireType<Boolean>(key, value))
                DeviceSettingValueType.INT ->
                    editor.putInt(key.storageName, requireType<Int>(key, value))
                DeviceSettingValueType.LONG ->
                    editor.putLong(key.storageName, requireType<Long>(key, value))
                DeviceSettingValueType.STRING ->
                    editor.putString(key.storageName, requireType<String>(key, value))
            }
            values[key.storageName] = value
            changed = true
        }

        override fun remove(key: DeviceSettingKey<*>) {
            editor.remove(key.storageName)
            values.remove(key.storageName)
            changed = true
        }

        private inline fun <reified T : Any> requireType(
            key: DeviceSettingKey<*>,
            value: Any,
        ): T {
            require(value is T) {
                "Value for ${key.storageName} does not match ${key.valueType}"
            }
            return value
        }
    }

    companion object {
        const val PREFERENCES_NAME = "kani_device_settings"
        private val EDIT_LOCK = Any()
        private val DURABILITY_STATES = WeakHashMap<SharedPreferences, DurabilityState>()

        private class DurabilityState {
            @Volatile
            var failure: IllegalStateException? = null
        }

        private inline fun <T> AndroidDeviceSettingsStore.healthy(block: () -> T): T {
            checkHealthy()
            return block()
        }

        private fun AndroidDeviceSettingsStore.checkHealthy() {
            durabilityState.failure?.let { failure ->
                throw IllegalStateException(
                    "Device settings durability previously failed; restart required",
                    failure,
                )
            }
        }

        internal fun resetPersistenceFailureForTests() {
            synchronized(EDIT_LOCK) {
                DURABILITY_STATES.clear()
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T : Any> readValue(
            values: Map<String, *>,
            key: DeviceSettingKey<T>,
        ): T? {
            val stored = values[key.storageName] ?: return null
            val matches = when (key.valueType) {
                DeviceSettingValueType.BOOLEAN -> stored is Boolean
                DeviceSettingValueType.INT -> stored is Int
                DeviceSettingValueType.LONG -> stored is Long
                DeviceSettingValueType.STRING -> stored is String
            }
            return if (matches) stored as T else null
        }
    }
}
