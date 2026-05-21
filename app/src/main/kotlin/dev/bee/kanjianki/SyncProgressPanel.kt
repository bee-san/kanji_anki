package dev.bee.kanjianki

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.SyncProgressCopy
import dev.bee.kanjianki.sync.SyncProgress

private val INK = 0xFF2D1635.toInt()
private val MUTED = 0xFF6C5674.toInt()
private val SyncProgressTrack = Color(0xFFFBDDEC)
private val SyncProgressFill = Color(0xFFF82D72)

internal data class SyncProgressPanelState(
    val stage: String = "Finding note type",
    val count: String = "Reading collection details.",
    val rate: String = "",
    val progressIndeterminate: Boolean = true,
    val progressMax: Int = 1000,
    val progressValue: Int = 0,
    val progressDescription: String = "Sync progress"
)

class SyncProgressPanel @JvmOverloads constructor(
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private var scanStartedAt: Long = 0L
    private var lastScannedCards: Int = -1
    private var lastTotalCards: Int = -1
    internal var state by mutableStateOf(SyncProgressPanelState())
        private set

    fun render(progress: SyncProgress) {
        val currentStage = progress.coreStage()
        val stageTitle = SyncProgressCopy.stageTitle(currentStage)
        if (progress.totalKnown()) {
            lastScannedCards = progress.scannedCards
            lastTotalCards = progress.totalCards
            if (scanStartedAt <= 0L) {
                scanStartedAt = elapsedRealtime()
            }
        }
        state = if (lastTotalCards >= 0) {
            renderKnownTotal(currentStage)
        } else {
            SyncProgressPanelState(
                stage = stageTitle,
                count = SyncProgressCopy.stageBody(currentStage),
                rate = "",
                progressIndeterminate = true,
                progressMax = 1000,
                progressValue = 0,
                progressDescription = "Sync progress: $stageTitle"
            )
        }
    }

    private fun renderKnownTotal(currentStage: SyncProgressCopy.Stage?): SyncProgressPanelState {
        val cardText = SyncProgressCopy.cardProgressText(lastScannedCards, lastTotalCards)
        return SyncProgressPanelState(
            stage = SyncProgressCopy.stageTitle(currentStage),
            count = cardText,
            rate = SyncProgressCopy.scanRateText(
                currentStage,
                lastScannedCards,
                lastTotalCards,
                elapsedRealtime() - scanStartedAt
            ),
            progressIndeterminate = false,
            progressMax = 1000,
            progressValue = SyncProgressCopy.progressPermille(lastScannedCards, lastTotalCards),
            progressDescription = "Sync progress: $cardText"
        )
    }
}

@Composable
internal fun SyncProgressScreen(title: String, progressPanel: SyncProgressPanel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        SyncProgressTitle(title)
        SyncProgressPanelContent(progressPanel.state)
    }
}

@Composable
internal fun SyncProgressTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        color = Color(INK),
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 36.sp,
        style = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = true)
        )
    )
}

@Composable
private fun SyncProgressPanelContent(state: SyncProgressPanelState) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        SyncProgressText(
            value = state.stage,
            sizeSp = 22f,
            color = INK,
            bold = true,
            modifier = Modifier.fillMaxWidth()
        )
        SyncProgressBar(
            indeterminate = state.progressIndeterminate,
            progressMax = state.progressMax,
            progressValue = state.progressValue,
            contentDescription = state.progressDescription,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )
        SyncProgressText(
            value = state.count,
            sizeSp = 17f,
            color = MUTED,
            bold = false,
            modifier = Modifier.fillMaxWidth()
        )
        SyncProgressText(
            value = state.rate,
            sizeSp = 15f,
            color = MUTED,
            bold = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SyncProgressText(
    value: String,
    sizeSp: Float,
    color: Int,
    bold: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = value,
        modifier = modifier.padding(vertical = 4.dp),
        color = Color(color),
        fontSize = sizeSp.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    )
}

@Composable
private fun SyncProgressBar(
    indeterminate: Boolean,
    progressMax: Int,
    progressValue: Int,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    if (indeterminate) {
        LinearProgressIndicator(
            modifier = modifier.semantics { this.contentDescription = contentDescription },
            color = SyncProgressFill,
            trackColor = SyncProgressTrack
        )
        return
    }
    val progressFraction = if (progressMax <= 0) 0f else progressValue.toFloat() / progressMax.toFloat()
    LinearProgressIndicator(
        progress = { progressFraction },
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
            progressBarRangeInfo = ProgressBarRangeInfo(progressFraction, 0f..1f)
        },
        color = SyncProgressFill,
        trackColor = SyncProgressTrack
    )
}
