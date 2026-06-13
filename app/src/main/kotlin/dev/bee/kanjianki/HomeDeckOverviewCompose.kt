package dev.bee.kanjianki

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeDeckOverview(rows: List<String>) {
    if (rows.isEmpty()) {
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = HomeTextCopy.deckOverviewTitle(),
            actionLabel = null,
            onAction = null,
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { row ->
                HomeDeckOverviewChip(row)
            }
        }
    }
}

@Composable
private fun HomeDeckOverviewChip(text: String) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(kaniColor(MainActivityUiSupport.STUDY_PILL))
            .border(1.dp, kaniColor(MainActivityUiSupport.STUDY_BORDER), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = kaniColor(MainActivityUiSupport.STUDY_PLUM),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        )
    }
}
