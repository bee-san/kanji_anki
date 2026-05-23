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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.SettingsTextCopy

internal val SettingsUpdateInk = ComposeColor(0xFF2D1635)
internal val SettingsUpdateMuted = ComposeColor(0xFF6C5674)
internal val SettingsUpdateCoral = ComposeColor(0xFFFF4C76)
internal val SettingsUpdateTeal = ComposeColor(0xFF00AEB5)
internal val SettingsUpdatePinkDark = ComposeColor(0xFFDA3A7A)
internal val SettingsUpdateHomeButtonBorder = ComposeColor(0xFFEBD6E4)
internal val SettingsUpdateButtonBorder = ComposeColor(0xFFEEBDDA)
internal val SettingsUpdatePanelBorder = ComposeColor(0xFFFFC7DE)
internal val SettingsUpdatePanelFill = ComposeColor(0xFFFFFDFE)
internal val SettingsUpdateWhite = ComposeColor(0xFFFFFFFF)
internal val SettingsUpdatePrimaryButtonShape = RoundedCornerShape(12.dp)
internal val SettingsUpdateWideButtonShape = RoundedCornerShape(22.dp)
internal val SettingsUpdatePanelShape = RoundedCornerShape(24.dp)

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
    minHeight: androidx.compose.ui.unit.Dp,
    shape: RoundedCornerShape,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    borderColor: ComposeColor = SettingsUpdateButtonBorder,
) {
    OutlinedButton(
        onClick = onClick,
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
    containerColor: ComposeColor,
    contentColor: ComposeColor,
    minHeight: androidx.compose.ui.unit.Dp,
    shape: RoundedCornerShape,
    iconRes: Int? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 19.sp,
) {
    Button(
        onClick = onClick,
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
    contentColor: ComposeColor,
    fontSize: androidx.compose.ui.unit.TextUnit,
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
