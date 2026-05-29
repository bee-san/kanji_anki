@file:JvmName("MainActivityStudyWritingPrimaryActionsCompose")

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class WritingPrimaryActionsView private constructor(
    private val sharedState: WritingActionsBarState?,
) {
    private var model by mutableStateOf(WritingPrimaryActionsModel.initial())

    constructor(context: Context) : this(null)

    internal constructor(context: Context, sharedState: WritingActionsBarState) : this(sharedState)

    fun render(model: WritingPrimaryActionsModel) {
        if (sharedState == null) {
            this.model = model
        } else {
            sharedState.primaryActions = model
        }
    }

    fun currentModel(): WritingPrimaryActionsModel = sharedState?.primaryActions ?: model
}

@Composable
internal fun WritingPrimaryActions(model: WritingPrimaryActionsModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (model.checkVisible) {
            WritingPrimaryButton(
                label = model.checkText,
                enabled = model.checkEnabled,
                onClick = { model.onCheck.run() },
                modifier = Modifier.weight(1f)
            )
        }
        if (model.downloadVisible) {
            WritingSecondaryButton(
                label = model.downloadText,
                onClick = { model.onDownload.run() },
                modifier = Modifier.weight(1f)
            )
        }
        if (model.nextVisible) {
            WritingPrimaryButton(
                label = model.nextText,
                enabled = true,
                onClick = { model.onNext.run() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun WritingPrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 62.dp,
) {
    StudyPrimaryActionButton(label, onClick, modifier, enabled, minHeight)
}

@Composable
internal fun WritingSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 62.dp,
    enabled: Boolean = true,
) {
    StudySecondaryActionButton(label, onClick, modifier, enabled, minHeight)
}
