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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = ComposeColor(0xFF2D1635)
private val White = ComposeColor(0xFFFFFFFF)
private val BorderPink = ComposeColor(0xFFEBD6E4)

@Composable
fun SyncResultScreen(model: SyncResultScreenModel) {
    val accent = ComposeColor(model.accentColor)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = model.title,
            color = Ink,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = accent,
            border = BorderStroke(1.dp, accent)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                model.headline?.let { headline ->
                    Text(
                        text = headline,
                        color = White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                model.lines.forEachIndexed { index, line ->
                    Text(
                        text = line,
                        color = White,
                        fontSize = if (index == 0 && model.headline == null) 17.sp else 15.sp,
                        fontWeight = if (index == 0 && model.headline == null) FontWeight.Normal else FontWeight.Medium
                    )
                }
            }
        }
        model.primaryLabel?.let { label ->
            Button(
                onClick = { model.onPrimary?.run() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ComposeColor(model.primaryColor),
                    contentColor = White
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
            onClick = { model.onSecondary.run() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, BorderPink),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = White,
                contentColor = Ink
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
