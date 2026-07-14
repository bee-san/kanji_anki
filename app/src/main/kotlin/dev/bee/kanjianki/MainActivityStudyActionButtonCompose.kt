@file:JvmName("MainActivityStudyActionButtonCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val StudyActionPrimaryColor: Color @Composable get() = KaniTheme.colors.primary
internal val StudyActionBorderColor: Color @Composable get() = KaniTheme.colors.border
internal val StudyActionSecondaryFill: Color @Composable get() = KaniTheme.colors.secondaryFill
private val StudyActionDisabledBorder: Color @Composable get() = KaniTheme.colors.disabledBorder
private val StudyActionDisabledText: Color @Composable get() = KaniTheme.colors.disabledContent
private val StudyActionDisabledPrimaryFill: Color @Composable get() = KaniTheme.colors.disabledContainer

internal fun studyActionButtonTestTag(label: String): String = "study-action-button-$label"

enum class StudyActionTone {
    PRIMARY,
    SECONDARY,
    FAIL,
    PASS,
}

@Composable
internal fun StudyPrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 62.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    tone: StudyActionTone = StudyActionTone.PRIMARY,
    leadingContent: @Composable RowScope.() -> Unit = {},
) {
    val containerColor = studyActionContainerColor(tone)
    val contentColor = studyActionContentColor(tone, containerColor)
    Button(
        onClick = { withButtonTrace(label) { onClick() } },
        enabled = enabled,
        modifier = modifier
            .testTag(studyActionButtonTestTag(label))
            .heightIn(min = minHeight)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        shape = KaniUiTokens.StudyShapeMedium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = StudyActionDisabledPrimaryFill,
            disabledContentColor = KaniTheme.colors.onPrimary,
        ),
        border = BorderStroke(1.dp, StudyActionBorderColor),
        contentPadding = contentPadding,
    ) {
        leadingContent()
        StudyActionButtonText(label = label, color = contentColor)
    }
}

@Composable
internal fun StudySecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 62.dp,
    tone: StudyActionTone = StudyActionTone.SECONDARY,
) {
    if (tone != StudyActionTone.SECONDARY) {
        StudyPrimaryActionButton(
            label = label,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            minHeight = minHeight,
            tone = tone,
        )
        return
    }
    OutlinedButton(
        onClick = { withButtonTrace(label) { onClick() } },
        enabled = enabled,
        modifier = modifier
            .testTag(studyActionButtonTestTag(label))
            .heightIn(min = minHeight)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        shape = KaniUiTokens.StudyShapeMedium,
        border = BorderStroke(1.dp, if (enabled) StudyActionBorderColor else StudyActionDisabledBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = StudyActionSecondaryFill,
            contentColor = StudyActionPrimaryColor,
            disabledContainerColor = KaniTheme.colors.secondaryFill,
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
private fun studyActionContainerColor(tone: StudyActionTone): Color = when (tone) {
    StudyActionTone.PRIMARY -> StudyActionPrimaryColor
    StudyActionTone.SECONDARY -> StudyActionSecondaryFill
    StudyActionTone.FAIL -> KaniTheme.colors.coral
    StudyActionTone.PASS -> KaniTheme.colors.teal
}

@Composable
private fun studyActionContentColor(tone: StudyActionTone, containerColor: Color): Color = when (tone) {
    StudyActionTone.PRIMARY -> KaniTheme.colors.onPrimary
    StudyActionTone.SECONDARY -> StudyActionPrimaryColor
    StudyActionTone.FAIL,
    StudyActionTone.PASS -> KaniUiTokens.readableTextColor(containerColor)
}

@Composable
private fun StudyActionButtonText(label: String, color: Color) {
    Text(
        text = label,
        color = color,
        fontSize = KaniUiTokens.StudyActionTextSizeSp.sp,
        fontWeight = FontWeight.Bold,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
    )
}
