package dev.bee.kanjianki.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

internal class AndroidDeviceSettingsStore(context: Context) : DeviceSettingsStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun contains(key: DeviceSettingKey<*>): Boolean =
        preferences.contains(key.storageName)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> read(key: DeviceSettingKey<T>): T? {
        val stored = preferences.all[key.storageName] ?: return null
        val matches = when (key.valueType) {
            DeviceSettingValueType.BOOLEAN -> stored is Boolean
            DeviceSettingValueType.INT -> stored is Int
            DeviceSettingValueType.LONG -> stored is Long
            DeviceSettingValueType.STRING -> stored is String
        }
        return if (matches) stored as T else null
    }

    @SuppressLint("ApplySharedPref")
    override fun edit(block: DeviceSettingsEditor.() -> Unit) {
        val editor = preferences.edit()
        AndroidEditor(editor).block()
        check(editor.commit()) { "Unable to persist device settings" }
    }

    private class AndroidEditor(
        private val editor: SharedPreferences.Editor,
    ) : DeviceSettingsEditor {
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
        }

        override fun remove(key: DeviceSettingKey<*>) {
            editor.remove(key.storageName)
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

    internal companion object {
        const val PREFERENCES_NAME = "kani_device_settings"
    }
}
