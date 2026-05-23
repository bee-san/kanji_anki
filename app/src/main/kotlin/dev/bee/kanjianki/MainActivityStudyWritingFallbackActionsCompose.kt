@file:JvmName("MainActivityStudyWritingFallbackActionsCompose")

package dev.bee.kanjianki

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class WritingFallbackActionsView private constructor(
    private val sharedState: WritingActionsBarState?,
) {
    private var model by mutableStateOf(WritingFallbackActionsModel.initial())

    constructor(context: Context) : this(null)

    internal constructor(context: Context, sharedState: WritingActionsBarState) : this(sharedState)

    fun render(model: WritingFallbackActionsModel) {
        if (sharedState == null) {
            this.model = model
        } else {
            sharedState.fallbackActions = model
        }
    }

    fun currentModelForTests(): WritingFallbackActionsModel = sharedState?.fallbackActions ?: model
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
