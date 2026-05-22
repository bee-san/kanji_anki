package dev.bee.kanjianki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

internal class WritingActionsBarState {
    var toolActions by mutableStateOf(WritingToolActionsModel.initial())
    var primaryActions by mutableStateOf(WritingPrimaryActionsModel.initial())
    var fallbackActions by mutableStateOf(WritingFallbackActionsModel.initial())
}

@Composable
internal fun WritingActionsBar(
    state: WritingActionsBarState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WritingToolActions(state.toolActions)
        WritingPrimaryActions(state.primaryActions)
        WritingFallbackActions(state.fallbackActions)
    }
}
