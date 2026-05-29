@file:JvmName("MainActivitySettingsLadderThresholdCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
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

object SettingsLadderThresholdTestTags {
    const val PROMOTION_DAYS_INPUT = "settings-ladder-threshold-promotion-days-input"
    const val FAIL_STREAK_INPUT = "settings-ladder-threshold-fail-streak-input"
}

@Composable
fun SettingsLadderThresholdPanel(model: SettingsLadderThresholdPanelModel) {
    var promotionDaysText by rememberSaveable { mutableStateOf(model.initialPromotionDaysText) }
    var failStreakText by rememberSaveable { mutableStateOf(model.initialFailStreakText) }
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
            LadderThresholdInput(
                label = model.promotionDaysLabel,
                value = promotionDaysText,
                testTag = SettingsLadderThresholdTestTags.PROMOTION_DAYS_INPUT,
                onValueChange = { promotionDaysText = it }
            )
            LadderThresholdInput(
                label = model.failStreakLabel,
                value = failStreakText,
                testTag = SettingsLadderThresholdTestTags.FAIL_STREAK_INPUT,
                onValueChange = { failStreakText = it }
            )
            KaniOutlinedButton(
                label = model.defaultsLabel,
                onClick = {
                    promotionDaysText = model.defaultPromotionDaysText
                    failStreakText = model.defaultFailStreakText
                }
            )
            KaniPrimaryButton(label = model.saveLabel) { model.onSave.save(promotionDaysText, failStreakText) }
        }
    }
}

@Composable
private fun LadderThresholdInput(
    label: String,
    value: String,
    testTag: String,
    onValueChange: (String) -> Unit,
) {
    Text(
        text = label,
        color = KaniUiTokens.Ink,
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = KaniUiTokens.Ink,
            fontSize = 20.sp
        )
    )
}
