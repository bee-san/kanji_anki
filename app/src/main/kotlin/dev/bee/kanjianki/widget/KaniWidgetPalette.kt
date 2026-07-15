package dev.bee.kanjianki.widget

import androidx.compose.ui.graphics.Color
import dev.bee.kanjianki.theme.KaniThemeChoice
import dev.bee.kanjianki.theme.resolvePalette

/**
 * One widget color role with explicit day and night values. Fixed theme
 * choices resolve to the same color for both; only [KaniThemeChoice.SYSTEM]
 * differs so the launcher's UI-mode re-render can flip palettes without a
 * data reload.
 */
internal data class KaniWidgetColorRole(
    val day: Color,
    val night: Color,
) {
    fun withAlpha(alpha: Float): KaniWidgetColorRole =
        KaniWidgetColorRole(day.copy(alpha = alpha), night.copy(alpha = alpha))
}

/**
 * The widget's color roles derived from the in-app theme palettes
 * ([KaniThemeChoice.resolvePalette]). The Glance widget cannot use
 * [dev.bee.kanjianki.KaniTheme]/`MaterialTheme`, so the mapping from
 * `KaniColors` to widget roles is explicit here and unit-tested per theme.
 */
internal data class KaniWidgetPalette(
    /** Widget card background — `KaniColors.bg`. */
    val background: KaniWidgetColorRole,
    /** Brand label, action row, and filled activity cells — `KaniColors.primary`. */
    val primary: KaniWidgetColorRole,
    /** Title text — `KaniColors.ink`. */
    val ink: KaniWidgetColorRole,
    /** Body/supporting text — `KaniColors.muted`. */
    val muted: KaniWidgetColorRole,
    /** Empty activity-strip cells — `KaniColors.track`. */
    val track: KaniWidgetColorRole,
) {
    companion object {
        fun forChoice(choice: KaniThemeChoice): KaniWidgetPalette {
            val day = choice.resolvePalette(isSystemInDarkTheme = false)
            val night = choice.resolvePalette(isSystemInDarkTheme = true)
            return KaniWidgetPalette(
                background = KaniWidgetColorRole(day.bg, night.bg),
                primary = KaniWidgetColorRole(day.primary, night.primary),
                ink = KaniWidgetColorRole(day.ink, night.ink),
                muted = KaniWidgetColorRole(day.muted, night.muted),
                track = KaniWidgetColorRole(day.track, night.track),
            )
        }
    }
}
