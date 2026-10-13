package dev.bee.kanjianki.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val KaniTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

val KaniShapes = Shapes(
    small = KaniUiTokens.ButtonShape,
    medium = KaniUiTokens.LeafShape,
    large = KaniUiTokens.PanelShape,
)

/**
 * The palette in scope.
 *
 * `static` rather than a regular composition local: a theme change should
 * recompose everything reading it, and nothing reads it conditionally, so the
 * cheaper non-tracking local is the right one.
 */
val LocalKaniColors = staticCompositionLocalOf { GirlypopKaniColors }

/** Accessor mirror of [MaterialTheme]: `KaniTheme.colors` inside composables. */
object KaniTheme {
    val colors: KaniColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKaniColors.current
}

/**
 * Kani's theme, for both hosts.
 *
 * [isSystemInDarkTheme] is a parameter rather than a call to Compose's
 * `isSystemInDarkTheme()` because the two hosts answer it differently — Android
 * from `uiMode`, desktop from the OS appearance or a stored preference — and a
 * shared composable that guessed would be wrong on one of them. It also makes
 * every theme renderable in a test without a system to interrogate.
 *
 * The [Material 3][MaterialTheme] scheme is derived from the palette rather than
 * declared beside it, so a palette edit cannot leave the M3 mapping stale. The
 * mapping is the Android app's, field for field.
 */
@Composable
fun KaniTheme(
    theme: KaniThemeId = KaniThemeId.GIRLYPOP,
    isSystemInDarkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    KaniTheme(
        colors = theme.resolvePalette(isSystemInDarkTheme),
        content = content,
    )
}

/** Renders [content] under an explicit palette, bypassing choice resolution. */
@Composable
fun KaniTheme(
    colors: KaniColors,
    content: @Composable () -> Unit,
) {
    val scheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            secondary = colors.teal,
            tertiary = colors.blue,
            background = colors.bg,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.panel,
            onSurfaceVariant = colors.muted,
            outline = colors.border,
            error = colors.coral,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            secondary = colors.teal,
            tertiary = colors.blue,
            background = colors.bg,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.panel,
            onSurfaceVariant = colors.muted,
            outline = colors.border,
            error = colors.coral,
        )
    }
    CompositionLocalProvider(LocalKaniColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = KaniTypography,
            shapes = KaniShapes,
            content = content,
        )
    }
}
