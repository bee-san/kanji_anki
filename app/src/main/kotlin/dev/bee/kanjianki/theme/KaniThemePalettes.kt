package dev.bee.kanjianki.theme

import androidx.compose.ui.graphics.Color
import dev.bee.kanjianki.KaniColors

internal data class KaniSystemBars(
    val backgroundColor: Color,
    val appearanceLightStatusBars: Boolean,
    val appearanceLightNavigationBars: Boolean,
)

internal fun KaniThemeChoice.resolvePalette(isSystemInDarkTheme: Boolean): KaniColors {
    return when (this) {
        KaniThemeChoice.GIRLYPOP -> dev.bee.kanjianki.GirlypopKaniColors
        KaniThemeChoice.LIGHT -> dev.bee.kanjianki.NeutralLightKaniColors
        KaniThemeChoice.DARK -> dev.bee.kanjianki.DarkKaniColors
        KaniThemeChoice.SYSTEM -> if (isSystemInDarkTheme) {
            dev.bee.kanjianki.DarkKaniColors
        } else {
            dev.bee.kanjianki.NeutralLightKaniColors
        }
        KaniThemeChoice.AUTUMN -> dev.bee.kanjianki.AutumnKaniColors
        KaniThemeChoice.MATCHA_MILK -> dev.bee.kanjianki.MatchaMilkKaniColors
        KaniThemeChoice.OCEAN_STUDY -> dev.bee.kanjianki.OceanStudyKaniColors
        KaniThemeChoice.MIDNIGHT_ARCADE -> dev.bee.kanjianki.MidnightArcadeKaniColors
        KaniThemeChoice.GRAPE_SODA -> dev.bee.kanjianki.GrapeSodaKaniColors
        KaniThemeChoice.FOREST_MOSS -> dev.bee.kanjianki.ForestMossKaniColors
    }
}

internal fun KaniThemeChoice.resolveDarkTheme(isSystemInDarkTheme: Boolean): Boolean {
    return resolvePalette(isSystemInDarkTheme).isDark
}

internal fun KaniThemeChoice.resolveSystemBars(isSystemInDarkTheme: Boolean): KaniSystemBars {
    val palette = resolvePalette(isSystemInDarkTheme)
    val lightIcons = !palette.isDark
    return KaniSystemBars(
        backgroundColor = palette.bg,
        appearanceLightStatusBars = lightIcons,
        appearanceLightNavigationBars = lightIcons,
    )
}
