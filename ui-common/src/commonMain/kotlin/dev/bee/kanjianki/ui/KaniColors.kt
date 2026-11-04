package dev.bee.kanjianki.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Kani's semantic color palette.
 *
 * Every color either host renders resolves through one of these fields, so a
 * theme is a single value rather than a scattering of literals. The field names
 * and every literal below are unchanged from the Android app's `KaniColors`:
 * these are the product's visual identity and a stored `app_theme_choice` must
 * keep resolving to the same pixels after the move to shared code.
 *
 * The one thing deliberately *not* carried over is Android's `fromLegacy(argb)`
 * mapping. That existed because Android screen models passed light-palette ARGB
 * ints around as semantic keys; portable state has no such ints, so a shared
 * component that needs a color names it.
 */
@Immutable
data class KaniColors(
    val isDark: Boolean,
    // Backgrounds
    val bg: Color,
    val studyBg: Color,
    val surface: Color,
    val panel: Color,
    val panelSoft: Color,
    val panelFill: Color,
    val pill: Color,
    val secondaryFill: Color,
    // Text
    val ink: Color,
    val plum: Color,
    val muted: Color,
    val grey: Color,
    val greyText: Color,
    // Accents / status
    val primary: Color,
    val onPrimary: Color,
    val coral: Color,
    val onCoral: Color,
    val teal: Color,
    val gold: Color,
    val blue: Color,
    val lilac: Color,
    // Borders / lines
    val border: Color,
    val borderSoft: Color,
    val pinkStroke: Color,
    val track: Color,
    // Disabled buttons
    val disabledContainer: Color,
    val disabledContent: Color,
    val disabledBorder: Color,
)
