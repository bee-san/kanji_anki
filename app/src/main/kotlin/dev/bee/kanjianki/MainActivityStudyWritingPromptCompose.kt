@file:JvmName("MainActivityStudyWritingPromptCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WritingPromptHeader(model: WritingPromptHeaderModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StudyModeChip(model.modeLabel, Modifier.padding(bottom = 8.dp))
        WritingPromptText(
            text = model.title,
            sizeSp = KaniUiTokens.StudyQuestionTextSizeSp,
            color = MainActivityUiSupport.STUDY_PLUM,
            bold = true
        )
        model.detailLines.forEach { line ->
            WritingPromptText(
                text = line.text,
                sizeSp = line.sizeSp,
                color = line.color,
                bold = line.bold
            )
        }
    }
}

@Composable
private fun WritingPromptText(
    text: String,
    sizeSp: Int,
    color: Int,
    bold: Boolean,
) {
    Text(
        text = text,
        color = kaniColor(color),
        fontSize = sizeSp.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true))
    )
}
