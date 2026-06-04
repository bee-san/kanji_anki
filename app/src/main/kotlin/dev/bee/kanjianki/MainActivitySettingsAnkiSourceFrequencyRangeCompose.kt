@file:JvmName("MainActivitySettingsAnkiSourceFrequencyRangeCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import dev.bee.kanjianki.core.FrequencyRetentionRanges
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.core.SettingsTextCopy
import java.util.Locale
import kotlin.math.roundToInt

private val FrequencyInk = KaniUiTokens.Ink
private val FrequencyMuted = KaniUiTokens.Muted
private val FrequencyTeal = KaniUiTokens.Teal
private val FrequencyPanelBorder = KaniUiTokens.PanelBorder
private val FrequencyWhite = KaniUiTokens.White
private val FrequencyPanelShape = KaniUiTokens.PanelShape

@Composable
fun SettingsFrequencyRangePanel(model: SettingsFrequencyRangePanelModel) {
    var minRank by rememberSaveable { mutableIntStateOf(model.selectedRanks[0]) }
    var maxRank by rememberSaveable { mutableIntStateOf(model.selectedRanks[1]) }
    var minRankText by rememberSaveable { mutableStateOf(model.initialMinRankText) }
    var maxRankText by rememberSaveable { mutableStateOf(model.initialMaxRankText) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FrequencyPanelShape,
        color = FrequencyWhite,
        border = BorderStroke(1.dp, FrequencyPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = FrequencyInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = SettingsTextCopy.frequencyRangeStatusText(minRank, maxRank),
                color = FrequencyTeal,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.body,
                color = FrequencyMuted,
                fontSize = 15.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RankInput(
                    label = model.minRankLabel,
                    value = minRankText,
                    tag = SettingsFrequencyRangeTestTags.MIN_RANK_INPUT,
                    modifier = Modifier.weight(1f),
                    onValueChange = { value ->
                        minRankText = value
                        val parsed = value.toIntOrNull()
                        if (parsed != null && SettingsInputRules.validRank(parsed)) {
                            val nextMin = minOf(parsed, maxRank)
                            model.selectedRanks[0] = nextMin
                            minRankText = formatRank(nextMin)
                            minRank = nextMin
                        }
                    }
                )
                RankInput(
                    label = model.maxRankLabel,
                    value = maxRankText,
                    tag = SettingsFrequencyRangeTestTags.MAX_RANK_INPUT,
                    modifier = Modifier.weight(1f),
                    onValueChange = { value ->
                        maxRankText = value
                        val parsed = value.toIntOrNull()
                        if (parsed != null && SettingsInputRules.validRank(parsed)) {
                            val nextMax = maxOf(parsed, minRank)
                            model.selectedRanks[1] = nextMax
                            maxRankText = formatRank(nextMax)
                            maxRank = nextMax
                        }
                    }
                )
            }
            RankSlider(
                label = model.minimumRankLabel,
                rank = minRank,
                tag = SettingsFrequencyRangeTestTags.MIN_RANK_SLIDER,
                onRankChanged = { rank ->
                    val nextMin = minOf(rank, maxRank)
                    model.selectedRanks[0] = nextMin
                    minRankText = formatRank(nextMin)
                    minRank = nextMin
                }
            )
            RankSlider(
                label = model.maximumRankLabel,
                rank = maxRank,
                tag = SettingsFrequencyRangeTestTags.MAX_RANK_SLIDER,
                onRankChanged = { rank ->
                    val nextMax = maxOf(rank, minRank)
                    model.selectedRanks[1] = nextMax
                    maxRankText = formatRank(nextMax)
                    maxRank = nextMax
                }
            )
            KaniPrimaryButton(label = model.saveLabel) { model.onSave.save(minRankText, maxRankText) }
        }
    }
}

private fun formatRank(rank: Int): String {
    return String.format(Locale.ROOT, "%d", rank)
}

@Composable
private fun RankInput(
    label: String,
    value: String,
    tag: String,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = FrequencyInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
                .semantics { contentDescription = label },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = FrequencyInk,
                fontSize = 22.sp
            )
        )
    }
}

@Composable
private fun RankSlider(
    label: String,
    rank: Int,
    tag: String,
    onRankChanged: (Int) -> Unit,
) {
    Text(
        text = label,
        color = FrequencyMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
    Slider(
        value = SettingsInputRules.rankSliderProgress(rank).toFloat(),
        onValueChange = { progress ->
            onRankChanged(SettingsInputRules.rankFromSliderProgress(progress.roundToInt()))
        },
        valueRange = 0f..SettingsInputRules.rankSliderProgress(FrequencyRetentionRanges.MAX_RANK).toFloat(),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(tag)
            .semantics { contentDescription = label }
    )
}
