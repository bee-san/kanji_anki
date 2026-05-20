package dev.bee.kanjianki

import android.content.Context
import android.os.SystemClock
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.bee.kanjianki.core.SyncProgressCopy
import dev.bee.kanjianki.sync.SyncProgress

private val INK = 0xFF2D1635.toInt()
private val MUTED = 0xFF6C5674.toInt()

private data class SyncProgressPanelState(
    val stage: String = "Finding note type",
    val count: String = "Reading collection details.",
    val rate: String = "",
    val progressIndeterminate: Boolean = true,
    val progressMax: Int = 1000,
    val progressValue: Int = 0,
    val progressDescription: String = "Sync progress"
)

internal fun syncProgressScreenView(context: Context, title: String, progressPanel: SyncProgressPanel): View {
    return ComposeView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                SyncProgressScreen(
                    title = title,
                    progressPanel = progressPanel
                )
            }
        }
    }
}

class SyncProgressPanel(context: Context) : FrameLayout(context) {
    private var scanStartedAt: Long = 0L
    private var lastScannedCards: Int = -1
    private var lastTotalCards: Int = -1
    private var state by mutableStateOf(SyncProgressPanelState())

    init {
        addView(
            ComposeView(context).apply {
                layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setContent {
                    MaterialTheme {
                        SyncProgressPanelContent(state)
                    }
                }
            }
        )
    }

    fun render(progress: SyncProgress) {
        val currentStage = progress.coreStage()
        val stageTitle = SyncProgressCopy.stageTitle(currentStage)
        if (progress.totalKnown()) {
            lastScannedCards = progress.scannedCards
            lastTotalCards = progress.totalCards
            if (scanStartedAt <= 0L) {
                scanStartedAt = SystemClock.elapsedRealtime()
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

    private fun renderKnownTotal(currentStage: SyncProgressCopy.Stage): SyncProgressPanelState {
        val cardText = SyncProgressCopy.cardProgressText(lastScannedCards, lastTotalCards)
        return SyncProgressPanelState(
            stage = SyncProgressCopy.stageTitle(currentStage),
            count = cardText,
            rate = SyncProgressCopy.scanRateText(
                currentStage,
                lastScannedCards,
                lastTotalCards,
                SystemClock.elapsedRealtime() - scanStartedAt
            ),
            progressIndeterminate = false,
            progressMax = 1000,
            progressValue = SyncProgressCopy.progressPermille(lastScannedCards, lastTotalCards),
            progressDescription = "Sync progress: $cardText"
        )
    }
}

@Composable
internal fun SyncProgressScreen(title: String, progressPanel: View) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        SyncProgressTitle(title)
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = {
                detachFromParent(progressPanel)
                progressPanel
            },
            update = {
                detachFromParent(progressPanel)
            }
        )
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
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                textSize = sizeSp
                setTextColor(color)
                setIncludeFontPadding(false)
                setPadding(0, context.dp(4), 0, context.dp(4))
                if (bold) {
                    setTypeface(Typeface.DEFAULT_BOLD)
                }
            }
        },
        update = { view ->
            view.text = value
        }
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
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                this.contentDescription = contentDescription
            }
        },
        update = { view ->
            view.isIndeterminate = indeterminate
            if (!indeterminate) {
                view.max = progressMax
                view.progress = progressValue
            }
            view.contentDescription = contentDescription
        }
    )
}

private fun Context.dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)

private fun detachFromParent(view: View) {
    (view.parent as? ViewGroup)?.removeView(view)
}
