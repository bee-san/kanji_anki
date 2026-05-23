package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.SettingValuePolicy
import java.util.Locale

internal class SettingsRepository(
    private val storage: SettingsStorage,
) {
    fun getInt(key: String?, fallback: Int): Int {
        return storage.get(key)?.let { SettingValuePolicy.parseInt(it, fallback) } ?: fallback
    }

    fun getLong(key: String?, fallback: Long): Long {
        return storage.get(key)?.let { SettingValuePolicy.parseLong(it, fallback) } ?: fallback
    }

    fun getString(key: String?, fallback: String?): String? {
        return storage.get(key) ?: fallback
    }

    fun getDouble(key: String?, fallback: Double): Double {
        return storage.get(key)?.let { SettingValuePolicy.parseDouble(it, fallback) } ?: fallback
    }

    fun putInt(key: String?, value: Int) {
        put(key, value.toString())
    }

    fun putLong(key: String?, value: Long) {
        put(key, value.toString())
    }

    fun putString(key: String?, value: String?) {
        put(key, value ?: "")
    }

    fun putDouble(key: String?, value: Double) {
        put(key, String.format(Locale.ROOT, "%.4f", value))
    }

    fun put(key: String?, value: String?) {
        storage.put(key, value)
    }
}
