package dev.bee.kanjianki.kani.ui

import android.view.inputmethod.EditorInfo
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Ink on paper by day, true black by night. Values come from the Kani 1.0 design. */
@Immutable
data class KaniColors(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val line: Color,
    val divider: Color,
    val chip: Color,
    val ink: Color,
    val secondary: Color,
    val body: Color,
    val dim: Color,
    val accent: Color,
    val correct: Color,
    val wrongBg: Color,
    val buttonBg: Color,
    val buttonText: Color,
    val trainingBg: Color,
    val trainingText: Color,
    val watchingBg: Color,
    val watchingText: Color,
    val solidBg: Color,
    val solidText: Color,
)

val LightColors = KaniColors(
    isDark = false,
    bg = Color(0xFFF6F3EC),
    card = Color(0xFFFBF9F4),
    line = Color(0xFFDDD5C6),
    divider = Color(0xFFE6DFD2),
    chip = Color(0xFFF1ECE2),
    ink = Color(0xFF1B1E2B),
    secondary = Color(0xFF575C6B),
    body = Color(0xFF3E4250),
    dim = Color(0xFF8A8E9A),
    accent = Color(0xFFB23A0E),
    correct = Color(0xFF2F4B8A),
    wrongBg = Color(0xFFF6DED3),
    buttonBg = Color(0xFF1B1E2B),
    buttonText = Color(0xFFF6F3EC),
    trainingBg = Color(0xFFF6DED3),
    trainingText = Color(0xFF8E2D0A),
    watchingBg = Color(0xFFECE6DA),
    watchingText = Color(0xFF4A4F5E),
    solidBg = Color(0xFFDCE3F1),
    solidText = Color(0xFF23396B),
)

val AmoledColors = KaniColors(
    isDark = true,
    bg = Color(0xFF000000),
    card = Color(0xFF0E0F12),
    line = Color(0xFF26282E),
    divider = Color(0xFF1E2025),
    chip = Color(0xFF15161A),
    ink = Color(0xFFECE8DF),
    secondary = Color(0xFFA3A6B0),
    body = Color(0xFFC8C5BD),
    dim = Color(0xFF7C808A),
    accent = Color(0xFFF08A5D),
    correct = Color(0xFF8FA8E8),
    wrongBg = Color(0xFF3A1A0E),
    buttonBg = Color(0xFFECE8DF),
    buttonText = Color(0xFF000000),
    trainingBg = Color(0xFF3A1A0E),
    trainingText = Color(0xFFF08A5D),
    watchingBg = Color(0xFF1E2025),
    watchingText = Color(0xFFA3A6B0),
    solidBg = Color(0xFF16203A),
    solidText = Color(0xFF8FA8E8),
)

enum class ThemeMode { SYSTEM, LIGHT, AMOLED }

val LocalKaniColors = staticCompositionLocalOf { LightColors }

/** Kanji render in the system serif, which is Noto Serif CJK JP on most Android phones. */
val KanjiFont: FontFamily = FontFamily.Serif

object Kani {
    val colors: KaniColors
        @Composable get() = LocalKaniColors.current
}

@Composable
fun KaniTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.AMOLED -> true
    }
    CompositionLocalProvider(LocalKaniColors provides if (dark) AmoledColors else LightColors) {
        KeyboardBesideThePage(content)
    }
}

/**
 * Asks the keyboard not to take the whole screen. In landscape a keyboard may
 * hide the app behind a text box of its own ("extract" mode), which on a
 * handheld's short screen hides the kanji being asked about. Every text field
 * under this asks for the keyboard alone, with both of Android's flags for it.
 * They are requests: a keyboard may still ignore them.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun KeyboardBesideThePage(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            next.startInputMethod(
                PlatformTextInputMethodRequest { attributes ->
                    request.createInputConnection(attributes).also {
                        attributes.imeOptions = attributes.imeOptions or
                            EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
                    }
                },
            )
        },
        content = content,
    )
}
