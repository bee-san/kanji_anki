package dev.bee.kanjianki.core

import java.util.Locale

object SettingsStudyAheadTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun studyAheadTitle(): String = localizedText("Study ahead", "先取り学習")

    @JvmStatic
    fun studyAheadBody(): String = localizedText("Review early; learning waits stay fixed.", "早めに復習し、学習待ちはそのまま。")

    @JvmStatic
    fun saveStudyAheadLabel(): String = localizedText("Save minutes", "先取りを保存")

    @JvmStatic
    fun studyAheadSavedToast(): String = localizedText("Look-ahead saved.", "先取り学習を保存しました。")

    @JvmStatic
    fun studyAheadMinutesLabel(): String =
        if (isJapaneseLocale()) {
            String.format(Locale.ROOT, "分 (%s)", studyAheadMinutesRange())
        } else {
            String.format(Locale.ROOT, "Minutes (%s)", studyAheadMinutesRange())
        }

    @JvmStatic
    fun studyAheadMinutesRange(): String {
        return String.format(
            Locale.ROOT,
            "%d-%d",
            SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES,
            SettingsInputRules.MAX_STUDY_AHEAD_MINUTES,
        )
    }

    @JvmStatic
    fun studyAheadWholeNumberErrorText(): String =
        if (isJapaneseLocale()) {
            String.format(Locale.ROOT, "整数の分を入力してください (%s).", studyAheadMinutesRange())
        } else {
            String.format(Locale.ROOT, "Enter whole minutes (%s).", studyAheadMinutesRange())
        }

    @JvmStatic
    fun studyAheadOutOfRangeErrorText(): String =
        if (isJapaneseLocale()) {
            String.format(Locale.ROOT, "%s 分を入力してください。0 でオフになります。", studyAheadMinutesRange())
        } else {
            String.format(Locale.ROOT, "Enter %s minutes; 0 turns it off.", studyAheadMinutesRange())
        }

    @JvmStatic
    fun studyAheadMaxDescription(): String {
        val maxMinutes = SettingsInputRules.MAX_STUDY_AHEAD_MINUTES
        if (maxMinutes % 60 == 0) {
            return if (isJapaneseLocale()) {
                String.format(Locale.ROOT, "%d 分 (%d時間)", maxMinutes, maxMinutes / 60)
            } else {
                String.format(Locale.ROOT, "%d minutes (%dh)", maxMinutes, maxMinutes / 60)
            }
        }
        return if (isJapaneseLocale()) {
            String.format(Locale.ROOT, "%d 分", maxMinutes)
        } else {
            String.format(Locale.ROOT, "%d minutes", maxMinutes)
        }
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
