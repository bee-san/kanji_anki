package dev.bee.kanjianki.core

import java.util.Locale

object StudyReviewButtonCopy {
    private const val JAPANESE_LANGUAGE = "ja"
    private const val LABEL_REVEAL = "Reveal"
    private const val LABEL_AGAIN = "Again"
    private const val LABEL_GOOD = "Good"
    private const val DESCRIPTION_AGAIN = "Again: show this card again sooner"
    private const val DESCRIPTION_GOOD = "Good: keep the next review on schedule"

    @JvmStatic
    fun revealLabel(): String = localizedText(LABEL_REVEAL, "答えを見る")

    @JvmStatic
    fun againLabel(): String = localizedText(LABEL_AGAIN, "もう一度")

    @JvmStatic
    fun goodLabel(): String = localizedText(LABEL_GOOD, "できた")

    @JvmStatic
    fun undoLabel(): String = localizedText("Undo", "元に戻す")

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
