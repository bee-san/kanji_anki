@file:JvmName("MainActivityStudyWritingPadCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val WritingPadFill: Color @Composable get() = KaniTheme.colors.panel
private val WritingPadBorder: Color @Composable get() = KaniTheme.colors.border

@Composable
internal fun WritingPadPanel(
    drawingPad: View,
    maxSizePx: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = KaniUiTokens.StudyShapeMedium,
        color = WritingPadFill,
        border = BorderStroke(1.dp, WritingPadBorder)
    ) {
        key(drawingPad, maxSizePx) {
            val night = KaniTheme.colors.isDark
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                factory = { context ->
                    MainActivityUiSupport.SquarePadFrame(context, maxSizePx).apply {
                        attachDrawingPad(drawingPad)
                    }
                },
                update = { frame ->
                    frame.attachDrawingPad(drawingPad)
                    (drawingPad as? DrawingPadView)?.setNightMode(night)
                }
            )
        }
    }
}

private fun ViewGroup.attachDrawingPad(drawingPad: View) {
    if (childCount == 1 && getChildAt(0) === drawingPad) {
        return
    }
    drawingPad.detachFromParent()
    removeAllViews()
    addView(drawingPad)
}

private fun View.detachFromParent() {
    (parent as? ViewGroup)?.removeView(this)
}
