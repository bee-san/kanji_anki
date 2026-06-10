package dev.bee.kanjianki.core

import java.util.Locale

object StudyReviewButtonCopy {
    private const val JAPANESE_LANGUAGE = "ja"
    private const val LABEL_AGAIN = "Again"
    private const val LABEL_GOOD = "Good"
    private const val DESCRIPTION_AGAIN = "Again: show this card again sooner"
    private const val DESCRIPTION_GOOD = "Good: keep the next review on schedule"

    @JvmStatic
    fun againLabel(): String = localizedText(LABEL_AGAIN, "もう一度")

    @JvmStatic
    fun goodLabel(): String = localizedText(LABEL_GOOD, "できた")

    @JvmStatic
    fun againContentDescription(): String =
        localizedText(DESCRIPTION_AGAIN, "もう一度: このカードを早めに再表示する")

    @JvmStatic
    fun goodContentDescription(): String =
        localizedText(DESCRIPTION_GOOD, "できた: 次回の復習を予定どおりに保つ")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
