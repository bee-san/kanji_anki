package dev.bee.kanjianki.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BlossomBg = Color(0xFFFFF8FB)
val BlossomBgStrong = Color(0xFFFFF2F7)
val BlossomSurface = Color(0xFFFFF9FC)
val BlossomSurfaceStrong = Color(0xFFFFF4F9)
val BlossomInk = Color(0xFF4B2D34)
val BlossomInkSoft = Color(0xFF6E4B55)
val BlossomMuted = Color(0xFF8E7078)
val BlossomLine = Color(0x4DF4A1C2)
val BlossomLineStrong = Color(0x73ED89B4)
val BlossomPink = Color(0xFFFF5E9D)
val BlossomPinkStrong = Color(0xFFEF3D88)
val BlossomPinkSoft = Color(0x1FFF5E9D)
val BlossomViolet = Color(0xFF7E72FF)
val BlossomVioletSoft = Color(0x1F7E72FF)
val BlossomMint = Color(0xFF4C9B67)
val BlossomMintSoft = Color(0x1F4C9B67)
val BlossomApricot = Color(0xFFF58C45)
val BlossomApricotSoft = Color(0x1FF58C45)
val BlossomRose = Color(0xFFFF7CAC)
val BlossomRoseSoft = Color(0x1FFF7CAC)
val BlossomDanger = Color(0xFFD85373)
val BlossomDangerSoft = Color(0x1FD85373)

private val LightPalette = lightColorScheme(
    primary = BlossomPinkStrong,
    onPrimary = Color(0xFFFFFBFD),
    secondary = BlossomViolet,
    onSecondary = Color.White,
    tertiary = BlossomApricot,
    onTertiary = Color.White,
    background = BlossomBg,
    onBackground = BlossomInk,
    surface = BlossomSurface,
    onSurface = BlossomInk,
    surfaceVariant = BlossomBgStrong,
    onSurfaceVariant = BlossomInkSoft,
    outline = BlossomLineStrong,
    error = BlossomDanger,
    onError = Color.White,
)

private val DarkPalette = darkColorScheme(
    primary = Color(0xFFFFA0C3),
    onPrimary = Color(0xFF542035),
    secondary = Color(0xFFC5BEFF),
    onSecondary = Color(0xFF2F285E),
    tertiary = Color(0xFFFFC09A),
    onTertiary = Color(0xFF613114),
    background = Color(0xFF24161C),
    onBackground = Color(0xFFFFEDF3),
    surface = Color(0xFF321E27),
    onSurface = Color(0xFFFFEDF3),
    surfaceVariant = Color(0xFF432A35),
    onSurfaceVariant = Color(0xFFF3C3D4),
    outline = Color(0x66F4A1C2),
    error = Color(0xFFFF93B0),
    onError = Color(0xFF5C1F33),
)

private val BlossomTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.3.sp,
    ),
)

private val BlossomShapes = Shapes(
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
)

@Composable
fun KanjiAnkiTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkPalette else LightPalette,
        typography = BlossomTypography,
        shapes = BlossomShapes,
        content = content,
    )
}
