@file:JvmName("MainActivitySettingsAnkiSourceFrequencyRangeCompose")

package dev.bee.kanjianki

import android.view.View
import android.widget.EditText
import android.widget.SeekBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.bee.kanjianki.core.FrequencyRetentionRanges
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.core.SettingsTextCopy
import java.util.Locale

private val FrequencyInk = Color(0xFF2D1635)
private val FrequencyMuted = Color(0xFF6C5674)
private val FrequencyTeal = Color(0xFF24756C)
private val FrequencyPinkDark = Color(0xFFDA3A7A)
private val FrequencyPanelBorder = Color(0xFFFFC7DE)
private val FrequencyWhite = Color(0xFFFFFFFF)
private val FrequencyPanelShape = RoundedCornerShape(24.dp)
private val FrequencyButtonShape = RoundedCornerShape(12.dp)

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
    val minRankSlider: SeekBar,
    val maximumRankLabel: String,
    val maxRankSlider: SeekBar,
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
                slider = model.minRankSlider,
                initialRank = minRank,
                currentRank = { model.selectedRanks[0] },
                onRankChanged = { rank ->
                    val nextMin = minOf(rank, model.selectedRanks[1])
                    model.selectedRanks[0] = nextMin
                    model.minRankInput.setText(formatRank(nextMin))
                    minRank = nextMin
                }
            )
            RankSlider(
                label = model.maximumRankLabel,
                slider = model.maxRankSlider,
                initialRank = maxRank,
                currentRank = { model.selectedRanks[1] },
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
    slider: SeekBar,
    initialRank: Int,
    currentRank: () -> Int,
    onRankChanged: (Int) -> Unit,
) {
    Text(
        text = label,
        color = FrequencyMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
    AndroidView(
        factory = {
            slider.apply {
                max = FrequencyRetentionRanges.MAX_RANK - FrequencyRetentionRanges.MIN_RANK
                progress = SettingsInputRules.rankSliderProgress(initialRank)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        onRankChanged(SettingsInputRules.rankFromSliderProgress(progress))
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        seekBar.progress = SettingsInputRules.rankSliderProgress(currentRank())
                    }
                })
            }
        },
        update = {
            it.progress = SettingsInputRules.rankSliderProgress(currentRank())
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    )
}
