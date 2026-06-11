package dev.bee.kanjianki.core

import java.util.Locale

object NewCardSortSettingsPolicy {
    const val SAVED_MESSAGE: String = "New card sort saved."

    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun saveRequest(selectedMode: String?): SaveRequest {
        return SaveRequest(RecordsSyncModels.Settings.normalizeNewCardSortMode(selectedMode), savedMessage())
    }

    @JvmStatic
    fun savedMessage(): String = localizedText(SAVED_MESSAGE, "新規カードの並び順を保存しました。")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

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
