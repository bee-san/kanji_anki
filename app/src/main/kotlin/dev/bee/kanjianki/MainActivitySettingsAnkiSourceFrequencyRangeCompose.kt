@file:JvmName("MainActivitySettingsAnkiSourceFrequencyRangeCompose")

package dev.bee.kanjianki

import android.view.View
import android.widget.EditText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.bee.kanjianki.core.FrequencyRetentionRanges
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.core.SettingsTextCopy
import java.util.Locale
import kotlin.math.roundToInt

private val FrequencyInk = Color(0xFF2D1635)
private val FrequencyMuted = Color(0xFF6C5674)
private val FrequencyTeal = Color(0xFF24756C)
private val FrequencyPinkDark = Color(0xFFDA3A7A)
private val FrequencyPanelBorder = Color(0xFFFFC7DE)
private val FrequencyWhite = Color(0xFFFFFFFF)
private val FrequencyPanelShape = RoundedCornerShape(24.dp)
private val FrequencyButtonShape = RoundedCornerShape(12.dp)

object SettingsFrequencyRangeTestTags {
    const val MIN_RANK_SLIDER = "settings-frequency-min-rank-slider"
    const val MAX_RANK_SLIDER = "settings-frequency-max-rank-slider"
}

fun interface SettingsFrequencyRangeAction {
    fun run()
}

data class SettingsFrequencyRangePanelModel(
    val title: String,
    val body: String,
    val selectedRanks: IntArray,
    val minRankLabel: String,
    val minRankInput: EditText,
    val maxRankLabel: String,
    val maxRankInput: EditText,
    val minimumRankLabel: String,
    val maximumRankLabel: String,
    val saveLabel: String,
    val onSave: SettingsFrequencyRangeAction,
)

internal fun frequencyRangeSettingsPanelView(
    activity: MainActivitySettings,
    model: SettingsFrequencyRangePanelModel,
): View {
    return ComposeView(activity).apply {
        layoutParams = settingsPanelLayoutParams(activity)
        setContent {
            MaterialTheme {
                SettingsFrequencyRangePanel(model)
            }
        }
    }
}

@Composable
fun SettingsFrequencyRangePanel(model: SettingsFrequencyRangePanelModel) {
    var minRank by remember { mutableIntStateOf(model.selectedRanks[0]) }
    var maxRank by remember { mutableIntStateOf(model.selectedRanks[1]) }

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
                RankInput(model.minRankLabel, model.minRankInput, Modifier.weight(1f))
                RankInput(model.maxRankLabel, model.maxRankInput, Modifier.weight(1f))
            }
            RankSlider(
                label = model.minimumRankLabel,
                rank = minRank,
                tag = SettingsFrequencyRangeTestTags.MIN_RANK_SLIDER,
                onRankChanged = { rank ->
                    val nextMin = minOf(rank, model.selectedRanks[1])
                    model.selectedRanks[0] = nextMin
                    model.minRankInput.setText(formatRank(nextMin))
                    minRank = nextMin
                }
            )
            RankSlider(
                label = model.maximumRankLabel,
                rank = maxRank,
                tag = SettingsFrequencyRangeTestTags.MAX_RANK_SLIDER,
                onRankChanged = { rank ->
                    val nextMax = maxOf(rank, model.selectedRanks[0])
                    model.selectedRanks[1] = nextMax
                    model.maxRankInput.setText(formatRank(nextMax))
                    maxRank = nextMax
                }
            )
            Button(
                onClick = { model.onSave.run() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = FrequencyButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FrequencyPinkDark,
                    contentColor = FrequencyWhite
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

private fun formatRank(rank: Int): String {
    return String.format(Locale.ROOT, "%d", rank)
}

@Composable
private fun RankInput(label: String, input: EditText, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = FrequencyInk,
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
