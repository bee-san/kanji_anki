package dev.bee.kanjianki

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView

internal class WritingActionsBarState {
    var toolActions by mutableStateOf(WritingToolActionsModel.initial())
    var primaryActions by mutableStateOf(WritingPrimaryActionsModel.initial())
    var fallbackActions by mutableStateOf(WritingFallbackActionsModel.initial())
}

internal fun writingActionsBarView(
    context: Context,
    state: WritingActionsBarState,
): View {
    return FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        addView(
            ComposeView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setContent {
                    MaterialTheme {
                        Surface {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                WritingToolActions(state.toolActions)
                                WritingPrimaryActions(state.primaryActions)
                                WritingFallbackActions(state.fallbackActions)
                            }
                        }
                    }
                }
            },
        )
    }
}
