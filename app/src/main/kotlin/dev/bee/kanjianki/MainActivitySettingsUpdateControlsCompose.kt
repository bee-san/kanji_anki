package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.SettingsTextCopy

internal val SettingsUpdateInk: Color @Composable get() = KaniUiTokens.Ink
internal val SettingsUpdateMuted: Color @Composable get() = KaniUiTokens.Muted
internal val SettingsUpdateCoral: Color @Composable get() = KaniUiTokens.Coral
internal val SettingsUpdateTeal: Color @Composable get() = KaniUiTokens.Teal
internal val SettingsUpdatePinkDark: Color @Composable get() = KaniUiTokens.Primary
internal val SettingsUpdateHomeButtonBorder: Color @Composable get() = KaniUiTokens.SubtleButtonBorder
internal val SettingsUpdateButtonBorder: Color @Composable get() = KaniUiTokens.ButtonBorder
internal val SettingsUpdatePanelBorder: Color @Composable get() = KaniUiTokens.PanelBorder
internal val SettingsUpdatePanelFill: Color @Composable get() = KaniUiTokens.PanelFill
internal val SettingsUpdateWhite: Color @Composable get() = KaniUiTokens.White
internal val SettingsUpdatePrimaryButtonShape = KaniUiTokens.ButtonShape
internal val SettingsUpdateWideButtonShape = KaniUiTokens.WideButtonShape
internal val SettingsUpdatePanelShape = KaniUiTokens.PanelShape

@Composable
internal fun SettingsUpdateHomeButton(onClick: () -> Unit) {
    SettingsUpdateOutlinedButton(
        label = HomeTextCopy.homeLabel(),
        iconRes = R.drawable.ic_home_24,
        minHeight = 56.dp,
        shape = SettingsUpdateWideButtonShape,
        fontSize = 15.sp,
        borderColor = SettingsUpdateHomeButtonBorder,
        onClick = onClick
    )
}

@Composable
internal fun SettingsUpdateBackButton(onClick: () -> Unit) {
    SettingsUpdateOutlinedButton(
        label = SettingsTextCopy.backToSettingsLabel(),
        minHeight = 54.dp,
        shape = SettingsUpdatePrimaryButtonShape,
        fontSize = 16.sp,
        onClick = onClick
    )
}

@Composable
internal fun SettingsUpdateOutlinedButton(
    label: String,
    onClick: () -> Unit,
    iconRes: Int? = null,
    minHeight: Dp,
    shape: RoundedCornerShape,
    fontSize: TextUnit = 16.sp,
    borderColor: Color = SettingsUpdateButtonBorder,
) {
    OutlinedButton(
        onClick = { withButtonTrace(label) { onClick() } },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        shape = shape,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SettingsUpdateWhite,
            contentColor = SettingsUpdateInk
        )
    ) {
        SettingsUpdateButtonContent(
            label = label,
            iconRes = iconRes,
            contentColor = SettingsUpdateInk,
            fontSize = fontSize
        )
    }
}

@Composable
internal fun SettingsUpdateFilledButton(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    minHeight: Dp,
    shape: RoundedCornerShape,
    iconRes: Int? = null,
    fontSize: TextUnit = 19.sp,
) {
    Button(
        onClick = { withButtonTrace(label) { onClick() } },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        SettingsUpdateButtonContent(
            label = label,
            iconRes = iconRes,
            contentColor = contentColor,
            fontSize = fontSize
        )
    }
}

@Composable
private fun SettingsUpdateButtonContent(
    label: String,
    iconRes: Int?,
    contentColor: Color,
    fontSize: TextUnit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold
        )
    }
}
