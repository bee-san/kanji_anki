@file:JvmName("MainActivityStudyWritingToolActionsCompose")

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

class WritingToolActionsView private constructor(
    private val sharedState: WritingActionsBarState?,
) {
    private var model by mutableStateOf(WritingToolActionsModel.initial())

    constructor(context: Context) : this(null)

    internal constructor(context: Context, sharedState: WritingActionsBarState) : this(sharedState)

    fun render(model: WritingToolActionsModel) {
        if (sharedState == null) {
            this.model = model
        } else {
            sharedState.toolActions = model
        }
    }

    fun currentModelForTests(): WritingToolActionsModel = sharedState?.toolActions ?: model
}

@Composable
internal fun WritingToolActions(model: WritingToolActionsModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WritingSecondaryButton(
            label = "Erase",
            onClick = { model.onErase.run() },
            modifier = Modifier.weight(1f),
            minHeight = 58.dp
        )
        WritingSecondaryButton(
            label = "Undo",
            onClick = { model.onUndo.run() },
            modifier = Modifier.weight(1f),
            minHeight = 58.dp,
            enabled = model.undoEnabled
        )
        if (model.hintVisible) {
            WritingSecondaryButton(
                label = model.hintText,
                onClick = { model.onHint.run() },
                modifier = Modifier.weight(1f),
                minHeight = 58.dp
            )
        }
    }
}
