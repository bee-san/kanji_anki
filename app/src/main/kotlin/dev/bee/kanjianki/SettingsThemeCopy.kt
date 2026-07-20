package dev.bee.kanjianki

import androidx.compose.ui.graphics.toArgb
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.theme.resolvePalette
import java.util.Locale

internal object SettingsThemeCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    private data class ChoiceCopy(
        val titleEnglish: String,
        val titleJapanese: String,
        val subtitleEnglish: String,
        val subtitleJapanese: String,
    )

    @JvmStatic
    internal fun appearanceTitle(): String = if (isJapaneseLocale()) "外観" else "Appearance"

    @JvmStatic
    internal fun appearanceBody(): String = if (isJapaneseLocale()) "アプリのテーマを選ぶ。" else "Choose your app theme."

    @JvmStatic
    internal fun choiceTitle(choice: KaniThemeChoice): String {
        val copy = choiceCopy(choice)
        return if (isJapaneseLocale()) copy.titleJapanese else copy.titleEnglish
    }

    @JvmStatic
    internal fun choiceSubtitle(choice: KaniThemeChoice): String {
        val copy = choiceCopy(choice)
        return if (isJapaneseLocale()) copy.subtitleJapanese else copy.subtitleEnglish
    }

    @JvmStatic
    internal fun selectedLabel(): String = if (isJapaneseLocale()) "選択中" else "Selected"

    @JvmStatic
    internal fun choiceContentDescription(choice: KaniThemeChoice, selected: Boolean): String {
        val title = choiceTitle(choice)
        val subtitle = choiceSubtitle(choice)
        return if (isJapaneseLocale()) {
            if (selected) {
                "${title}、選択済み。$subtitle"
            } else {
                "${title}を選択。$subtitle"
            }
        } else {
            if (selected) {
                "$title theme, selected. $subtitle"
            } else {
                "Select $title theme. $subtitle"
            }
        }
    }

    @JvmStatic
    internal fun previewSwatches(choice: KaniThemeChoice): List<Int> {
        return when (choice) {
            KaniThemeChoice.SYSTEM -> {
                val light = KaniThemeChoice.LIGHT.resolvePalette(false)
                val dark = KaniThemeChoice.DARK.resolvePalette(true)
                listOf(light.bg, dark.bg, light.primary, dark.primary).map { it.toArgb() }
            }
            else -> themeSwatches(choice.resolvePalette(false))
        }
    }

    private fun choiceCopy(choice: KaniThemeChoice): ChoiceCopy {
        return when (choice) {
            KaniThemeChoice.GIRLYPOP -> ChoiceCopy(
                titleEnglish = "Girlypop",
                titleJapanese = "ガーリーポップ",
                subtitleEnglish = "Pink, plum, and soft cream.",
                subtitleJapanese = "ピンク、プラム、やわらかなクリーム。",
            )
            KaniThemeChoice.LIGHT -> ChoiceCopy(
                titleEnglish = "Light",
                titleJapanese = "ライト",
                subtitleEnglish = "Bright and neutral.",
                subtitleJapanese = "明るいニュートラル。",
            )
            KaniThemeChoice.DARK -> ChoiceCopy(
                titleEnglish = "Dark",
                titleJapanese = "ダーク",
                subtitleEnglish = "Low-light and high-contrast.",
                subtitleJapanese = "暗い画面向けの高コントラスト。",
            )
            KaniThemeChoice.SYSTEM -> ChoiceCopy(
                titleEnglish = "System",
                titleJapanese = "システム",
                subtitleEnglish = "Follows the device setting.",
                subtitleJapanese = "端末の設定に合わせる。",
            )
            KaniThemeChoice.AUTUMN -> ChoiceCopy(
                titleEnglish = "Autumn",
                titleJapanese = "オータム",
                subtitleEnglish = "Warm brown and gold.",
                subtitleJapanese = "あたたかいブラウンとゴールド。",
            )
            KaniThemeChoice.MATCHA_MILK -> ChoiceCopy(
                titleEnglish = "Matcha Milk",
                titleJapanese = "抹茶ミルク",
                subtitleEnglish = "Soft matcha and cream.",
                subtitleJapanese = "やさしい抹茶とミルク。",
            )
            KaniThemeChoice.OCEAN_STUDY -> ChoiceCopy(
                titleEnglish = "Ocean Study",
                titleJapanese = "海の勉強",
                subtitleEnglish = "Cool ocean blue and paper.",
                subtitleJapanese = "海の青と紙みたいな落ち着き。",
            )
            KaniThemeChoice.MIDNIGHT_ARCADE -> ChoiceCopy(
                titleEnglish = "Midnight Arcade",
                titleJapanese = "真夜中のアーケード",
                subtitleEnglish = "Neon lights on a dark stage.",
                subtitleJapanese = "深夜のネオンが光るダークテーマ。",
            )
            KaniThemeChoice.GRAPE_SODA -> ChoiceCopy(
                titleEnglish = "Grape Soda",
                titleJapanese = "グレープソーダ",
                subtitleEnglish = "Playful violet and fizz.",
                subtitleJapanese = "ぶどうソーダみたいな紫と泡。",
            )
            KaniThemeChoice.FOREST_MOSS -> ChoiceCopy(
                titleEnglish = "Forest Moss",
                titleJapanese = "森のコケ",
                subtitleEnglish = "Moss green with warm leaf tones.",
                subtitleJapanese = "森のコケとあたたかい葉の色。",
            )
        }
    }

    private fun themeSwatches(palette: KaniColors): List<Int> {
        return listOf(palette.bg, palette.surface, palette.primary, palette.ink).map { it.toArgb() }
    }

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
