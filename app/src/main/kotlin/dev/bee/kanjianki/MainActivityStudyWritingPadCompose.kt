@file:JvmName("MainActivityStudyWritingPadCompose")

package dev.bee.kanjianki

import android.content.Context
import kotlin.math.roundToInt
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val WritingPadFill = Color(MainActivityUiSupport.STUDY_PANEL)
private val WritingPadBorder = Color(MainActivityUiSupport.STUDY_BORDER)

internal fun writingPadPanelView(context: Context, drawingPad: DrawingPadView, maxSizePx: Int): View {
    return ComposeView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, context.dp(12), 0, context.dp(10))
        }
        setContent {
            MaterialTheme {
                WritingPadPanel(drawingPad = drawingPad, maxSizePx = maxSizePx)
            }
        }
    }
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

@Composable
internal fun WritingPadPanel(drawingPad: DrawingPadView, maxSizePx: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = WritingPadFill,
        border = BorderStroke(1.dp, WritingPadBorder)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            factory = { context ->
                MainActivityUiSupport.SquarePadFrame(context, maxSizePx).apply {
                    addView(drawingPad)
                }
            }
        )
    }
}
