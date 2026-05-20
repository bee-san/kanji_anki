@file:JvmName("MainActivitySettingsLearningCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

private val LearningStepsInk = Color(0xFF2D1635)
private val LearningStepsMuted = Color(0xFF6C5674)
private val LearningStepsPinkDark = Color(0xFFDA3A7A)
private val LearningStepsPanelBorder = Color(0xFFFFC7DE)
private val LearningStepsButtonBorder = Color(0xFFEEBDDA)
private val LearningStepsWhite = Color(0xFFFFFFFF)
private val LearningStepsPanelShape = RoundedCornerShape(24.dp)
private val LearningStepsButtonShape = RoundedCornerShape(12.dp)

fun interface SettingsLearningStepsAction {
    fun run()
}

data class SettingsLearningStepsPanelModel(
    val title: String,
    val body: String,
    val newCardsLabel: String,
    val newStepsInput: EditText,
    val reviewMissesLabel: String,
    val reviewStepsInput: EditText,
    val ankiDefaultLabel: String,
    val sameStepsLabel: String,
    val saveLabel: String,
    val onUseAnkiDefault: SettingsLearningStepsAction,
    val onUseSameSteps: SettingsLearningStepsAction,
    val onSave: SettingsLearningStepsAction,
)

internal fun learningStepsSettingsPanelView(
    activity: MainActivitySettings,
    model: SettingsLearningStepsPanelModel,
): View {
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setContent {
            MaterialTheme {
                SettingsLearningStepsPanel(model)
            }
        }
    }
}

@Composable
fun SettingsLearningStepsPanel(model: SettingsLearningStepsPanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LearningStepsPanelShape,
        color = LearningStepsWhite,
        border = BorderStroke(1.dp, LearningStepsPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = LearningStepsInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.body,
                color = LearningStepsMuted,
                fontSize = 15.sp
            )
            LearningStepsInput(label = model.newCardsLabel, input = model.newStepsInput)
            LearningStepsInput(label = model.reviewMissesLabel, input = model.reviewStepsInput)
            Row(modifier = Modifier.fillMaxWidth()) {
                LearningStepsOutlinedButton(
                    label = model.ankiDefaultLabel,
                    modifier = Modifier.weight(1f),
                    onClick = { model.onUseAnkiDefault.run() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                LearningStepsOutlinedButton(
                    label = model.sameStepsLabel,
                    modifier = Modifier.weight(1f),
                    onClick = { model.onUseSameSteps.run() }
                )
            }
            Button(
                onClick = { model.onSave.run() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = LearningStepsButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LearningStepsPinkDark,
                    contentColor = LearningStepsWhite
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
private fun LearningStepsInput(label: String, input: EditText) {
    Text(
        text = label,
        color = LearningStepsInk,
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

@Composable
private fun LearningStepsOutlinedButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 50.dp),
        shape = LearningStepsButtonShape,
        border = BorderStroke(1.dp, LearningStepsButtonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = LearningStepsWhite,
            contentColor = LearningStepsInk
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
