@file:JvmName("MainActivitySettingsStudySortCompose")

package dev.bee.kanjianki

import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.SettingsTextCopy

private val StudySortInk = Color(0xFF2D1635)
private val StudySortMuted = Color(0xFF6C5674)
private val StudySortTeal = Color(0xFF00AEB5)
private val StudySortPinkDark = Color(0xFFDA3A7A)
private val StudySortPanelBorder = Color(0xFFFFC7DE)
private val StudySortButtonBorder = Color(0xFFEEBDDA)
private val StudySortWhite = Color(0xFFFFFFFF)
private val StudySortPanelShape = RoundedCornerShape(24.dp)
private val StudySortButtonShape = RoundedCornerShape(12.dp)

internal fun newCardSortSettingsPanelView(
    activity: MainActivitySettings,
    model: SettingsNewCardSortPanelModel,
): View {
    return ComposeView(activity).apply {
        layoutParams = settingsPanelLayoutParams(activity)
        setContent {
            MaterialTheme {
                SettingsNewCardSortPanel(model)
            }
        }
    }
}

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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    onClick = { selectedMode = option.mode },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                    shape = StudySortButtonShape,
                    border = BorderStroke(1.dp, StudySortButtonBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = StudySortWhite,
                        contentColor = StudySortInk
                    )
                ) {
                    Text(
                        text = option.label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Button(
                onClick = { model.onSave.save(selectedMode) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
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
