package dev.bee.kanjianki.core

import java.util.Locale

/**
 * Theme names and one-line descriptions, in the shared `:core` layer.
 *
 * The Android host's `SettingsThemeCopy` also renders preview swatches, which need a
 * resolved palette and `Color.toArgb` — Android-only, and not something the desktop
 * Appearance section shows. This carries only the locale-aware text both hosts need,
 * so the shared Appearance control can name every theme without either host owning the
 * wording.
 */
object SettingsThemeTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun themeTitle(choice: KaniThemeChoice): String {
        val copy = choiceCopy(choice)
        return if (isJapaneseLocale()) copy.second.first else copy.first.first
    }

    @JvmStatic
    fun themeSubtitle(choice: KaniThemeChoice): String {
        val copy = choiceCopy(choice)
        return if (isJapaneseLocale()) copy.second.second else copy.first.second
    }

    @JvmStatic
    fun selectedLabel(): String = if (isJapaneseLocale()) "選択中" else "Selected"

    // english (title, subtitle) to japanese (title, subtitle).
    private fun choiceCopy(choice: KaniThemeChoice): Pair<Pair<String, String>, Pair<String, String>> =
        when (choice) {
            KaniThemeChoice.GIRLYPOP ->
                ("Girlypop" to "Pink, plum, and soft cream.") to ("ガーリーポップ" to "ピンク、プラム、やわらかなクリーム。")
            KaniThemeChoice.LIGHT ->
                ("Light" to "Bright and neutral.") to ("ライト" to "明るいニュートラル。")
            KaniThemeChoice.DARK ->
                ("Dark" to "Low-light and high-contrast.") to ("ダーク" to "暗い画面向けの高コントラスト。")
            KaniThemeChoice.SYSTEM ->
                ("System" to "Follows the device setting.") to ("システム" to "端末の設定に合わせる。")
            KaniThemeChoice.AUTUMN ->
                ("Autumn" to "Warm brown and gold.") to ("オータム" to "あたたかいブラウンとゴールド。")
            KaniThemeChoice.MATCHA_MILK ->
                ("Matcha Milk" to "Soft matcha and cream.") to ("抹茶ミルク" to "やさしい抹茶とミルク。")
            KaniThemeChoice.OCEAN_STUDY ->
                ("Ocean Study" to "Cool ocean blue and paper.") to ("海の勉強" to "海の青と紙みたいな落ち着き。")
            KaniThemeChoice.MIDNIGHT_ARCADE ->
                ("Midnight Arcade" to "Neon lights on a dark stage.") to ("真夜中のアーケード" to "深夜のネオンが光るダークテーマ。")
            KaniThemeChoice.GRAPE_SODA ->
                ("Grape Soda" to "Playful violet and fizz.") to ("グレープソーダ" to "ぶどうソーダみたいな紫と泡。")
            KaniThemeChoice.FOREST_MOSS ->
                ("Forest Moss" to "Moss green with warm leaf tones.") to ("森のコケ" to "森のコケとあたたかい葉の色。")
        }

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
