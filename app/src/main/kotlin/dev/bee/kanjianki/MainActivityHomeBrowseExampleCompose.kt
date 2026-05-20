@file:JvmName("MainActivityHomeBrowseExampleCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BrowseExampleCardModel(
    val sourceLabel: String,
    val expression: String,
    val sentence: String,
    val meaning: String,
    val color: Int,
)

internal fun exampleCardView(activity: MainActivityHomeBrowseDetail, model: BrowseExampleCardModel): View {
    return ComposeView(activity.home()).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                BrowseExampleCard(model)
            }
        }
    }
}

@Composable
fun BrowseExampleCard(model: BrowseExampleCardModel) {
    val accent = ComposeColor(model.color)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        shape = ExampleCardShape,
        color = ExampleWhite,
        border = BorderStroke(1.dp, accent)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BrowseExampleChip(label = model.sourceLabel, color = accent)
            Text(
                text = model.expression,
                color = ExampleInk,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (model.sentence.isNotEmpty()) {
                Text(
                    text = model.sentence,
                    color = ExampleMuted,
                    fontSize = 16.sp
                )
            }
            if (model.meaning.isNotEmpty()) {
                Text(
                    text = model.meaning,
                    color = ExampleMuted,
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
        color = softenedExampleColor(color),
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

private fun softenedExampleColor(color: ComposeColor): ComposeColor {
    return when (color) {
        ExampleCoral -> ComposeColor(0xFFFFEBF3)
        ExampleTeal -> ComposeColor(0xFFE6FAFB)
        ExampleGold -> ComposeColor(0xFFFFF7DC)
        ComposeColor(0xFF6E5CE6), ComposeColor(0xFFC9B9FF) -> ComposeColor(0xFFF2EEFF)
        else -> ComposeColor(0xFFF8EEF5)
    }
}

private val ExampleInk = ComposeColor(0xFF2D1635)
private val ExampleMuted = ComposeColor(0xFF6C5674)
private val ExampleCoral = ComposeColor(0xFFFF4C76)
private val ExampleTeal = ComposeColor(0xFF00AEB5)
private val ExampleGold = ComposeColor(0xFFFFD640)
private val ExampleWhite = ComposeColor(0xFFFFFFFF)
private val ExampleCardShape = RoundedCornerShape(8.dp)
