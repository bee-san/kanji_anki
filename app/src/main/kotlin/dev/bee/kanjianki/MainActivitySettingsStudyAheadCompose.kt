@file:JvmName("MainActivitySettingsStudyAheadCompose")

package dev.bee.kanjianki

import android.view.View
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

private val StudyAheadInk = Color(0xFF2D1635)
private val StudyAheadMuted = Color(0xFF6C5674)
private val StudyAheadPinkDark = Color(0xFFDA3A7A)
private val StudyAheadPanelBorder = Color(0xFFFFC7DE)
private val StudyAheadWhite = Color(0xFFFFFFFF)
private val StudyAheadPanelShape = RoundedCornerShape(24.dp)
private val StudyAheadButtonShape = RoundedCornerShape(12.dp)

fun interface SettingsStudyAheadSaver {
    fun save()
}

data class SettingsStudyAheadPanelModel(
    val title: String,
    val body: String,
    val minutesLabel: String,
    val minutesInput: EditText,
    val saveLabel: String,
    val onSave: SettingsStudyAheadSaver,
)

internal fun studyAheadSettingsPanelView(
    activity: MainActivitySettings,
    model: SettingsStudyAheadPanelModel,
): View {
    return ComposeView(activity).apply {
        layoutParams = settingsPanelLayoutParams(activity)
        setContent {
            MaterialTheme {
                SettingsStudyAheadPanel(model)
            }
        }
    }
}

@Composable
fun SettingsStudyAheadPanel(model: SettingsStudyAheadPanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = StudyAheadPanelShape,
        color = StudyAheadWhite,
        border = BorderStroke(1.dp, StudyAheadPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = StudyAheadInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.body,
                color = StudyAheadMuted,
                fontSize = 15.sp
            )
            Text(
                text = model.minutesLabel,
                color = StudyAheadInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            AndroidView(
                factory = { model.minutesInput },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            )
            Button(
                onClick = { model.onSave.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = StudyAheadButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudyAheadPinkDark,
                    contentColor = StudyAheadWhite
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
