package dev.bee.kanjianki.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.bee.kanjianki.theme.KaniThemeChoice
import dev.bee.kanjianki.theme.resolvePalette
import kotlin.math.max
import kotlin.math.min

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
    /** Brand label, action row, and current-day outline — `KaniColors.primary`. */
    val primary: KaniWidgetColorRole,
    /** Accessible text on a filled [primary] action surface. */
    val onPrimary: KaniWidgetColorRole,
    /** Small accent text, falling back to ink when primary is below 4.5:1. */
    val primaryText: KaniWidgetColorRole,
    /** Title text — `KaniColors.ink`. */
    val ink: KaniWidgetColorRole,
    /** Body/supporting text — `KaniColors.muted`. */
    val muted: KaniWidgetColorRole,
    /** Empty legacy activity-strip cells — `KaniColors.track`. */
    val track: KaniWidgetColorRole,
    /** Low activity — `KaniColors.muted`. */
    val heatOne: KaniWidgetColorRole,
    /** Medium activity — `KaniColors.ink`. */
    val heatTwo: KaniWidgetColorRole,
    /** High activity — `KaniColors.primary`. */
    val heatThree: KaniWidgetColorRole,
) {
    companion object {
        fun forChoice(choice: KaniThemeChoice): KaniWidgetPalette {
            val day = choice.resolvePalette(isSystemInDarkTheme = false)
            val night = choice.resolvePalette(isSystemInDarkTheme = true)
            return KaniWidgetPalette(
                background = KaniWidgetColorRole(day.bg, night.bg),
                primary = KaniWidgetColorRole(day.primary, night.primary),
                onPrimary = KaniWidgetColorRole(
                    readableForeground(day.primary),
                    readableForeground(night.primary),
                ),
                primaryText = KaniWidgetColorRole(
                    readableAccent(day.primary, day.bg, day.ink),
                    readableAccent(night.primary, night.bg, night.ink),
                ),
                ink = KaniWidgetColorRole(day.ink, night.ink),
                muted = KaniWidgetColorRole(day.muted, night.muted),
                track = KaniWidgetColorRole(day.track, night.track),
                heatOne = KaniWidgetColorRole(day.muted, night.muted),
                heatTwo = KaniWidgetColorRole(day.ink, night.ink),
                heatThree = KaniWidgetColorRole(day.primary, night.primary),
            )
        }

        private fun readableAccent(primary: Color, background: Color, ink: Color): Color =
            primary.takeIf { contrastRatio(it, background) >= 4.5 } ?: ink

        private fun readableForeground(background: Color): Color =
            listOf(Color.Black, Color.White).maxBy { contrastRatio(it, background) }

        private fun contrastRatio(foreground: Color, background: Color): Double {
            val lighter = max(foreground.luminance(), background.luminance()).toDouble()
            val darker = min(foreground.luminance(), background.luminance()).toDouble()
            return (lighter + 0.05) / (darker + 0.05)
        }
    }
}

internal fun KaniWidgetPalette.activityHeat(intensity: ActivityIntensity): KaniWidgetColorRole = when (intensity) {
    ActivityIntensity.EMPTY -> track
    ActivityIntensity.LOW -> heatOne
    ActivityIntensity.MEDIUM -> heatTwo
    ActivityIntensity.HIGH -> heatThree
}

internal val KaniWidgetPalette.todayOutline: KaniWidgetColorRole
    get() = primary
