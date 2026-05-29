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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object KaniUiTokens {
    val Ink = Color(0xFF2D1635)
    val Muted = Color(0xFF6C5674)
    val Primary = Color(0xFFDA3A7A)
    val Coral = Color(MainActivityUiSupport.CORAL)
    val Teal = Color(0xFF24756C)
    val White = Color(0xFFFFFFFF)
    val PanelBorder = Color(0xFFFFC7DE)
    val ButtonBorder = Color(0xFFEEBDDA)
    val PanelShape = RoundedCornerShape(24.dp)
    val ButtonShape = RoundedCornerShape(12.dp)
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
            onClick = onClick,
            modifier = sizedModifier,
            shape = KaniUiTokens.ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = KaniUiTokens.Primary,
                contentColor = KaniUiTokens.White
            )
        ) {
            KaniButtonText(label = label, sizeSp = textSizeSp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
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
