@file:JvmName("MainActivitySettingsStudyLadderCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StudyLadderInk = Color(0xFF2D1635)
private val StudyLadderMuted = Color(0xFF6C5674)
private val StudyLadderPlum = Color(0xFF6E2B73)
private val StudyLadderPinkDark = Color(0xFFDA3A7A)
private val StudyLadderPanelBorder = Color(0xFFFFC7DE)
private val StudyLadderButtonBorder = Color(0xFFEEBDDA)
private val StudyLadderDivider = Color(0xFFF3D4E4)
private val StudyLadderWhite = Color(0xFFFFFFFF)
private val StudyLadderPanelShape = RoundedCornerShape(24.dp)
private val StudyLadderButtonShape = RoundedCornerShape(12.dp)

@Composable
fun SettingsStudyLadderPanel(model: SettingsStudyLadderPanelModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = StudyLadderPanelShape,
        color = StudyLadderWhite,
        border = BorderStroke(1.dp, StudyLadderPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.title,
                color = StudyLadderInk,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.body,
                color = StudyLadderMuted,
                fontSize = 15.sp
            )
            model.rungs.forEachIndexed { index, rung ->
                StudyLadderRungRow(rung)
                if (index < model.rungs.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(StudyLadderDivider)
                    )
                }
            }
            Button(
                onClick = { model.onRestore.run() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .semantics { contentDescription = model.restoreDescription },
                shape = StudyLadderButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudyLadderPinkDark,
                    contentColor = StudyLadderWhite
                )
            ) {
                Text(
                    text = model.restoreLabel,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StudyLadderRungRow(rung: SettingsStudyLadderRungModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = rung.label,
            color = StudyLadderPlum,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = rung.subtitle,
            color = StudyLadderMuted,
            fontSize = 13.sp
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            StudyLadderOutlinedButton(
                label = rung.toggleLabel,
                description = rung.toggleDescription,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = { rung.onToggle.run() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            StudyLadderOutlinedButton(
                label = rung.moveUpLabel,
                description = rung.moveUpDescription,
                enabled = rung.canMoveUp,
                modifier = Modifier.weight(1f),
                onClick = { rung.onMoveUp.run() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            StudyLadderOutlinedButton(
                label = rung.moveDownLabel,
                description = rung.moveDownDescription,
                enabled = rung.canMoveDown,
                modifier = Modifier.weight(1f),
                onClick = { rung.onMoveDown.run() }
            )
        }
    }
}

@Composable
private fun StudyLadderOutlinedButton(
    label: String,
    description: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description },
        shape = StudyLadderButtonShape,
        border = BorderStroke(1.dp, StudyLadderButtonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = StudyLadderWhite,
            contentColor = StudyLadderInk,
            disabledContentColor = StudyLadderMuted
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
