@file:JvmName("MainActivitySettingsDeckLimitsCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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

@Composable
fun SettingsDeckLimitsPanel(model: SettingsDeckLimitsPanelModel) {
    var newPerDayText by rememberSaveable { mutableStateOf(model.initialNewPerDayText) }
    var activeQueueCapText by rememberSaveable { mutableStateOf(model.initialActiveQueueCapText) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = KaniUiTokens.PanelShape,
        color = KaniUiTokens.White,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text(
                text = model.title,
                color = KaniUiTokens.Ink,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = model.body,
                color = KaniUiTokens.Muted,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedTextField(
                value = newPerDayText,
                onValueChange = { newPerDayText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag(SettingsDeckLimitsTestTags.NEW_PER_DAY_INPUT)
                    .semantics { contentDescription = model.newPerDayLabel },
                label = { Text(model.newPerDayLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = KaniUiTokens.Ink,
                    fontSize = 20.sp,
                ),
            )
            OutlinedTextField(
                value = activeQueueCapText,
                onValueChange = { activeQueueCapText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag(SettingsDeckLimitsTestTags.ACTIVE_QUEUE_CAP_INPUT)
                    .semantics { contentDescription = model.activeQueueCapLabel },
                label = { Text(model.activeQueueCapLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = KaniUiTokens.Ink,
                    fontSize = 20.sp,
                ),
            )
            KaniPrimaryButton(
                label = model.saveLabel,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                model.onSave.save(newPerDayText, activeQueueCapText)
            }
        }
    }
}
