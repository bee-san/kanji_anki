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

object SettingsStudyAheadTestTags {
    const val MINUTES_INPUT = "settings-study-ahead-minutes-input"
}

@Composable
fun SettingsStudyAheadPanel(model: SettingsStudyAheadPanelModel) {
    var minutesText by rememberSaveable { mutableStateOf(model.initialMinutesText) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = KaniUiTokens.PanelShape,
        color = KaniUiTokens.White,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = KaniUiTokens.Ink,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.body,
                color = KaniUiTokens.Muted,
                fontSize = 15.sp
            )
            Text(
                text = model.minutesLabel,
                color = KaniUiTokens.Ink,
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
                    color = KaniUiTokens.Ink,
                    fontSize = 20.sp
                )
            )
            KaniPrimaryButton(label = model.saveLabel) { model.onSave.save(minutesText) }
        }
    }
}
