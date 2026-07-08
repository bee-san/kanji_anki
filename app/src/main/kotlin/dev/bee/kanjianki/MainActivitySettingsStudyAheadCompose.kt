@file:JvmName("MainActivitySettingsStudyAheadCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.text.KeyboardOptions

private val StudyAheadInk: Color @Composable get() = KaniUiTokens.Ink
private val StudyAheadMuted: Color @Composable get() = KaniUiTokens.Muted
private val StudyAheadPanelBorder: Color @Composable get() = KaniUiTokens.PanelBorder
private val StudyAheadWhite: Color @Composable get() = KaniUiTokens.White
private val StudyAheadPanelShape = KaniUiTokens.PanelShape

object SettingsStudyAheadTestTags {
    const val MINUTES_INPUT = "settings-study-ahead-minutes-input"
}

@Composable
fun SettingsStudyAheadPanel(model: SettingsStudyAheadPanelModel) {
    var minutesText by rememberSaveable { mutableStateOf(model.initialMinutesText) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = StudyAheadPanelShape,
        color = StudyAheadWhite,
        border = BorderStroke(1.dp, StudyAheadPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
            OutlinedTextField(
                value = minutesText,
                onValueChange = { minutesText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsStudyAheadTestTags.MINUTES_INPUT)
                    .semantics { contentDescription = model.minutesLabel },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = StudyAheadInk,
                    fontSize = 20.sp
                )
            )
            KaniPrimaryButton(label = model.saveLabel) { model.onSave.save(minutesText) }
        }
    }
}
