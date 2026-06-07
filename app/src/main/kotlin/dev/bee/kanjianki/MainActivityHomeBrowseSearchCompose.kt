@file:JvmName("MainActivityHomeBrowseSearchCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsImportModels

internal fun browseKanjiRowTestTag(kanji: String): String = "browse-kanji-row-$kanji"
internal fun browseKanjiStudiedToggleTestTag(kanji: String): String = "browse-kanji-studied-$kanji"
internal fun browseSimilarFilterTestTag(): String = "browse-similar-filter"
internal fun browseSelectAllStudiedTestTag(): String = "browse-select-all-studied"
internal fun browseDeselectAllStudiedTestTag(): String = "browse-deselect-all-studied"

private fun browseKanjiRowDescription(
    kanji: String,
    meaning: String,
    readings: String,
    summary: String,
    studied: Boolean,
    suspended: Boolean,
): String {
    return listOfNotNull(
        "Browse kanji row",
        kanji,
        meaning,
        readings.takeIf { it.isNotBlank() },
        summary,
        if (studied) "selected for study" else "not selected for study",
        if (suspended) HomeTextCopy.suspendedChipLabel() else null,
    ).joinToString(", ")
}

internal fun buildBrowseScreenData(
    items: List<RecordsImportModels.KanjiInventoryItem>,
    rowBuilder: (RecordsImportModels.KanjiInventoryItem) -> BrowseKanjiRowModel,
): BrowseScreenData {
    val rows = ArrayList<BrowseKanjiRowModel>(items.size)
    val kanjiList = ArrayList<String>(items.size)
    var studiedCount = 0
    for (item in items) {
        val row = rowBuilder(item)
        rows.add(row)
        kanjiList.add(item.kanji)
        if (row.studied) {
            studiedCount += 1
        }
    }
    return BrowseScreenData(rows, kanjiList, studiedCount)
}

internal fun browseScreenModel(
    activity: MainActivityHome,
    query: String,
    items: List<RecordsImportModels.KanjiInventoryItem>,
    onlySimilarKanji: Boolean = false,
): BrowseScreenModel {
    val screenData = buildBrowseScreenData(items) { item ->
        browseKanjiRowModel(activity, query, onlySimilarKanji, item)
    }
    return BrowseScreenModel(
        initialQuery = query,
        resultHeading = HomeTextCopy.browseResultHeading(screenData.rows.size),
        rows = screenData.rows,
        similarFilterActive = onlySimilarKanji,
        studySelectionSummary = HomeTextCopy.browseStudySelectionSummary(screenData.studiedCount, screenData.rows.size),
        onToggleSimilarFilter = { updatedQuery -> activity.renderBrowseKanji(updatedQuery, !onlySimilarKanji) },
        onSelectAllStudied = {
            activity.store.setKanjiLocallySuspendedForKanji(screenData.kanjiList, false, System.currentTimeMillis())
            activity.renderBrowseKanji(query, onlySimilarKanji)
        },
        onDeselectAllStudied = {
            activity.store.setKanjiLocallySuspendedForKanji(screenData.kanjiList, true, System.currentTimeMillis())
            activity.renderBrowseKanji(query, onlySimilarKanji)
        },
        onHome = activity::renderHome,
        onSearch = { updatedQuery -> activity.renderBrowseKanji(updatedQuery, onlySimilarKanji) }
    )
}

private fun browseKanjiRowModel(
    activity: MainActivityHome,
    browseQuery: String,
    onlySimilarKanji: Boolean,
    item: RecordsImportModels.KanjiInventoryItem
): BrowseKanjiRowModel {
    val meaning = HomeTextCopy.browseItemMeaning(item)
    val summary = HomeTextCopy.browseInventorySummary(item.sourceCount, item.exampleCount)
    return BrowseKanjiRowModel(
        kanji = item.kanji,
        meaning = meaning,
        readings = item.readings,
        summary = summary,
        contentDescription = browseKanjiRowDescription(
            kanji = item.kanji,
            meaning = meaning,
            readings = item.readings,
            summary = summary,
            studied = !item.suspended,
            suspended = item.suspended,
        ),
        suspended = item.suspended,
        studied = !item.suspended,
        onStudiedChange = { studied ->
            activity.store.setKanjiLocallySuspended(item.kanji, !studied, System.currentTimeMillis())
            activity.renderBrowseKanji(browseQuery, onlySimilarKanji)
        },
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
            onClick = {
                withButtonTrace("home-search") {
                    runSearch()
                }
            },
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
        BrowseStudyControls(model = model, query = query)
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
private fun BrowseStudyControls(model: BrowseScreenModel, query: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { model.onToggleSimilarFilter(query) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(browseSimilarFilterTestTag()),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (model.similarFilterActive) BrowseTeal else BrowseWhite,
                contentColor = if (model.similarFilterActive) BrowseWhite else BrowseTeal,
            ),
            border = BorderStroke(1.dp, BrowseTeal),
        ) {
            Text(
                text = HomeTextCopy.browseSimilarFilterLabel(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = model.studySelectionSummary,
            color = BrowseMuted,
            fontSize = 14.sp
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = model.onSelectAllStudied,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(browseSelectAllStudiedTestTag()),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BrowseTeal),
            ) {
                Text(
                    text = HomeTextCopy.browseSelectAllStudiedLabel(),
                    color = BrowseTeal,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = model.onDeselectAllStudied,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag(browseDeselectAllStudiedTestTag()),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BrowseCoral),
            ) {
                Text(
                    text = HomeTextCopy.browseDeselectAllStudiedLabel(),
                    color = BrowseCoral,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
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
            .testTag(browseKanjiRowTestTag(model.kanji))
            .semantics {
                contentDescription = model.contentDescription
            }
            .clickable(
                role = Role.Button,
                onClick = {
                    withButtonTrace("browse-kanji-${model.kanji}") {
                        model.onClick()
                    }
                }
            ),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Checkbox(
                    checked = model.studied,
                    onCheckedChange = model.onStudiedChange,
                    modifier = Modifier
                        .testTag(browseKanjiStudiedToggleTestTag(model.kanji))
                        .semantics {
                            contentDescription = HomeTextCopy.browseStudiedToggleLabel(model.kanji)
                        },
                    colors = CheckboxDefaults.colors(checkedColor = BrowseTeal)
                )
                Text(
                    text = HomeTextCopy.browseStudiedToggleLabel(model.kanji),
                    color = BrowseInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BrowseEmptyState() {
    HomeEmptyState(
        title = HomeTextCopy.browseEmptyTitle(),
        body = HomeTextCopy.browseEmptyBody()
    )
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
