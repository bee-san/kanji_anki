package dev.bee.kanjianki.core

import java.util.Locale

object SettingsLearningTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun learningStepsTitle(): String = localizedText("Learning steps", "学習ステップ")

    @JvmStatic
    fun learningStepsBody(): String {
        return localizedText(
            "Set new and missed waits. Due reviews move up.",
            "新規とミス後の待ち時間を設定します。期限レビューは上に進みます。",
        )
    }

    @JvmStatic
    fun reviewMissesLabel(): String = localizedText("Missed reviews", "ミスしたレビュー")

    @JvmStatic
    fun ankiDefaultLabel(): String = localizedText("Use Anki defaults", "Ankiの標準を使う")

    @JvmStatic
    fun sameLearningStepsLabel(): String = localizedText("Copy new-card steps", "新規カードのステップをコピー")

    @JvmStatic
    fun saveLearningStepsLabel(): String = localizedText("Save steps", "ステップを保存")

    @JvmStatic
    fun learningStepsSavedToast(): String = localizedText("Steps saved.", "ステップを保存しました。")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
