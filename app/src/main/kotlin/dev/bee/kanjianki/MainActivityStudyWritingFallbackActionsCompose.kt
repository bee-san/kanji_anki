@file:JvmName("MainActivityStudyWritingFallbackActionsCompose")

package dev.bee.kanjianki

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp

data class WritingFallbackActionsModel(
    val replayVisible: Boolean,
    val manualOverrideVisible: Boolean,
    val practiceWithGuideVisible: Boolean,
    val onReplay: Runnable,
    val onManualOverride: Runnable,
    val onPracticeWithGuide: Runnable,
) {
    companion object {
        fun initial(): WritingFallbackActionsModel {
            return WritingFallbackActionsModel(
                replayVisible = false,
                manualOverrideVisible = false,
                practiceWithGuideVisible = false,
                onReplay = Runnable {},
                onManualOverride = Runnable {},
                onPracticeWithGuide = Runnable {}
            )
        }
    }
}

class WritingFallbackActionsView(context: Context) : FrameLayout(context) {
    private var model by mutableStateOf(WritingFallbackActionsModel.initial())

    init {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(
            ComposeView(context).apply {
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setContent {
                    MaterialTheme {
                        Surface {
                            WritingFallbackActions(model)
                        }
                    }
                }
            }
        )
    }

    fun render(model: WritingFallbackActionsModel) {
        this.model = model
    }
}

@Composable
internal fun WritingFallbackActions(model: WritingFallbackActionsModel) {
    if (!model.replayVisible && !model.manualOverrideVisible && !model.practiceWithGuideVisible) {
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp)
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (model.replayVisible) {
            WritingSecondaryButton(
                label = "Replay",
                onClick = { model.onReplay.run() },
                modifier = Modifier.weight(1f),
                minHeight = 56.dp
            )
        }
        if (model.manualOverrideVisible) {
            WritingSecondaryButton(
                label = "Mark right anyway",
                onClick = { model.onManualOverride.run() },
                modifier = Modifier.weight(1f),
                minHeight = 56.dp
            )
        }
        if (model.practiceWithGuideVisible) {
            WritingSecondaryButton(
                label = "Try again with full guide",
                onClick = { model.onPracticeWithGuide.run() },
                modifier = Modifier.weight(1f),
                minHeight = 56.dp
            )
        }
    }
}
