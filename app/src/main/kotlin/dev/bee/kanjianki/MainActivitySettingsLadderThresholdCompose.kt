@file:JvmName("MainActivitySettingsLadderThresholdCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private val LadderThresholdInk = Color(0xFF2D1635)
private val LadderThresholdMuted = Color(0xFF6C5674)
private val LadderThresholdPinkDark = Color(0xFFDA3A7A)
private val LadderThresholdPanelBorder = Color(0xFFFFC7DE)
private val LadderThresholdButtonBorder = Color(0xFFEEBDDA)
private val LadderThresholdWhite = Color(0xFFFFFFFF)
private val LadderThresholdPanelShape = RoundedCornerShape(24.dp)
private val LadderThresholdButtonShape = RoundedCornerShape(12.dp)

fun interface SettingsLadderThresholdAction {
    fun run()
}

data class SettingsLadderThresholdPanelModel(
    val title: String,
    val body: String,
    val promotionDaysLabel: String,
    val promotionDaysInput: EditText,
    val failStreakLabel: String,
    val failStreakInput: EditText,
    val defaultsLabel: String,
    val saveLabel: String,
    val onUseDefaults: SettingsLadderThresholdAction,
    val onSave: SettingsLadderThresholdAction,
)

internal fun ladderThresholdSettingsPanelView(
    activity: MainActivitySettings,
    model: SettingsLadderThresholdPanelModel,
): View {
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setContent {
            MaterialTheme {
                SettingsLadderThresholdPanel(model)
            }
        }
    }
}

@Composable
fun SettingsLadderThresholdPanel(model: SettingsLadderThresholdPanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LadderThresholdPanelShape,
        color = LadderThresholdWhite,
        border = BorderStroke(1.dp, LadderThresholdPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = LadderThresholdInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.body,
                color = LadderThresholdMuted,
                fontSize = 15.sp
            )
            LadderThresholdInput(label = model.promotionDaysLabel, input = model.promotionDaysInput)
            LadderThresholdInput(label = model.failStreakLabel, input = model.failStreakInput)
            OutlinedButton(
                onClick = { model.onUseDefaults.run() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = LadderThresholdButtonShape,
                border = BorderStroke(1.dp, LadderThresholdButtonBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = LadderThresholdWhite,
                    contentColor = LadderThresholdInk
                )
            ) {
                Text(
                    text = model.defaultsLabel,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = { model.onSave.run() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = LadderThresholdButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LadderThresholdPinkDark,
                    contentColor = LadderThresholdWhite
                )
            ) {
                Text(
                    text = model.saveLabel,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LadderThresholdInput(label: String, input: EditText) {
    Text(
        text = label,
        color = LadderThresholdInk,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )
    AndroidView(
        factory = { input },
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
    )
}
