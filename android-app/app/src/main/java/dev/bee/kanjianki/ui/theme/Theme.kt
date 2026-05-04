package dev.bee.kanjianki.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightPalette = lightColorScheme(
    primary = Color(0xFF215A6D),
    onPrimary = Color(0xFFF9F5EB),
    secondary = Color(0xFF7A4E2D),
    onSecondary = Color(0xFFF9F5EB),
    tertiary = Color(0xFF8A7B45),
    background = Color(0xFFF6F1E8),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF1D1B16),
)

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF8EC7D8),
    secondary = Color(0xFFD1AE8D),
    tertiary = Color(0xFFE5D48F),
    background = Color(0xFF171411),
    surface = Color(0xFF211D18),
    onSurface = Color(0xFFF3ECE2),
)

@Composable
fun KanjiAnkiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkPalette else LightPalette,
        content = content,
    )
}
