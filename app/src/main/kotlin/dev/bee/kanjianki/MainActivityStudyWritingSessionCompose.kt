@file:JvmName("MainActivityStudyWritingSessionCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

data class WritingSessionCardModel(
    val promptHeader: WritingPromptHeaderModel,
    val answerPanel: View,
    val writingTitle: String,
    val writingTitleColor: Int,
    val statusView: View,
    val padPanel: View,
    val resultStatusView: View,
)

internal fun writingSessionCardView(activity: MainActivityStudy, model: WritingSessionCardModel): View {
    return ComposeView(activity).apply {
        setContent {
            MaterialTheme {
                WritingSessionCard(model)
            }
        }
    }
}

@Composable
fun WritingSessionCard(model: WritingSessionCardModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.Start
        ) {
            WritingPromptHeader(model.promptHeader)
            WritingEmbeddedView(model.answerPanel, Modifier.padding(top = 12.dp, bottom = 10.dp))
            WritingSectionTitle(title = model.writingTitle, color = model.writingTitleColor)
            WritingEmbeddedView(model.statusView)
            WritingEmbeddedView(model.padPanel, Modifier.padding(top = 12.dp, bottom = 10.dp))
            WritingEmbeddedView(model.resultStatusView)
        }
    }
}

@Composable
private fun WritingEmbeddedView(view: View, modifier: Modifier = Modifier) {
    key(view) {
        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = {
                detachFromParent(view)
                view
            }
        )
    }
}

private fun detachFromParent(view: View) {
    (view.parent as? ViewGroup)?.removeView(view)
}
