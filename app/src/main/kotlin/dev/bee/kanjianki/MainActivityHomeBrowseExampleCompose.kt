@file:JvmName("MainActivityHomeBrowseExampleCompose")

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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrowseExampleCard(model: BrowseExampleCardModel) {
    val accent = kaniColor(model.color)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        shape = BrowseCardShape,
        color = BrowseWhite,
        border = BorderStroke(1.dp, accent)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BrowseExampleChip(label = model.sourceLabel, color = accent)
            Text(
                text = model.expression,
                color = BrowseInk,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (model.sentence.isNotEmpty()) {
                Text(
                    text = model.sentence,
                    color = BrowseMuted,
                    fontSize = 16.sp
                )
            }
            if (model.meaning.isNotEmpty()) {
                Text(
                    text = model.meaning,
                    color = BrowseMuted,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun BrowseExampleChip(label: String, color: ComposeColor) {
    Surface(
        modifier = Modifier.padding(top = 7.dp, end = 7.dp, bottom = 2.dp),
        shape = RoundedCornerShape(999.dp),
        color = browseSoftenedColor(color),
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
