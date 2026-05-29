package dev.bee.kanjianki.core

object DeckLimitsSettingsPolicy {
    const val MAX_NEW_PER_DAY = 999

    @JvmStatic
    fun normalizeNewPerDay(value: Int): Int {
        return value.coerceIn(0, MAX_NEW_PER_DAY)
    }

    @JvmStatic
    fun saveNewPerDay(text: String?): SaveRequest {
        return saveNewPerDay(text, RecordsSyncModels.Settings.kikuDefaults().newPerDay)
    }

    @JvmStatic
    fun saveNewPerDay(text: String?, fallback: Int): SaveRequest {
        val parsed = text?.trim()?.toIntOrNull() ?: fallback
        val normalized = normalizeNewPerDay(parsed)
        return SaveRequest(normalized, "New cards/day saved: $normalized")
    }

    data class SaveRequest(
        @JvmField val newPerDay: Int,
        @JvmField val message: String,
    )
}
