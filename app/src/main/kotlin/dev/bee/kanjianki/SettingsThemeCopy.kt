package dev.bee.kanjianki

import androidx.compose.ui.graphics.toArgb
import dev.bee.kanjianki.theme.KaniThemeChoice
import dev.bee.kanjianki.theme.resolvePalette
import java.util.Locale

internal object SettingsThemeCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    internal fun appearanceTitle(): String = localizedText("Appearance", "外観")

    @JvmStatic
    internal fun appearanceBody(): String = localizedText("Choose your app theme.", "アプリのテーマを選ぶ。")

    @JvmStatic
    internal fun choiceTitle(choice: KaniThemeChoice): String {
        return when (choice) {
            KaniThemeChoice.GIRLYPOP -> localizedText("Girlypop", "ガーリーポップ")
            KaniThemeChoice.LIGHT -> localizedText("Light", "ライト")
            KaniThemeChoice.DARK -> localizedText("Dark", "ダーク")
            KaniThemeChoice.SYSTEM -> localizedText("System", "システム")
            KaniThemeChoice.AUTUMN -> localizedText("Autumn", "オータム")
            KaniThemeChoice.MATCHA_MILK -> localizedText("Matcha Milk", "抹茶ミルク")
            KaniThemeChoice.OCEAN_STUDY -> localizedText("Ocean Study", "海の勉強")
            KaniThemeChoice.MIDNIGHT_ARCADE -> localizedText("Midnight Arcade", "真夜中のアーケード")
            KaniThemeChoice.GRAPE_SODA -> localizedText("Grape Soda", "グレープソーダ")
            KaniThemeChoice.FOREST_MOSS -> localizedText("Forest Moss", "森のコケ")
        }
    }

    @JvmStatic
    internal fun choiceSubtitle(choice: KaniThemeChoice): String {
        return when (choice) {
            KaniThemeChoice.GIRLYPOP -> localizedText("Pink, plum, and soft cream.", "ピンク、プラム、やわらかなクリーム。")
            KaniThemeChoice.LIGHT -> localizedText("Bright and neutral.", "明るいニュートラル。")
            KaniThemeChoice.DARK -> localizedText("Low-light and high-contrast.", "暗い画面向けの高コントラスト。")
            KaniThemeChoice.SYSTEM -> localizedText("Follows the device setting.", "端末の設定に合わせる。")
            KaniThemeChoice.AUTUMN -> localizedText("Warm brown and gold.", "あたたかいブラウンとゴールド。")
            KaniThemeChoice.MATCHA_MILK -> localizedText("Soft matcha and cream.", "やさしい抹茶とミルク。")
            KaniThemeChoice.OCEAN_STUDY -> localizedText("Cool ocean blue and paper.", "海の青と紙みたいな落ち着き。")
            KaniThemeChoice.MIDNIGHT_ARCADE -> localizedText("Neon lights on a dark stage.", "深夜のネオンが光るダークテーマ。")
            KaniThemeChoice.GRAPE_SODA -> localizedText("Playful violet and fizz.", "ぶどうソーダみたいな紫と泡。")
            KaniThemeChoice.FOREST_MOSS -> localizedText("Moss green with warm leaf tones.", "森のコケとあたたかい葉の色。")
        }
    }

    @JvmStatic
    internal fun selectedLabel(): String = localizedText("Selected", "選択中")

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
                listOf(
                    light.bg.toArgb(),
                    dark.bg.toArgb(),
                    light.primary.toArgb(),
                    dark.primary.toArgb(),
                )
            }
            else -> themeSwatches(choice.resolvePalette(false))
        }
    }

    private fun themeSwatches(palette: KaniColors): List<Int> {
        return listOf(
            palette.bg.toArgb(),
            palette.surface.toArgb(),
            palette.primary.toArgb(),
            palette.ink.toArgb(),
        )
    }

    private fun localizedText(english: String, japanese: String): String {
        return if (isJapaneseLocale()) japanese else english
    }

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
