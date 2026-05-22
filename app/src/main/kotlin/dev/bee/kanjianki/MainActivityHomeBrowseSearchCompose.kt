@file:JvmName("MainActivityHomeBrowseSearchCompose")

package dev.bee.kanjianki

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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsImportModels

internal fun browseScreenModel(
    activity: MainActivityHome,
    query: String,
    items: List<RecordsImportModels.KanjiInventoryItem>
): BrowseScreenModel {
    val rows = items.map { item -> browseKanjiRowModel(activity, query, item) }
    return BrowseScreenModel(
        initialQuery = query,
        resultHeading = HomeTextCopy.browseResultHeading(rows.size),
        rows = rows,
        onHome = activity::renderHome,
        onSearch = activity::renderBrowseKanji
    )
}

private fun browseKanjiRowModel(
    activity: MainActivityHome,
    browseQuery: String,
    item: RecordsImportModels.KanjiInventoryItem
): BrowseKanjiRowModel {
    return BrowseKanjiRowModel(
        kanji = item.kanji,
        meaning = HomeTextCopy.browseItemMeaning(item),
        readings = item.readings,
        summary = HomeTextCopy.browseInventorySummary(item.sourceCount, item.exampleCount),
        suspended = item.suspended,
        onClick = { activity.renderDetail(item.kanji, true, browseQuery) }
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
            color = BrowseInk,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            style = browseNoFontPaddingStyle(34)
        )
        Text(
            text = HomeTextCopy.browseBody(),
            modifier = Modifier.fillMaxWidth(),
            color = BrowseMuted,
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
            colors = ButtonDefaults.buttonColors(containerColor = BrowseTeal)
        ) {
            Text(
                text = HomeTextCopy.browseSearchButtonLabel(),
                color = BrowseWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = model.resultHeading,
            color = BrowseInk,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            style = browseNoFontPaddingStyle(22)
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
fun BrowseKanjiRow(model: BrowseKanjiRowModel) {
    val borderColor = if (model.suspended) BrowseCoral else BrowseTeal
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { }
            .clickable(onClick = model.onClick),
        shape = BrowseCardShape,
        color = BrowseWhite,
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
                    color = BrowseBlush,
                    border = BorderStroke(1.dp, BrowseBlush)
                ) {
                    Text(
                        text = model.kanji,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp),
                        color = BrowseInk,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = browseNoFontPaddingStyle(44)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = model.meaning,
                        color = BrowseInk,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        style = browseNoFontPaddingStyle(19)
                    )
                    if (model.readings.isNotEmpty()) {
                        Text(
                            text = model.readings,
                            color = BrowseTeal,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = model.summary,
                        color = BrowseMuted,
                        fontSize = 14.sp
                    )
                }
            }
            if (model.suspended) {
                BrowseSearchChip(label = HomeTextCopy.suspendedChipLabel(), color = BrowseCoral)
            }
        }
    }
}

@Composable
private fun BrowseEmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BrowseCardShape,
        color = ComposeColor(0xFFFFF7D6),
        border = BorderStroke(1.dp, BrowseGold)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = HomeTextCopy.browseEmptyTitle(),
                color = BrowseInk,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = HomeTextCopy.browseEmptyBody(),
                color = BrowseInk,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun BrowseSearchChip(label: String, color: ComposeColor) {
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
