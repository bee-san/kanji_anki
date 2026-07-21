package dev.bee.kanjianki.core

object SettingValuePolicy {
    @JvmStatic
    fun parseInt(value: String?, fallback: Int): Int {
        return value?.toIntOrNull() ?: fallback
    }

    @JvmStatic
    fun parseLong(value: String?, fallback: Long): Long {
        return value?.toLongOrNull() ?: fallback
    }

    @JvmStatic
    fun parseDouble(value: String?, fallback: Double): Double {
        return value?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: fallback
    }
}
