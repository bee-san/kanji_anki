@file:JvmName("MainActivityStudyWritingPadCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val WritingPadFill = Color(MainActivityUiSupport.STUDY_PANEL)
private val WritingPadBorder = Color(MainActivityUiSupport.STUDY_BORDER)

@Composable
internal fun WritingPadPanel(
    drawingPad: View,
    maxSizePx: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = WritingPadFill,
        border = BorderStroke(1.dp, WritingPadBorder)
    ) {
        key(drawingPad, maxSizePx) {
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
