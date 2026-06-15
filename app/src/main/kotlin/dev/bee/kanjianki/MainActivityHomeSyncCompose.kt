@file:JvmName("MainActivityHomeSyncCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy

@Composable
fun SyncResultScreen(model: SyncResultScreenModel) {
    val accent = kaniColor(model.accentColor)
    val contentColor = KaniUiTokens.readableTextColor(accent)
    val primaryColor = kaniColor(model.primaryColor)
    val primaryContentColor = KaniUiTokens.readableTextColor(primaryColor)
    val warningSemantics = if (model.title == HomeTextCopy.syncNeedsAttentionTitle()) {
        Modifier.semantics {
            liveRegion = LiveRegionMode.Assertive
        }
    } else {
        Modifier
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = model.title,
            color = KaniUiTokens.Ink,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
        Surface(
            modifier = Modifier.fillMaxWidth().then(warningSemantics),
            shape = RoundedCornerShape(8.dp),
            color = accent,
            border = BorderStroke(1.dp, KaniUiTokens.PanelBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                model.headline?.let { headline ->
                    Text(
                        text = headline,
                        color = contentColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                model.lines.forEachIndexed { index, line ->
                    Text(
                        text = line,
                        color = contentColor,
                        fontSize = if (index == 0 && model.headline == null) 17.sp else 15.sp,
                        fontWeight = if (index == 0 && model.headline == null) FontWeight.Normal else FontWeight.Medium
                    )
                }
            }
        }
        model.primaryLabel?.let { label ->
            Button(
                onClick = {
                    withButtonTrace(label) {
                        model.onPrimary?.run()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = KaniUiTokens.ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = primaryContentColor
                )
            ) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        OutlinedButton(
            onClick = {
                withButtonTrace(model.secondaryLabel) {
                    model.onSecondary.run()
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, KaniUiTokens.SubtleButtonBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = KaniUiTokens.White,
                contentColor = KaniUiTokens.Ink
            )
        ) {
            Text(
                text = model.secondaryLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
