package dev.bee.kanjianki.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KaniLightColors = lightColorScheme(
    primary = Color(0xFFB83A6A),
    onPrimary = Color.White,
    secondary = Color(0xFF6E5A7E),
    onSecondary = Color.White,
    tertiary = Color(0xFF1C7C74),
    onTertiary = Color.White,
    background = Color(0xFFFFF8FB),
    onBackground = Color(0xFF22181D),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF22181D),
)

object KaniSpacing {
    const val tiny = 4
    const val small = 8
    const val medium = 12
    const val large = 16
    const val xlarge = 24
}

@Composable
fun KaniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KaniLightColors,
        content = content,
    )
}
