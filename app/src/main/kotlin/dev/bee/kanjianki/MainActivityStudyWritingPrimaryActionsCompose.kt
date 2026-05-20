@file:JvmName("MainActivityStudyWritingPrimaryActionsCompose")

package dev.bee.kanjianki

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WritingPrimaryColor = Color(0xFFDA3A7A)
private val WritingPrimaryBorder = Color(0xFFFFADCD)
private val WritingSecondaryFill = Color(0xFFFFF5FA)

data class WritingPrimaryActionsModel(
    val checkText: String,
    val checkVisible: Boolean,
    val checkEnabled: Boolean,
    val downloadText: String,
    val downloadVisible: Boolean,
    val nextText: String,
    val nextVisible: Boolean,
    val onCheck: Runnable,
    val onDownload: Runnable,
    val onNext: Runnable,
) {
    companion object {
        fun initial(): WritingPrimaryActionsModel {
            return WritingPrimaryActionsModel(
                checkText = "Check",
                checkVisible = true,
                checkEnabled = true,
                downloadText = "Download checker",
                downloadVisible = true,
                nextText = MainActivityBase.LABEL_PASS,
                nextVisible = false,
                onCheck = Runnable {},
                onDownload = Runnable {},
                onNext = Runnable {}
            )
        }
    }
}

class WritingPrimaryActionsView(context: Context) : FrameLayout(context) {
    private var model by mutableStateOf(WritingPrimaryActionsModel.initial())

    init {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(
            ComposeView(context).apply {
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setContent {
                    MaterialTheme {
                        Surface {
                            WritingPrimaryActions(model)
                        }
                    }
                }
            }
        )
    }

    fun render(model: WritingPrimaryActionsModel) {
        this.model = model
    }
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
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WritingPrimaryColor,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFFFC2D8),
            disabledContentColor = Color.White
        ),
        border = BorderStroke(1.dp, WritingPrimaryBorder),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        WritingActionButtonText(label = label, color = Color.White)
    }
}

@Composable
internal fun WritingSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 62.dp,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, WritingPrimaryBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = WritingSecondaryFill,
            contentColor = WritingPrimaryColor
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        WritingActionButtonText(label = label, color = WritingPrimaryColor)
    }
}

@Composable
private fun WritingActionButtonText(label: String, color: Color) {
    Text(
        text = label,
        color = color,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    )
}
