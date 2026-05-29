@file:JvmName("MainActivitySettingsLearningCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LearningStepsInk = KaniUiTokens.Ink
private val LearningStepsMuted = KaniUiTokens.Muted
private val LearningStepsPanelBorder = KaniUiTokens.PanelBorder
private val LearningStepsWhite = KaniUiTokens.White
private val LearningStepsPanelShape = KaniUiTokens.PanelShape

@Composable
fun SettingsLearningStepsPanel(model: SettingsLearningStepsPanelModel) {
    var newStepsText by rememberSaveable { mutableStateOf(model.initialNewStepsText) }
    var reviewStepsText by rememberSaveable { mutableStateOf(model.initialReviewStepsText) }
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
            LearningStepsInput(
                label = model.newCardsLabel,
                value = newStepsText,
                testTag = SettingsLearningStepsTestTags.NEW_STEPS_INPUT,
                onValueChange = { newStepsText = it }
            )
            LearningStepsInput(
                label = model.reviewMissesLabel,
                value = reviewStepsText,
                testTag = SettingsLearningStepsTestTags.REVIEW_STEPS_INPUT,
                onValueChange = { reviewStepsText = it }
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                LearningStepsOutlinedButton(
                    label = model.ankiDefaultLabel,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        newStepsText = model.defaultNewStepsText
                        reviewStepsText = model.defaultReviewStepsText
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                LearningStepsOutlinedButton(
                    label = model.sameStepsLabel,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        newStepsText = model.defaultNewStepsText
                        reviewStepsText = model.defaultNewStepsText
                    }
                )
            }
            KaniPrimaryButton(label = model.saveLabel) { model.onSave.save(newStepsText, reviewStepsText) }
        }
    }
}

@Composable
private fun LearningStepsInput(
    label: String,
    value: String,
    testTag: String,
    onValueChange: (String) -> Unit,
) {
    Text(
        text = label,
        color = LearningStepsInk,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics { contentDescription = label },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = LearningStepsInk,
            fontSize = 20.sp
        )
    )
}

@Composable
private fun LearningStepsOutlinedButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    KaniOutlinedButton(label = label, modifier = modifier, onClick = onClick)
}
