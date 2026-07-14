@file:JvmName("MainActivityStudyDoneActionsCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.StudyTextCopy

private val StudyDonePrimary: Color @Composable get() = KaniTheme.colors.primary
private val StudyDonePrimaryBorder: Color @Composable get() = KaniTheme.colors.pinkStroke
private val StudyDoneSecondaryText: Color @Composable get() = KaniTheme.colors.plum
private val StudyDoneCardBackground: Color @Composable get() = KaniTheme.colors.bg
private val StudyDoneInsetBackground: Color @Composable get() = KaniTheme.colors.surface
private val StudyDoneMuted: Color @Composable get() = KaniTheme.colors.muted

@Composable
fun StudyDoneActions(
    availableStudyMoreNewCards: Int,
    onStudyMore: () -> Unit,
    onContinueAll: () -> Unit,
    onBackHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (availableStudyMoreNewCards > 0) {
            StudyPrimaryButton(
                label = StudyTextCopy.studyMoreNewCardsLabel(),
                traceLabel = "Study more new cards",
                onClick = onStudyMore
            )
        }
        if (availableStudyMoreNewCards > 0) {
            StudySecondaryButton(
                label = StudyTextCopy.continueAllKanjiLabel(),
                traceLabel = "Continue all kanji",
                onClick = onContinueAll
            )
        } else {
            StudyPrimaryButton(
                label = StudyTextCopy.continueAllKanjiLabel(),
                traceLabel = "Continue all kanji",
                onClick = onContinueAll
            )
        }
        StudySecondaryButton(
            label = StudyTextCopy.backHomeLabel(),
            traceLabel = "Back home",
            onClick = onBackHome
        )
    }
}

@Composable
fun StudyDoneScreen(model: StudyDoneScreenModel, modifier: Modifier = Modifier) {
    model.studyMoreDialog?.let { dialog ->
        StudyMoreNewCardsDialog(model = dialog)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = StudyDoneCardBackground,
        border = BorderStroke(1.dp, StudyDonePrimaryBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudyModePill(model.modeLabel)
            Text(
                text = model.title,
                modifier = Modifier.semantics { heading() },
                color = StudyDoneSecondaryText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            if (!model.showDoneActions && model.summaryLines.isEmpty()) {
                HomeEmptyState(
                    title = model.headline ?: model.title,
                    body = model.body
                )
            } else {
                model.headline?.let { headline ->
                    Text(
                        text = headline,
                        color = StudyDoneSecondaryText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = model.body,
                    color = StudyDoneMuted,
                    fontSize = 17.sp
                )
            }
            if (model.summaryLines.isNotEmpty()) {
                StudyDoneSummary(lines = model.summaryLines)
            }
            if (model.showDoneActions) {
                StudyDoneActions(
                    availableStudyMoreNewCards = model.availableStudyMoreNewCards,
                    onStudyMore = { model.onStudyMore.run() },
                    onContinueAll = { model.onContinueAll.run() },
                    onBackHome = { model.onBackHome.run() }
                )
            } else if (model.showBackHome) {
                if (model.backHomePrimary) {
                    StudyPrimaryButton(
                        label = StudyTextCopy.backHomeLabel(),
                        traceLabel = "Back home",
                        onClick = { model.onBackHome.run() }
                    )
                } else {
                    StudySecondaryButton(
                        label = StudyTextCopy.backHomeLabel(),
                        traceLabel = "Back home",
                        onClick = { model.onBackHome.run() }
                    )
                }
            }
        }
    }
}

@Composable
fun StudyMoreNewCardsDialog(model: StudyMoreNewCardsDialogModel) {
    var requestedCount by remember(model.initialCount) { mutableStateOf(model.initialCount.toString()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { model.onDismiss.run() },
        title = { Text(text = model.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = model.message)
                OutlinedTextField(
                    value = requestedCount,
                    onValueChange = { requestedCount = it },
                    label = { Text(text = model.inputLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                withButtonTrace("Study more new cards confirm") {
                    model.onConfirm(requestedCount)
                }
            }) {
                Text(text = model.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                withButtonTrace("Study more new cards cancel") {
                    model.onDismiss.run()
                }
            }) {
                Text(text = model.cancelLabel)
            }
        }
    )
}

@Composable
private fun StudyModePill(label: String) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
        color = KaniTheme.colors.surface,
        border = BorderStroke(1.dp, StudyDonePrimaryBorder)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = StudyDonePrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun StudyDoneSummary(lines: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = StudyDoneInsetBackground,
        border = BorderStroke(1.dp, StudyDonePrimaryBorder.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            lines.forEachIndexed { index, line ->
                Text(
                    text = line,
                    modifier = if (index == 0) Modifier.semantics { heading() } else Modifier,
                    color = if (index == 0) StudyDoneSecondaryText else StudyDoneMuted,
                    fontSize = if (index == 0) 20.sp else 15.sp,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun StudyPrimaryButton(
    label: String,
    traceLabel: String = label,
    onClick: () -> Unit
) {
    Button(
        onClick = { withButtonTrace(traceLabel) { onClick() } },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StudyDonePrimary,
            contentColor = KaniTheme.colors.onPrimary
        ),
        border = BorderStroke(1.dp, StudyDonePrimaryBorder),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = KaniTheme.colors.onPrimary,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun StudySecondaryButton(
    label: String,
    traceLabel: String = label,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = { withButtonTrace(traceLabel) { onClick() } },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, StudyDonePrimaryBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = KaniTheme.colors.surface,
            contentColor = StudyDoneSecondaryText
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = StudyDoneSecondaryText,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}
