@file:JvmName("MainActivityStudyWritingToolActionsCompose")

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

class WritingToolActionsView private constructor(
    context: Context,
    private val sharedState: WritingActionsBarState?,
    private val mountStandaloneContent: Boolean,
) : FrameLayout(context) {
    private var model by mutableStateOf(WritingToolActionsModel.initial())

    constructor(context: Context) : this(context, null, true)

    internal constructor(context: Context, sharedState: WritingActionsBarState) : this(context, sharedState, false)

    init {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        if (mountStandaloneContent) {
            addView(
                ComposeView(context).apply {
                    layoutParams = LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setContent {
                        MaterialTheme {
                            Surface {
                                WritingToolActions(model)
                            }
                        }
                    }
                }
            )
        }
    }

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
