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
            KaniThemeChoice.GIRLYPOP -> themeSwatches(choice.resolvePalette(false))
            KaniThemeChoice.LIGHT -> themeSwatches(choice.resolvePalette(false))
            KaniThemeChoice.DARK -> themeSwatches(choice.resolvePalette(false))
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
            KaniThemeChoice.AUTUMN -> themeSwatches(choice.resolvePalette(false))
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
