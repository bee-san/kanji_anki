package dev.bee.kanjianki

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import dev.bee.kanjianki.ui.KaniUiTokens
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.theme.resolvePalette

/**
 * Semantic color palette for Kani. Every color rendered by the app should
 * resolve through this class so light and dark themes stay consistent.
 */
@Immutable
internal class KaniColors(
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
) {
}

internal val LightKaniColors = KaniColors(
    isDark = false,
    bg = Color(0xFFFFF7FB),
    studyBg = Color(0xFFFFF6FB),
    surface = Color(0xFFFFFFFF),
    panel = Color(0xFFFFECF5),
    panelSoft = Color(0xFFFDF1F7),
    panelFill = Color(0xFFFFFDFE),
    pill = Color(0xFFFFEFF7),
    secondaryFill = Color(0xFFFFF5FA),
    ink = Color(0xFF2D1635),
    plum = Color(0xFF4B2552),
    muted = Color(0xFF6C5674),
    grey = Color(0xFFB2B2BA),
    greyText = Color(0xFF6E6E78),
    primary = Color(0xFFD32F73),
    onPrimary = Color(0xFFFFFFFF),
    coral = Color(0xFFFF4C76),
    onCoral = Color(0xFFFFFFFF),
    teal = Color(0xFF00AEB5),
    gold = Color(0xFFFFD640),
    blue = Color(0xFF6E5CE6),
    lilac = Color(0xFF7648FF),
    border = Color(0xFFFFC7DE),
    borderSoft = Color(0xFFEEBDDA),
    pinkStroke = Color(0xFFFFAECC),
    track = Color(0xFFFBDDEC),
    disabledContainer = Color(0xFFFFC2D8),
    disabledContent = Color(0xFF9F8A98),
    disabledBorder = Color(0xFFFFD5E6),
)

internal val GirlypopKaniColors: KaniColors = LightKaniColors

internal val NeutralLightKaniColors = KaniColors(
    isDark = false,
    bg = Color(0xFFFFFCF8),
    studyBg = Color(0xFFFFFAF5),
    surface = Color(0xFFFFFFFF),
    panel = Color(0xFFF5ECE5),
    panelSoft = Color(0xFFF9F4EF),
    panelFill = Color(0xFFFFFDFC),
    pill = Color(0xFFF2E7E1),
    secondaryFill = Color(0xFFF8F3EE),
    ink = Color(0xFF241B23),
    plum = Color(0xFF4B3B4B),
    muted = Color(0xFF6D6470),
    grey = Color(0xFFB6AEB6),
    greyText = Color(0xFF776D79),
    primary = Color(0xFFB84E58),
    onPrimary = Color(0xFFFFFFFF),
    coral = Color(0xFFC96B5E),
    onCoral = Color(0xFFFFFFFF),
    teal = Color(0xFF1B8B8D),
    gold = Color(0xFFC78C22),
    blue = Color(0xFF6477DA),
    lilac = Color(0xFF8A67F1),
    border = Color(0xFFE4D5CF),
    borderSoft = Color(0xFFDACAC3),
    pinkStroke = Color(0xFFD2B5BE),
    track = Color(0xFFECE3DE),
    disabledContainer = Color(0xFFF0E6E1),
    disabledContent = Color(0xFF9D9498),
    disabledBorder = Color(0xFFE1D5D0),
)

internal val AutumnKaniColors = KaniColors(
    isDark = false,
    bg = Color(0xFFFFF6ED),
    studyBg = Color(0xFFFFF3E3),
    surface = Color(0xFFFFFFFF),
    panel = Color(0xFFFFE8CE),
    panelSoft = Color(0xFFFDF0DF),
    panelFill = Color(0xFFFFFCF8),
    pill = Color(0xFFFFE6C8),
    secondaryFill = Color(0xFFFDF3E7),
    ink = Color(0xFF2E1C12),
    plum = Color(0xFF5A3725),
    muted = Color(0xFF7B6555),
    grey = Color(0xFFBAA99A),
    greyText = Color(0xFF7D6656),
    primary = Color(0xFFB85A18),
    onPrimary = Color(0xFFFFFFFF),
    coral = Color(0xFFD46A2F),
    onCoral = Color(0xFFFFFFFF),
    teal = Color(0xFF2A8E84),
    gold = Color(0xFFD49B22),
    blue = Color(0xFF6C64D6),
    lilac = Color(0xFFA069EA),
    border = Color(0xFFE1C9B1),
    borderSoft = Color(0xFFD7B893),
    pinkStroke = Color(0xFFCFA789),
    track = Color(0xFFF0DEC9),
    disabledContainer = Color(0xFFF2E4D3),
    disabledContent = Color(0xFF9E8975),
    disabledBorder = Color(0xFFE4CBB5),
)

internal val DarkKaniColors = KaniColors(
    isDark = true,
    // Inlined from the deleted MainActivityUiSupport, whose only surviving use this was.
    bg = Color(0xFF1C1220),
    studyBg = Color(0xFF1F1424),
    surface = Color(0xFF2B1C30),
    panel = Color(0xFF3A2240),
    panelSoft = Color(0xFF341F39),
    panelFill = Color(0xFF2E1E33),
    pill = Color(0xFF3D2543),
    secondaryFill = Color(0xFF32203A),
    ink = Color(0xFFF5EAF4),
    plum = Color(0xFFE9D7EA),
    muted = Color(0xFFB59FBC),
    grey = Color(0xFF8A8A94),
    greyText = Color(0xFFA9A2B0),
    primary = Color(0xFFFF8AB6),
    onPrimary = Color(0xFF46102C),
    coral = Color(0xFFFF7D9B),
    onCoral = Color(0xFF4A1024),
    teal = Color(0xFF3FC9CE),
    gold = Color(0xFFFFD640),
    blue = Color(0xFF9D8FFF),
    lilac = Color(0xFFA98AFF),
    border = Color(0xFF55305C),
    borderSoft = Color(0xFF4A2C50),
    pinkStroke = Color(0xFF6B3A5C),
    track = Color(0xFF4A2C50),
    disabledContainer = Color(0xFF3A2440),
    disabledContent = Color(0xFF7E6C84),
    disabledBorder = Color(0xFF4A2C50),
)

internal val LocalKaniColors = staticCompositionLocalOf { LightKaniColors }

internal val KaniTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

internal val KaniShapes = Shapes(
    small = KaniUiTokens.ButtonShape,
    medium = KaniUiTokens.LeafShape,
    large = KaniUiTokens.PanelShape,
)

/** Accessor mirror of [MaterialTheme]: `KaniTheme.colors` inside composables. */
internal object KaniTheme {
    val colors: KaniColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKaniColors.current
}

@Composable
internal fun KaniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    KaniTheme(
        choice = if (darkTheme) KaniThemeChoice.DARK else KaniThemeChoice.GIRLYPOP,
        isSystemInDarkTheme = darkTheme,
        content = content,
    )
}

@Composable
internal fun KaniTheme(
    choice: KaniThemeChoice,
    isSystemInDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = choice.resolvePalette(isSystemInDarkTheme)
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
        MaterialTheme(colorScheme = scheme, typography = KaniTypography, shapes = KaniShapes, content = content)
    }
}

