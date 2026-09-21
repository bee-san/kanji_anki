package dev.bee.kanjianki.ui

/**
 * The user's chosen theme, as shared UI sees it.
 *
 * A separate enum from `:core`'s `KaniThemeChoice` because `:ui-common` is
 * common presentation and may not depend on a JVM module. The [storageKey]
 * values are identical to that enum's on purpose: a host maps between the two
 * by key, and a stored `app_theme_choice` written by the Android app resolves to
 * the same palette in either host.
 * `test_portable_theme_ids_match_the_stored_core_theme_choices` in
 * `tools/test_module_boundaries.py` asserts the two lists stay equal, so the
 * duplication cannot silently drift into two different theme vocabularies.
 */
enum class KaniThemeId(val storageKey: String) {
    GIRLYPOP("girlypop"),
    LIGHT("light"),
    DARK("dark"),

    /**
     * Follows the host's dark-mode signal.
     *
     * The only theme whose palette is not a function of the choice alone, which
     * is why [resolvePalette] takes the host's dark preference as an argument
     * rather than reading it from a composition local.
     */
    SYSTEM("system"),
    AUTUMN("autumn"),
    MATCHA_MILK("matcha_milk"),
    OCEAN_STUDY("ocean_study"),
    MIDNIGHT_ARCADE("midnight_arcade"),
    GRAPE_SODA("grape_soda"),
    FOREST_MOSS("forest_moss"),
    ;

    companion object {
        /** Unknown and absent keys both fall back to the shipped default. */
        fun fromStorageKey(value: String?): KaniThemeId =
            entries.firstOrNull { it.storageKey == value } ?: GIRLYPOP
    }
}

/** The palette this choice renders with, given the host's dark-mode signal. */
fun KaniThemeId.resolvePalette(isSystemInDarkTheme: Boolean): KaniColors = when (this) {
    KaniThemeId.GIRLYPOP -> GirlypopKaniColors
    KaniThemeId.LIGHT -> NeutralLightKaniColors
    KaniThemeId.DARK -> DarkKaniColors
    KaniThemeId.SYSTEM ->
        if (isSystemInDarkTheme) DarkKaniColors else NeutralLightKaniColors
    KaniThemeId.AUTUMN -> AutumnKaniColors
    KaniThemeId.MATCHA_MILK -> MatchaMilkKaniColors
    KaniThemeId.OCEAN_STUDY -> OceanStudyKaniColors
    KaniThemeId.MIDNIGHT_ARCADE -> MidnightArcadeKaniColors
    KaniThemeId.GRAPE_SODA -> GrapeSodaKaniColors
    KaniThemeId.FOREST_MOSS -> ForestMossKaniColors
}

/**
 * Whether this choice renders dark, without the caller inspecting the palette.
 *
 * Hosts need this for chrome they draw themselves — Android's status-bar icon
 * appearance, the desktop window's title-bar hint — and deriving it from the
 * palette keeps one source of truth for "is this a dark theme".
 */
fun KaniThemeId.resolveDarkTheme(isSystemInDarkTheme: Boolean): Boolean =
    resolvePalette(isSystemInDarkTheme).isDark
