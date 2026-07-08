@file:JvmName("MainActivitySettingsStudySortCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.SettingsTextCopy

private val StudySortInk: Color @Composable get() = KaniTheme.colors.ink
private val StudySortMuted: Color @Composable get() = KaniTheme.colors.muted
private val StudySortTeal: Color @Composable get() = KaniTheme.colors.teal
private val StudySortPinkDark: Color @Composable get() = KaniTheme.colors.primary
private val StudySortPanelBorder: Color @Composable get() = KaniTheme.colors.border
private val StudySortButtonBorder: Color @Composable get() = KaniTheme.colors.borderSoft
private val StudySortWhite: Color @Composable get() = KaniTheme.colors.surface
private val StudySortPanelShape = RoundedCornerShape(24.dp)
private val StudySortButtonShape = RoundedCornerShape(12.dp)

@Composable
fun SettingsNewCardSortPanel(model: SettingsNewCardSortPanelModel) {
    var selectedMode by rememberSaveable(model.initialMode) { mutableStateOf(model.initialMode) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = StudySortPanelShape,
        color = StudySortWhite,
        border = BorderStroke(1.dp, StudySortPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = model.title,
                color = StudySortInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = SettingsTextCopy.newCardSortStatusText(selectedMode),
                color = StudySortTeal,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.body,
                color = StudySortMuted,
                fontSize = 15.sp
            )
            model.options.forEach { option ->
                OutlinedButton(
                    onClick = {
                        withButtonTrace("study-sort-option-${option.mode}") {
                            selectedMode = option.mode
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("new-card-sort-option-${option.mode}"),
                    shape = StudySortButtonShape,
                    border = BorderStroke(1.dp, StudySortButtonBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StudySortWhite,
                        contentColor = StudySortInk
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = option.label,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = option.description,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = StudySortMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            val previewRows = model.previewRows(selectedMode)
            if (previewRows.isNotEmpty()) {
                NewCardSortPreview(rows = previewRows, warning = model.previewWarning(selectedMode))
            }
            Button(
                onClick = {
                    withButtonTrace("study-sort-save") {
                        model.onSave.save(selectedMode)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("new-card-sort-save"),
                shape = StudySortButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudySortPinkDark,
                    contentColor = StudySortWhite
                )
            ) {
                Text(
                    text = model.saveLabel,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NewCardSortPreview(rows: List<SettingsNewCardSortPreviewRowModel>, warning: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = StudySortButtonShape,
        color = KaniTheme.colors.bg,
        border = BorderStroke(1.dp, StudySortButtonBorder),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Next up preview",
                color = StudySortInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = row.kanji,
                        color = StudySortInk,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = row.primaryMeaning,
                        color = StudySortInk,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = row.scoreLabel,
                        color = StudySortMuted,
                        fontSize = 12.sp,
                    )
                }
            }
            if (!warning.isNullOrBlank()) {
                Text(
                    text = warning,
                    color = StudySortPinkDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
