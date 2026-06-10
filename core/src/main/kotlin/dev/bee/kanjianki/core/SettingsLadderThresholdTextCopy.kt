package dev.bee.kanjianki.core

import java.util.Locale

object SettingsLadderThresholdTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun ladderThresholdsTitle(): String = localizedText("Ladder movement", "ラダー移動")

    @JvmStatic
    fun ladderThresholdsBody(): String = localizedText("Due reviews move cards. Repeats stay practice-only.", "期限レビューでカードが移動する。繰り返しは練習のみ。")

    @JvmStatic
    fun fsrsDaysToGoUpLabel(): String = localizedText("Days to move up", "上がるまでの日数")

    @JvmStatic
    fun failsToGoDownLabel(): String = localizedText("Fails to move down", "下がるまでの失敗数")

    @JvmStatic
    fun useDefaultLadderThresholdsLabel(): String = localizedText("Use default movement rules", "既定の移動ルールを使う")

    @JvmStatic
    fun saveLadderThresholdsLabel(): String = localizedText("Save rules", "ルールを保存")

    @JvmStatic
    fun ladderThresholdsSavedToast(): String = localizedText("Movement rules saved.", "移動ルールを保存しました。")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
