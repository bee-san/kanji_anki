package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

internal object KaniUiTokens {
    val Ink: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.ink
    val Muted: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.muted
    val Primary: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.primary
    val Coral: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.coral
    val Teal: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.teal
    val Blue: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.blue
    val Grey: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.grey
    val StudyPlum: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.plum
    val Gold: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.gold
    val White: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.surface
    val PanelFill: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.panelFill
    val PanelBorder: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.border
    val SubtleButtonBorder: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.borderSoft
    val ButtonBorder: Color @Composable @ReadOnlyComposable get() = KaniTheme.colors.borderSoft
    val PanelShape = RoundedCornerShape(24.dp)
    val ButtonShape = RoundedCornerShape(12.dp)
    val WideButtonShape = RoundedCornerShape(22.dp)

    /**
     * Picks dark ink or white, whichever is more readable on [background].
     * Backgrounds passed here are saturated accents, so the choice is
     * theme-independent.
     */
    fun readableTextColor(background: Color): Color {
        val ink = Color(MainActivityUiSupport.INK)
        val inkContrast = contrastRatio(ink, background)
        val whiteContrast = contrastRatio(Color.White, background)
        return if (inkContrast >= whiteContrast) ink else Color.White
    }
}

private fun contrastRatio(foreground: Color, background: Color): Double {
    val foregroundLuminance = foreground.luminance().toDouble()
    val backgroundLuminance = background.luminance().toDouble()
    val lighter = max(foregroundLuminance, backgroundLuminance)
    val darker = min(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

@Composable
internal fun KaniPrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    minHeightDp: Int = 56,
    textSizeSp: Int = 17,
    onClick: () -> Unit,
) {
    KaniActionButton(
        label = label,
        primary = true,
        modifier = modifier,
        minHeightDp = minHeightDp,
        textSizeSp = textSizeSp,
        onClick = onClick
    )
}

@Composable
internal fun KaniOutlinedButton(
    label: String,
    modifier: Modifier = Modifier,
    minHeightDp: Int = 50,
    textSizeSp: Int = 16,
    onClick: () -> Unit,
) {
    KaniActionButton(
        label = label,
        primary = false,
        modifier = modifier,
        minHeightDp = minHeightDp,
        textSizeSp = textSizeSp,
        onClick = onClick
    )
}

@Composable
private fun KaniActionButton(
    label: String,
    primary: Boolean,
    modifier: Modifier,
    minHeightDp: Int,
    textSizeSp: Int,
    onClick: () -> Unit,
) {
    val sizedModifier = modifier
        .fillMaxWidth()
        .heightIn(min = minHeightDp.dp)
    if (primary) {
        Button(
            onClick = { withButtonTrace(label) { onClick() } },
            modifier = sizedModifier,
            shape = KaniUiTokens.ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = KaniTheme.colors.primary,
                contentColor = KaniTheme.colors.onPrimary
            )
        ) {
            KaniButtonText(label = label, sizeSp = textSizeSp)
        }
    } else {
        OutlinedButton(
            onClick = { withButtonTrace(label) { onClick() } },
            modifier = sizedModifier,
            shape = KaniUiTokens.ButtonShape,
            border = BorderStroke(1.dp, KaniUiTokens.ButtonBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = KaniUiTokens.White,
                contentColor = KaniUiTokens.Ink
            )
        ) {
            KaniButtonText(label = label, sizeSp = textSizeSp)
        }
    }
}

@Composable
internal fun KaniButtonText(label: String, sizeSp: Int) {
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.Bold
    )
}
