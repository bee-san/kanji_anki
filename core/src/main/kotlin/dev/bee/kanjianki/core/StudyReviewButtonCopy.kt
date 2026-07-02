package dev.bee.kanjianki.core

import java.util.Locale

object StudyReviewButtonCopy {
    private const val JAPANESE_LANGUAGE = "ja"
    private const val LABEL_REVEAL = "Reveal"

    // The study UI exposes Pass and Fail labels; the good/again wire format is
    // translated at the boundary (see AGENTS.md, Study Scheduler Notes).
    private const val LABEL_FAIL = "Fail"
    private const val LABEL_PASS = "Pass"
    private const val DESCRIPTION_FAIL = "Fail: show this card again sooner"
    private const val DESCRIPTION_PASS = "Pass: keep the next review on schedule"

    @JvmStatic
    fun revealLabel(): String = localizedText(LABEL_REVEAL, "答えを見る")

    @JvmStatic
    fun againLabel(): String = localizedText(LABEL_FAIL, "不合格")

    @JvmStatic
    fun goodLabel(): String = localizedText(LABEL_PASS, "合格")

    @JvmStatic
    fun undoLabel(): String = localizedText("Undo", "元に戻す")

    @JvmStatic
    fun againContentDescription(): String =
        localizedText(DESCRIPTION_FAIL, "不合格: このカードを早めに再表示する")

    @JvmStatic
    fun goodContentDescription(): String =
        localizedText(DESCRIPTION_PASS, "合格: 次回の復習を予定どおりに保つ")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
