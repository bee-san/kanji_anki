package dev.bee.kanjianki.widget

import dev.bee.kanjianki.core.KaniThemeChoice

/** Which layout a widget instance renders. */
enum class KaniWidgetStyle(val storageKey: String) {
    DUE_CARD("due_card"),
    HEATMAP("heatmap");

    companion object {
        fun fromStorageKey(value: String?): KaniWidgetStyle =
            entries.firstOrNull { it.storageKey == value } ?: DUE_CARD
    }
}

/**
 * Per-instance widget configuration persisted in Glance state. Parsing is
 * defensive: unknown or missing values fall back to the zero-config default
 * (due card, follow the in-app theme), so dropping a widget without visiting
 * the configure screen keeps today's behavior.
 */
data class KaniWidgetInstanceOptions(
    val style: KaniWidgetStyle = KaniWidgetStyle.DUE_CARD,
    /** `null` means "follow the in-app theme". */
    val themeOverride: KaniThemeChoice? = null,
) {
    fun resolveTheme(appTheme: KaniThemeChoice): KaniThemeChoice = themeOverride ?: appTheme

    fun withStyle(style: KaniWidgetStyle): KaniWidgetInstanceOptions = copy(style = style)

    fun themeStorageValue(): String = themeOverride?.storageKey ?: THEME_FOLLOW_APP

    companion object {
        const val STYLE_PREF_KEY = "widget_style"
        const val THEME_OVERRIDE_PREF_KEY = "widget_theme_override"
        const val THEME_FOLLOW_APP = "follow_app"

        fun fromStorageValues(styleValue: String?, themeValue: String?): KaniWidgetInstanceOptions {
            val themeOverride = themeValue
                ?.takeIf { it.isNotEmpty() && it != THEME_FOLLOW_APP }
                ?.let { key -> KaniThemeChoice.entries.firstOrNull { it.storageKey == key } }
            return KaniWidgetInstanceOptions(
                style = KaniWidgetStyle.fromStorageKey(styleValue),
                themeOverride = themeOverride,
            )
        }
    }
}
