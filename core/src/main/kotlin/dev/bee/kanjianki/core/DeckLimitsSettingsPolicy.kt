package dev.bee.kanjianki.core

object DeckLimitsSettingsPolicy {
    const val MAX_NEW_PER_DAY = 999

    // Active queue cap bounds. The default (24) is a calm working set; power
    // users can widen it, but a floor keeps at least a handful of items active
    // and a ceiling keeps the queue from becoming a full second deck.
    const val MIN_ACTIVE_QUEUE_CAP = 8
    const val MAX_ACTIVE_QUEUE_CAP = 200

    @JvmStatic
    fun normalizeNewPerDay(value: Int): Int {
        return value.coerceIn(0, MAX_NEW_PER_DAY)
    }

    @JvmStatic
    fun normalizeActiveQueueCap(value: Int): Int {
        return value.coerceIn(MIN_ACTIVE_QUEUE_CAP, MAX_ACTIVE_QUEUE_CAP)
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

    @JvmStatic
    fun saveActiveQueueCap(text: String?): SaveRequest {
        return saveActiveQueueCap(text, RecordsSyncModels.Settings.kikuDefaults().activeQueueCap)
    }

    @JvmStatic
    fun saveActiveQueueCap(text: String?, fallback: Int): SaveRequest {
        val parsed = text?.trim()?.toIntOrNull() ?: fallback
        val normalized = normalizeActiveQueueCap(parsed)
        return SaveRequest(normalized, "Active queue cap saved: $normalized")
    }

    data class SaveRequest(
        @JvmField val newPerDay: Int,
        @JvmField val message: String,
    )
}
