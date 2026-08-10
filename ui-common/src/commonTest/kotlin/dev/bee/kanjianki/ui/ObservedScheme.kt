package dev.bee.kanjianki.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Every Material slot Kani sets, plus the palette local, as one comparable value.
 *
 * Read together rather than asserted one at a time so a drifted mapping reports
 * which slot moved instead of failing on the first mismatch and hiding the rest.
 */
data class ObservedScheme(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val secondary: Color,
    val tertiary: Color,
    val outline: Color,
    val error: Color,
    val local: KaniColors,
)

@Composable
@ReadOnlyComposable
fun currentScheme(): ObservedScheme = ObservedScheme(
    primary = MaterialTheme.colorScheme.primary,
    onPrimary = MaterialTheme.colorScheme.onPrimary,
    background = MaterialTheme.colorScheme.background,
    onBackground = MaterialTheme.colorScheme.onBackground,
    surface = MaterialTheme.colorScheme.surface,
    onSurface = MaterialTheme.colorScheme.onSurface,
    surfaceVariant = MaterialTheme.colorScheme.surfaceVariant,
    onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant,
    secondary = MaterialTheme.colorScheme.secondary,
    tertiary = MaterialTheme.colorScheme.tertiary,
    outline = MaterialTheme.colorScheme.outline,
    error = MaterialTheme.colorScheme.error,
    local = LocalKaniColors.current,
)
