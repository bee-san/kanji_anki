package dev.bee.kanjianki.core

import java.util.Locale

/**
 * The Study keybinding editor's wording, in shared `:core`.
 *
 * Here rather than in the host mapper for the same reason every other settings copy is:
 * both hosts show the same editor, and the Japanese wording should not exist twice. The
 * command names are the *study* names the user already sees on the card's own buttons
 * (Pass, Fail, Undo) — an editor that called them `grade_pass` would be leaking the wire
 * id, and one that invented new names would describe buttons that are not there.
 *
 * The keystroke labels themselves are not here: `StudyKeystroke.label` builds them from
 * the platform's own notation, and `Ctrl` and `⌘` are not translated on any platform.
 */
object SettingsKeybindingTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun keybindingsTitle(): String = localizedText("Keyboard shortcuts", "キーボードショートカット")

    @JvmStatic
    fun keybindingsSummary(): String =
        localizedText(
            "Remap the Study keys. Buttons keep working either way.",
            "学習中のキーを変更できます。ボタンはそのまま使えます。",
        )

    /** The command's name, as the study screen's own buttons say it. */
    @JvmStatic
    fun commandLabel(commandId: String): String = when (commandId) {
        COMMAND_PRIMARY -> localizedText("Show answer / continue", "答えを表示・次へ")
        COMMAND_GRADE_PASS -> localizedText("Pass", "正解")
        COMMAND_GRADE_FAIL -> localizedText("Fail", "不正解")
        COMMAND_UNDO -> localizedText("Undo", "元に戻す")
        else -> commandId
    }

    /** Shown in place of an accelerator when a command has no key at all. */
    @JvmStatic
    fun unboundLabel(): String = localizedText("No key", "キーなし")

    @JvmStatic
    fun resetLabel(): String = localizedText("Reset to defaults", "初期設定に戻す")

    /** The "add a key" affordance's label for one command. */
    @JvmStatic
    fun addKeyLabel(): String = localizedText("Add key", "キーを追加")

    /** The "remove this key" affordance's label for one bound keystroke. */
    @JvmStatic
    fun removeKeyLabel(keystroke: String): String =
        if (isJapaneseLocale()) "${keystroke}を削除" else "Remove $keystroke"

    /** Why a keystroke another command already holds cannot be taken. */
    @JvmStatic
    fun conflictReason(commandLabel: String): String =
        if (isJapaneseLocale()) "${commandLabel}が使用中" else "Already $commandLabel"

    /**
     * Why a keystroke the platform owns cannot be taken.
     *
     * [reservedFor] is the OS action's English name from the reserved list; it is named
     * in the sentence rather than translated, because a user hunting for why `Ctrl+C` is
     * refused is helped most by the word the OS itself uses.
     */
    @JvmStatic
    fun reservedReason(reservedFor: String): String =
        if (isJapaneseLocale()) "OSが使用中: $reservedFor" else "Used by the system: $reservedFor"

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

    // The wire ids of the study commands, restated as constants rather than depending on
    // :presentation-api — :core sits below it, and the ids are a stable stored format.
    private const val COMMAND_PRIMARY = "primary"
    private const val COMMAND_GRADE_PASS = "grade_pass"
    private const val COMMAND_GRADE_FAIL = "grade_fail"
    private const val COMMAND_UNDO = "undo"
}
