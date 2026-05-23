@file:JvmName("MainActivityStudyWritingPrimaryActionsCompose")

package dev.bee.kanjianki

import android.content.Context
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WritingPrimaryColor = Color(0xFFDA3A7A)
private val WritingPrimaryBorder = Color(0xFFFFADCD)
private val WritingSecondaryFill = Color(0xFFFFF5FA)
private val WritingDisabledBorder = Color(0xFFFFD5E6)
private val WritingDisabledText = Color(0xFF9F8A98)

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

    fun currentModelForTests(): WritingPrimaryActionsModel = sharedState?.primaryActions ?: model
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
        border = BorderStroke(1.dp, if (enabled) WritingPrimaryBorder else WritingDisabledBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = WritingSecondaryFill,
            contentColor = WritingPrimaryColor,
            disabledContainerColor = Color(0xFFFFF9FC),
            disabledContentColor = WritingDisabledText
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        WritingActionButtonText(label = label, color = if (enabled) WritingPrimaryColor else WritingDisabledText)
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
