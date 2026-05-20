@file:JvmName("MainActivityHomeBrowseDetailCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsImportModels

private val Ink = ComposeColor(0xFF2D1635)
private val Muted = ComposeColor(0xFF6C5674)
private val Teal = ComposeColor(0xFF00AEB5)
private val Coral = ComposeColor(0xFFFF4C76)
private val Gold = ComposeColor(0xFFFFD640)
private val White = ComposeColor(0xFFFFFFFF)
private val Blush = ComposeColor(0xFFFFEFF6)
private val PanelShape = RoundedCornerShape(18.dp)
private val CardShape = RoundedCornerShape(8.dp)

data class BrowseScreenModel(
    val initialQuery: String,
    val resultHeading: String,
    val rows: List<BrowseKanjiRowModel>,
    val onHome: () -> Unit,
    val onSearch: (String) -> Unit,
)

data class BrowseKanjiRowModel(
    val kanji: String,
    val meaning: String,
    val readings: String,
    val summary: String,
    val suspended: Boolean,
    val onClick: () -> Unit,
)

internal fun browseScreenView(
    activity: MainActivityHomeBrowseDetail,
    query: String,
    items: List<RecordsImportModels.KanjiInventoryItem>
): View {
    val rows = items.map { item -> browseKanjiRowModel(activity, item) }
    return ComposeView(activity.home()).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                BrowseScreen(
                    model = BrowseScreenModel(
                        initialQuery = query,
                        resultHeading = HomeTextCopy.browseResultHeading(rows.size),
                        rows = rows,
                        onHome = activity.home()::renderHome,
                        onSearch = activity::renderBrowseKanji
                    )
                )
            }
        }
    }
}

internal fun browseKanjiRowView(activity: MainActivityHomeBrowseDetail, item: RecordsImportModels.KanjiInventoryItem): View {
    return ComposeView(activity.home()).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                BrowseKanjiRow(model = browseKanjiRowModel(activity, item))
            }
        }
    }
}

private fun browseKanjiRowModel(
    activity: MainActivityHomeBrowseDetail,
    item: RecordsImportModels.KanjiInventoryItem
): BrowseKanjiRowModel {
    return BrowseKanjiRowModel(
        kanji = item.kanji,
        meaning = HomeTextCopy.browseItemMeaning(item),
        readings = item.readings,
        summary = HomeTextCopy.browseInventorySummary(item.sourceCount, item.exampleCount),
        suspended = item.suspended,
        onClick = { activity.renderDetail(item.kanji, true) }
    )
}

@Composable
fun BrowseScreen(model: BrowseScreenModel) {
    var query by remember(model.initialQuery) { mutableStateOf(model.initialQuery) }
    val runSearch = { model.onSearch(query) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeFullWidthHomeButton(label = HomeTextCopy.homeLabel(), onClick = model.onHome)
        Text(
            text = HomeTextCopy.browseTitle(),
            modifier = Modifier.fillMaxWidth(),
            color = Ink,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            style = noFontPaddingStyle(34)
        )
        Text(
            text = HomeTextCopy.browseBody(),
            modifier = Modifier.fillMaxWidth(),
            color = Muted,
            fontSize = 16.sp
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(HomeTextCopy.browseSearchHint()) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() })
        )
        Button(
            onClick = runSearch,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Teal)
        ) {
            Text(
                text = HomeTextCopy.browseSearchButtonLabel(),
                color = White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = model.resultHeading,
            color = Ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            style = noFontPaddingStyle(22)
        )
        if (model.rows.isEmpty()) {
            BrowseEmptyState()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                model.rows.forEach { row ->
                    BrowseKanjiRow(model = row)
                }
            }
        }
    }
}

@Composable
private fun BrowseEmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = ComposeColor(0xFFFFF7D6),
        border = BorderStroke(1.dp, Gold)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = HomeTextCopy.browseEmptyTitle(),
                color = Ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = HomeTextCopy.browseEmptyBody(),
                color = Ink,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun BrowseKanjiRow(model: BrowseKanjiRowModel) {
    val borderColor = if (model.suspended) Coral else Teal
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .clickable(onClick = model.onClick),
        shape = CardShape,
        color = White,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(74.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Blush,
                    border = BorderStroke(1.dp, Blush)
                ) {
                    Text(
                        text = model.kanji,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp),
                        color = Ink,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = noFontPaddingStyle(44)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = model.meaning,
                        color = Ink,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        style = noFontPaddingStyle(19)
                    )
                    if (model.readings.isNotEmpty()) {
                        Text(
                            text = model.readings,
                            color = Teal,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = model.summary,
                        color = Muted,
                        fontSize = 14.sp
                    )
                }
            }
            if (model.suspended) {
                BrowseChip(label = HomeTextCopy.suspendedChipLabel())
            }
        }
    }
}

@Composable
private fun BrowseChip(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Coral,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun noFontPaddingStyle(sizeSp: Int): TextStyle {
    return TextStyle(
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.Bold,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
}

internal fun recoveryTimelinePanelsView(activity: MainActivityHomeBrowseDetail, model: MainActivityHomeBrowseDetail.BrowseTimelinePanelsModel): View {
    return ComposeView(activity.home()).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                RecoveryTimelinePanels(model)
            }
        }
    }
}

@Composable
fun RecoveryTimelinePanels(model: MainActivityHomeBrowseDetail.BrowseTimelinePanelsModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = model.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Ink,
            fontSize = 22.sp
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = PanelShape,
            color = White,
            border = BorderStroke(1.dp, ComposeColor(model.statusColor))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = model.statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Text(
                    text = model.supportText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }
        }
        if (model.emptyText != null) {
            Text(
                text = model.emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                fontSize = 15.sp
            )
        }
        model.events.forEach { event ->
            RecoveryTimelineEvent(event)
        }
    }
}

@Composable
private fun RecoveryTimelineEvent(model: MainActivityHomeBrowseDetail.BrowseTimelineEventModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = White,
        border = BorderStroke(1.dp, ComposeColor(model.color))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = model.dateText,
                style = MaterialTheme.typography.labelMedium,
                color = Muted
            )
            Text(
                text = model.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            if (model.detail.isNotEmpty()) {
                Text(
                    text = model.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted
                )
            }
            if (model.sourceLine.isNotEmpty()) {
                Text(
                    text = model.sourceLine,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
            }
        }
    }
}
