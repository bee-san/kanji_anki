package dev.bee.kanjianki.missing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.MissingKanjiContent
import dev.bee.kanjianki.presentation.MissingKanjiDestinations
import dev.bee.kanjianki.presentation.MissingKanjiOperationResult
import dev.bee.kanjianki.presentation.MissingKanjiRow
import dev.bee.kanjianki.presentation.MissingKanjiScreen
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val MISSING_SCREEN_TEST_TAG: String = "kani-missing"
const val MISSING_PRIMARY_TEST_TAG: String = "kani-missing-primary"
const val MISSING_CANCEL_TEST_TAG: String = "kani-missing-cancel"
const val MISSING_SCANNING_TEST_TAG: String = "kani-missing-scanning"
const val MISSING_ERROR_TEST_TAG: String = "kani-missing-error"
const val MISSING_REPORT_TEST_TAG: String = "kani-missing-report"
const val MISSING_SELECT_ALL_TEST_TAG: String = "kani-missing-select-all"
const val MISSING_CLEAR_TEST_TAG: String = "kani-missing-clear"
const val MISSING_ADD_TEST_TAG: String = "kani-missing-add"
const val MISSING_CREATE_ANKI_TEST_TAG: String = "kani-missing-create-anki"
const val MISSING_EXPORT_CSV_TEST_TAG: String = "kani-missing-export-csv"
const val MISSING_RESULT_TEST_TAG: String = "kani-missing-result"
const val MISSING_RESULT_DISMISS_TEST_TAG: String = "kani-missing-result-dismiss"

/** One report row, tagged by the kanji it is about. */
fun missingRowTestTag(literal: String): String = "kani-missing-row-$literal"

/** The select checkbox on a row, tagged separately. */
fun missingRowSelectTestTag(literal: String): String = "kani-missing-select-$literal"

/**
 * The Missing Kanji surface, from one [MissingKanjiScreen].
 *
 * The state machine the Android host ran, rendered from the portable model: first
 * run, provider-missing, permission, scanning, error, and the report with its
 * selectable rows and batch destinations. Selection is local state here — which rows
 * are ticked is UI, not something the host re-derives — and the destinations dispatch
 * it as a set. The operation-result dialog overlays the report when present.
 *
 * The one primary button dispatches [MissingKanjiScreen.primaryAction], whose meaning
 * (scan / grant / install / retry) the host decided, so both hosts cannot drift.
 */
@Composable
fun MissingKanjiScreenView(
    screen: MissingKanjiScreen,
    copy: MissingKanjiCopy,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MISSING_SCREEN_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val content = screen.content) {
            MissingKanjiContent.FirstRun ->
                StatePanel(copy.firstRunTitle, copy.firstRunBody, screen, copy, dispatch)
            MissingKanjiContent.ProviderMissing ->
                StatePanel(copy.providerMissingTitle, copy.providerMissingBody, screen, copy, dispatch)
            MissingKanjiContent.PermissionRequired ->
                StatePanel(copy.permissionTitle, copy.permissionBody, screen, copy, dispatch)
            is MissingKanjiContent.Scanning -> ScanningPanel(content, copy, dispatch)
            is MissingKanjiContent.Error -> StatePanel(copy.errorTitle, content.failureCode, screen, copy, dispatch, MISSING_ERROR_TEST_TAG)
            is MissingKanjiContent.Report -> ReportPanel(content, screen.destinations, copy, dispatch)
        }
        screen.operationResult?.let { ResultDialog(it, copy, dispatch) }
    }
}

@Composable
private fun StatePanel(
    title: String,
    body: String,
    screen: MissingKanjiScreen,
    copy: MissingKanjiCopy,
    dispatch: (KaniAction) -> Unit,
    tag: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it },
        shape = KaniUiTokens.PanelShape,
        color = KaniTheme.colors.panel,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = title, modifier = Modifier.semantics { heading() }, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp, fontWeight = FontWeight.Bold)
            Text(text = body, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp)
            PrimaryButton(screen, copy, dispatch)
        }
    }
}

@Composable
private fun PrimaryButton(screen: MissingKanjiScreen, copy: MissingKanjiCopy, dispatch: (KaniAction) -> Unit) {
    Button(
        onClick = { dispatch(screen.primaryAction) },
        modifier = Modifier.testTag(MISSING_PRIMARY_TEST_TAG),
        shape = KaniUiTokens.ButtonShape,
    ) {
        Text(text = screen.primaryActionLabel.ifBlank { copy.firstRunTitle })
    }
}

@Composable
private fun ScanningPanel(scanning: MissingKanjiContent.Scanning, copy: MissingKanjiCopy, dispatch: (KaniAction) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(MISSING_SCANNING_TEST_TAG),
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = copy.scanning, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp, fontWeight = FontWeight.Medium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = KaniTheme.colors.primary, trackColor = KaniTheme.colors.track)
            Text(
                text = "${scanning.notesScanned} · ${scanning.uniqueKanji}",
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
            TextButton(
                onClick = { dispatch(KaniAction.MissingKanji.CancelScan) },
                modifier = Modifier.testTag(MISSING_CANCEL_TEST_TAG),
                shape = KaniUiTokens.ButtonShape,
            ) {
                Text(text = copy.cancel)
            }
        }
    }
}

@Composable
private fun ReportPanel(
    report: MissingKanjiContent.Report,
    destinations: MissingKanjiDestinations,
    copy: MissingKanjiCopy,
    dispatch: (KaniAction) -> Unit,
) {
    var selected by remember(report.reportKeyOrRows()) { mutableStateOf(emptySet<String>()) }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(MISSING_REPORT_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = report.summaryLine, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp, fontWeight = FontWeight.Medium)
        Text(text = report.missingCountLine, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        report.staleLine?.let {
            Text(text = it, color = KaniTheme.colors.coral, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        }
        SelectionControls(report, copy, selected, onChange = { selected = it })
        DestinationRow(destinations, copy, selected, dispatch)
        for (row in report.rows) {
            ReportRow(row, selected.contains(row.literal), copy, dispatch, onToggle = { on ->
                selected = if (on) selected + row.literal else selected - row.literal
            })
        }
    }
}

@Composable
private fun SelectionControls(
    report: MissingKanjiContent.Report,
    copy: MissingKanjiCopy,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = copy.selection(selected.size), color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        TextButton(
            onClick = { onChange(report.rows.map { it.literal }.toSet()) },
            modifier = Modifier.testTag(MISSING_SELECT_ALL_TEST_TAG),
            shape = KaniUiTokens.ButtonShape,
        ) {
            Text(text = copy.selectAll)
        }
        TextButton(
            onClick = { onChange(emptySet()) },
            modifier = Modifier.testTag(MISSING_CLEAR_TEST_TAG),
            shape = KaniUiTokens.ButtonShape,
        ) {
            Text(text = copy.clear)
        }
    }
}

@Composable
private fun DestinationRow(
    destinations: MissingKanjiDestinations,
    copy: MissingKanjiCopy,
    selected: Set<String>,
    dispatch: (KaniAction) -> Unit,
) {
    val enabled = selected.isNotEmpty() && !destinations.operationInProgress
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        destinations.exportLine?.let {
            Text(text = it, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (destinations.addToKaniEnabled) {
                Button(
                    onClick = { dispatch(destinations.addAction(selected)) },
                    modifier = Modifier.weight(1f).testTag(MISSING_ADD_TEST_TAG),
                    enabled = enabled,
                    shape = KaniUiTokens.ButtonShape,
                ) {
                    Text(text = copy.addToKani)
                }
            }
            if (destinations.createAnkiEnabled) {
                OutlinedButton(
                    onClick = { dispatch(destinations.createAnkiAction(selected)) },
                    modifier = Modifier.weight(1f).testTag(MISSING_CREATE_ANKI_TEST_TAG),
                    enabled = enabled,
                    shape = KaniUiTokens.ButtonShape,
                ) {
                    Text(text = copy.createAnki)
                }
            }
            if (destinations.csvExportEnabled) {
                OutlinedButton(
                    onClick = { dispatch(destinations.exportCsvAction(selected)) },
                    modifier = Modifier.weight(1f).testTag(MISSING_EXPORT_CSV_TEST_TAG),
                    enabled = enabled,
                    shape = KaniUiTokens.ButtonShape,
                ) {
                    Text(text = copy.exportCsv)
                }
            }
        }
    }
}

@Composable
private fun ReportRow(
    row: MissingKanjiRow,
    selected: Boolean,
    copy: MissingKanjiCopy,
    dispatch: (KaniAction) -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(missingRowTestTag(row.literal))
            .semantics { contentDescription = "${row.literal} ${row.meaning} ${row.reading}" },
        shape = KaniUiTokens.StudyShapeSmall,
        color = KaniTheme.colors.panelSoft,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = row.literal, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyWordHeroTextSizeSp.sp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = row.meaning, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp, fontWeight = FontWeight.Medium)
                for (line in listOf(row.reading, row.rankLine).filter { it.isNotBlank() }) {
                    Text(text = line, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
                }
            }
            if (row.inKani && row.canRemove) {
                TextButton(
                    onClick = { dispatch(row.removeAction) },
                    shape = KaniUiTokens.ButtonShape,
                ) {
                    Text(text = "−", color = KaniTheme.colors.coral)
                }
            } else {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag(missingRowSelectTestTag(row.literal)),
                )
            }
        }
    }
}

@Composable
private fun ResultDialog(result: MissingKanjiOperationResult, copy: MissingKanjiCopy, dispatch: (KaniAction) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(MISSING_RESULT_TEST_TAG),
        shape = KaniUiTokens.PanelShape,
        color = KaniTheme.colors.panel,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = result.title, color = KaniTheme.colors.ink, fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp, fontWeight = FontWeight.Bold)
            for (line in result.lines) {
                Text(text = line, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp)
            }
            Button(
                onClick = { dispatch(KaniAction.MissingKanji.DismissResult) },
                modifier = Modifier.fillMaxWidth().heightIn(min = ACTION_MIN_HEIGHT).testTag(MISSING_RESULT_DISMISS_TEST_TAG),
                shape = KaniUiTokens.ButtonShape,
            ) {
                Text(text = copy.dismiss)
            }
        }
    }
}

/** A stable remember key for the selection: the row literals it can hold. */
private fun MissingKanjiContent.Report.reportKeyOrRows(): String =
    rows.joinToString(",") { it.literal }

private val ACTION_MIN_HEIGHT = 54.dp
