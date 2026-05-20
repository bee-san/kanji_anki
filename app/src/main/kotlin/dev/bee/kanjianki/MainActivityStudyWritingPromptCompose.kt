@file:JvmName("MainActivityStudyWritingPromptCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class WritingPromptHeaderModel(
    val modeLabel: String,
    val title: String,
    val taskLabel: String,
    val reasonLine: String,
    val detailLines: List<WritingPromptLineModel>,
)

data class WritingPromptLineModel(
    val text: String,
    val sizeSp: Int,
    val color: Int,
    val bold: Boolean,
)

@Composable
fun WritingPromptHeader(model: WritingPromptHeaderModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WritingModePill(model.modeLabel)
        WritingPromptText(
            text = model.title,
            sizeSp = 30,
            color = MainActivityUiSupport.STUDY_PLUM,
            bold = true
        )
        WritingPromptText(
            text = model.taskLabel,
            sizeSp = 16,
            color = MainActivityUiSupport.STUDY_PINK_DARK,
            bold = true
        )
        if (model.reasonLine.isNotEmpty()) {
            WritingPromptText(
                text = model.reasonLine,
                sizeSp = 14,
                color = MainActivityUiSupport.STUDY_MUTED,
                bold = false
            )
        }
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
private fun WritingModePill(label: String) {
    Surface(
        modifier = Modifier.padding(bottom = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(MainActivityUiSupport.STUDY_PILL),
        border = BorderStroke(1.dp, Color(MainActivityUiSupport.STUDY_BORDER))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color(MainActivityUiSupport.STUDY_PINK_DARK),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
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
        color = Color(color),
        fontSize = sizeSp.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true))
    )
}
