package dev.bee.kanjianki.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.BrowseResults
import dev.bee.kanjianki.presentation.BrowseRow
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.UiTextResolver
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val BROWSE_QUERY_TEST_TAG: String = "kani-browse-query"
const val BROWSE_SEARCH_TEST_TAG: String = "kani-browse-search"
const val BROWSE_SIMILAR_FILTER_TEST_TAG: String = "kani-browse-similar-filter"
const val BROWSE_SHOW_SUSPENDED_TEST_TAG: String = "kani-browse-show-suspended"
const val BROWSE_SELECT_ALL_TEST_TAG: String = "kani-browse-select-all"
const val BROWSE_DESELECT_ALL_TEST_TAG: String = "kani-browse-deselect-all"
const val BROWSE_RESULT_HEADING_TEST_TAG: String = "kani-browse-result-heading"
const val BROWSE_SELECTION_SUMMARY_TEST_TAG: String = "kani-browse-selection-summary"
const val BROWSE_EMPTY_TEST_TAG: String = "kani-browse-empty"

/** One row per kanji, tagged by the kanji it is about. */
fun browseRowTestTag(kanji: String): String = "kani-browse-row-$kanji"

/** The study checkbox on a row, tagged separately so a test can tick it directly. */
fun browseStudiedTestTag(kanji: String): String = "kani-browse-studied-$kanji"

/**
 * Browse: a search box, the filters, and the results.
 *
 * The query is local state and only the Search button (or the keyboard's search key)
 * commits it. That is the plan's "no actions while editing text" requirement, and it is
 * a correctness property rather than a preference: dispatching on every keystroke
 * would open a Browse destination per character, filling the back stack with partial
 * queries the user never searched for and re-running the query on each one.
 */
@Composable
fun BrowseScreen(
    results: BrowseResults,
    copy: BrowseCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the committed query so navigating to a new search replaces the field's
    // contents, while a recomposition mid-typing does not.
    var query by remember(results.query) { mutableStateOf(results.query) }
    val countLine = rememberBrowseCountLine(results.rows.size)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag(BROWSE_QUERY_TEST_TAG),
                label = { Text(text = copy.searchHint) },
                singleLine = true,
                // The keyboard's search key commits the same query the button does, so
                // a user who never leaves the keyboard is not stuck with an
                // uncommitted search and no visible way to run it.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { dispatch(results.search(query)) }),
            )
            Button(
                onClick = { dispatch(results.search(query)) },
                modifier = Modifier
                    .heightIn(min = SEARCH_BUTTON_MIN_HEIGHT)
                    .testTag(BROWSE_SEARCH_TEST_TAG),
                shape = KaniUiTokens.ButtonShape,
            ) {
                Text(text = copy.searchAction)
            }
        }
        BrowseFilters(results, copy, dispatch)
        BrowseSelectionControls(results, copy, dispatch)
        Text(
            text = copy.resultHeading(results, countLine),
            modifier = Modifier
                .testTag(BROWSE_RESULT_HEADING_TEST_TAG)
                .semantics { heading() },
            color = KaniTheme.colors.muted,
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
        )
        if (results.rows.isEmpty()) {
            BrowseEmptyState(copy)
        } else {
            for (row in results.rows) {
                BrowseKanjiRow(results, row, copy, resolver, dispatch)
            }
        }
    }
}

@Composable
private fun BrowseFilters(
    results: BrowseResults,
    copy: BrowseCopy,
    dispatch: (KaniAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BrowseToggle(
            label = copy.similarFilter,
            checked = results.onlySimilarKanji,
            testTag = BROWSE_SIMILAR_FILTER_TEST_TAG,
            onChange = { dispatch(results.withSimilarFilter(it)) },
        )
        BrowseToggle(
            label = copy.showSuspended,
            checked = results.showSuspended,
            testTag = BROWSE_SHOW_SUSPENDED_TEST_TAG,
            onChange = { dispatch(results.withSuspendedShown(it)) },
        )
    }
}

/**
 * One filter, with the whole row as the hit target.
 *
 * `toggleable` on the row rather than `onCheckedChange` on the box, which is the
 * Material pattern and matters here: a 20dp checkbox beside a full-width label invites
 * a tap on the label, and a label that ignores taps reads as a broken control. The
 * checkbox itself takes `null` so the row is one node rather than two competing ones.
 */
@Composable
private fun BrowseToggle(
    label: String,
    checked: Boolean,
    testTag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange)
            .testTag(testTag)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = label,
            color = KaniTheme.colors.ink,
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
        )
    }
}

@Composable
private fun BrowseSelectionControls(
    results: BrowseResults,
    copy: BrowseCopy,
    dispatch: (KaniAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = copy.selectionSummary(results),
            modifier = Modifier.testTag(BROWSE_SELECTION_SUMMARY_TEST_TAG),
            color = KaniTheme.colors.muted,
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
        )
        // Both controls are offered whatever the current selection, because "Select
        // all" on a fully-selected list is a harmless no-op while a control that
        // disappears at the extremes leaves the user hunting for the one they need.
        TextButton(
            onClick = { dispatch(results.setAllStudied(studied = true)) },
            modifier = Modifier.testTag(BROWSE_SELECT_ALL_TEST_TAG),
            shape = KaniUiTokens.ButtonShape,
        ) {
            Text(text = copy.selectAll)
        }
        TextButton(
            onClick = { dispatch(results.setAllStudied(studied = false)) },
            modifier = Modifier.testTag(BROWSE_DESELECT_ALL_TEST_TAG),
            shape = KaniUiTokens.ButtonShape,
        ) {
            Text(text = copy.deselectAll)
        }
    }
}

@Composable
private fun BrowseKanjiRow(
    results: BrowseResults,
    row: BrowseRow,
    copy: BrowseCopy,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
) {
    val meaning = resolver.resolve(row.meaning)
    val readings = resolver.resolve(row.readings)
    val summary = resolver.resolve(row.summary)
    // Coral for a suspended card, teal otherwise — the collection's own state, which
    // Kani reads and never writes.
    val accent = if (row.suspended) KaniTheme.colors.coral else KaniTheme.colors.teal
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(browseRowTestTag(row.kanji))
            .semantics { contentDescription = copy.rowDescription(row, resolver) },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
        border = BorderStroke(1.dp, accent.copy(alpha = ROW_STROKE_ALPHA)),
        // Carries the query and filters, so back from the detail returns to this list
        // rather than to a default Browse the user never searched.
        onClick = { dispatch(results.open(row)) },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.kanji,
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyWordHeroTextSizeSp.sp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = meaning,
                    color = KaniTheme.colors.ink,
                    fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                    fontWeight = FontWeight.Medium,
                )
                for (line in listOf(readings, summary).filter { it.isNotBlank() }) {
                    Text(
                        text = line,
                        color = KaniTheme.colors.muted,
                        fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
                    )
                }
                if (row.suspended) {
                    Text(
                        text = copy.suspendedChip,
                        color = KaniTheme.colors.coral,
                        fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            // The checkbox is Kani-side queue state, sitting next to a chip that is
            // the collection's. Ticking it never suspends or unsuspends anything.
            Checkbox(
                checked = row.studied,
                onCheckedChange = { dispatch(row.studiedAction(it)) },
                modifier = Modifier
                    .testTag(browseStudiedTestTag(row.kanji))
                    .semantics { contentDescription = copy.studiedToggle(row.kanji) },
            )
        }
    }
}

@Composable
private fun BrowseEmptyState(copy: BrowseCopy) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(BROWSE_EMPTY_TEST_TAG)
            .semantics { contentDescription = "${copy.emptyTitle}. ${copy.emptyBody}" },
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = copy.emptyTitle,
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = copy.emptyBody,
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
        }
    }
}

private val SEARCH_BUTTON_MIN_HEIGHT = 58.dp
private const val ROW_STROKE_ALPHA = 0.45f
