package dev.bee.kanjianki.core

object NewCardSortSettingsPolicy {
    const val SAVED_MESSAGE: String = "New card sort saved."

    @JvmStatic
    fun saveRequest(selectedMode: String?): SaveRequest {
        return SaveRequest(RecordsSyncModels.Settings.normalizeNewCardSortMode(selectedMode), SAVED_MESSAGE)
    }

    class SaveRequest private constructor(
        @JvmField val mode: String,
        @JvmField val message: String,
    ) {
        companion object {
            operator fun invoke(mode: String, message: String): SaveRequest {
                return SaveRequest(mode, message)
            }
        }
    }
}
