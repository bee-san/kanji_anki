package dev.bee.kanjianki.theme

import androidx.compose.ui.graphics.Color
import dev.bee.kanjianki.KaniColors

internal val DarkKaniColors: KaniColors = dev.bee.kanjianki.DarkKaniColors
internal val GirlypopKaniColors: KaniColors = dev.bee.kanjianki.GirlypopKaniColors
internal val NeutralLightKaniColors: KaniColors = dev.bee.kanjianki.NeutralLightKaniColors
internal val AutumnKaniColors: KaniColors = dev.bee.kanjianki.AutumnKaniColors
internal val MatchaMilkKaniColors: KaniColors = dev.bee.kanjianki.MatchaMilkKaniColors
internal val OceanStudyKaniColors: KaniColors = dev.bee.kanjianki.OceanStudyKaniColors
internal val MidnightArcadeKaniColors: KaniColors = dev.bee.kanjianki.MidnightArcadeKaniColors
internal val GrapeSodaKaniColors: KaniColors = dev.bee.kanjianki.GrapeSodaKaniColors
internal val ForestMossKaniColors: KaniColors = dev.bee.kanjianki.ForestMossKaniColors

internal data class KaniSystemBars(
    val backgroundColor: Color,
    val appearanceLightStatusBars: Boolean,
    val appearanceLightNavigationBars: Boolean,
)

internal fun KaniThemeChoice.resolvePalette(isSystemInDarkTheme: Boolean): KaniColors {
    return when (this) {
        KaniThemeChoice.GIRLYPOP -> GirlypopKaniColors
        KaniThemeChoice.LIGHT -> NeutralLightKaniColors
        KaniThemeChoice.DARK -> DarkKaniColors
        KaniThemeChoice.SYSTEM -> if (isSystemInDarkTheme) DarkKaniColors else NeutralLightKaniColors
        KaniThemeChoice.AUTUMN -> AutumnKaniColors
        KaniThemeChoice.MATCHA_MILK -> MatchaMilkKaniColors
        KaniThemeChoice.OCEAN_STUDY -> OceanStudyKaniColors
        KaniThemeChoice.MIDNIGHT_ARCADE -> MidnightArcadeKaniColors
        KaniThemeChoice.GRAPE_SODA -> GrapeSodaKaniColors
        KaniThemeChoice.FOREST_MOSS -> ForestMossKaniColors
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
