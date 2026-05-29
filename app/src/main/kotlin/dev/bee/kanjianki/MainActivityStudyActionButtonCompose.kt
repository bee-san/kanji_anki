@file:JvmName("MainActivityStudyActionButtonCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val StudyActionPrimaryColor = Color(MainActivityUiSupport.STUDY_PINK_DARK)
internal val StudyActionBorderColor = Color(MainActivityUiSupport.STUDY_BORDER)
internal val StudyActionSecondaryFill = Color(0xFFFFF5FA)
private val StudyActionDisabledBorder = Color(0xFFFFD5E6)
private val StudyActionDisabledText = Color(0xFF9F8A98)
private val StudyActionDisabledPrimaryFill = Color(0xFFFFC2D8)

@Composable
internal fun StudyPrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 62.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    leadingContent: @Composable RowScope.() -> Unit = {},
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StudyActionPrimaryColor,
            contentColor = Color.White,
            disabledContainerColor = StudyActionDisabledPrimaryFill,
            disabledContentColor = Color.White,
        ),
        border = BorderStroke(1.dp, StudyActionBorderColor),
        contentPadding = contentPadding,
    ) {
        leadingContent()
        StudyActionButtonText(label = label, color = Color.White)
    }
}

@Composable
internal fun StudySecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 62.dp,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (enabled) StudyActionBorderColor else StudyActionDisabledBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = StudyActionSecondaryFill,
            contentColor = StudyActionPrimaryColor,
            disabledContainerColor = Color(0xFFFFF9FC),
            disabledContentColor = StudyActionDisabledText,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        StudyActionButtonText(
            label = label,
            color = if (enabled) StudyActionPrimaryColor else StudyActionDisabledText,
        )
    }
}

@Composable
private fun StudyActionButtonText(label: String, color: Color) {
    Text(
        text = label,
        color = color,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
    )
}
