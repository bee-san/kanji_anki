package dev.bee.kanjianki.core

object SettingValuePolicy {
    @JvmStatic
    fun parseInt(value: String?, fallback: Int): Int {
        return try {
            value!!.toInt()
        } catch (_: NumberFormatException) {
            fallback
        }
    }

    @JvmStatic
    fun parseLong(value: String?, fallback: Long): Long {
        return try {
            value!!.toLong()
        } catch (_: NumberFormatException) {
            fallback
        }
    }

    @JvmStatic
    fun parseDouble(value: String?, fallback: Double): Double {
        return try {
            value!!.toDouble()
        } catch (_: NumberFormatException) {
            fallback
        }
    }
}
