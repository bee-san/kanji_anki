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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StudyDonePrimary = Color(0xFFDA3A7A)
private val StudyDonePrimaryBorder = Color(0xFFFFADCD)
private val StudyDoneSecondaryText = Color(0xFF4B2552)
private val StudyDoneCardBackground = Color(0xFFFFF7FB)
private val StudyDoneInsetBackground = Color(0xFFFFFFFF)
private val StudyDoneMuted = Color(0xFF6C5674)

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
                label = "Study more new cards",
                onClick = onStudyMore
            )
        }
        if (availableStudyMoreNewCards > 0) {
            StudySecondaryButton(
                label = MainActivityBase.LABEL_CONTINUE_ALL_KANJI,
                onClick = onContinueAll
            )
        } else {
            StudyPrimaryButton(
                label = MainActivityBase.LABEL_CONTINUE_ALL_KANJI,
                onClick = onContinueAll
            )
        }
        StudySecondaryButton(
            label = MainActivityBase.LABEL_BACK_HOME,
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
                color = StudyDoneSecondaryText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
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
                        label = MainActivityBase.LABEL_BACK_HOME,
                        onClick = { model.onBackHome.run() }
                    )
                } else {
                    StudySecondaryButton(
                        label = MainActivityBase.LABEL_BACK_HOME,
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
            TextButton(onClick = { model.onConfirm(requestedCount) }) {
                Text(text = model.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = { model.onDismiss.run() }) {
                Text(text = model.cancelLabel)
            }
        }
    )
}

@Composable
private fun StudyModePill(label: String) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
        color = Color.White,
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
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = StudyDonePrimary,
            contentColor = Color.White
        ),
        border = BorderStroke(1.dp, StudyDonePrimaryBorder),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun StudySecondaryButton(
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, StudyDonePrimaryBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
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
