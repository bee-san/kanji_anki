@file:JvmName("MissingKanjiCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.MissingKanjiSelection
import dev.bee.kanjianki.core.MissingKanjiTextCopy
import kotlinx.coroutines.delay

internal const val MISSING_KANJI_SCREEN_TAG = "missing-kanji-screen"
internal const val MISSING_KANJI_LIST_TAG = "missing-kanji-list"
internal const val MISSING_KANJI_SEARCH_TAG = "missing-kanji-search"
internal const val MISSING_KANJI_SELECT_VISIBLE_TAG = "missing-kanji-select-visible"
internal const val MISSING_KANJI_PRIMARY_ACTION_TAG = "missing-kanji-primary-action"
internal const val MISSING_KANJI_CANCEL_TAG = "missing-kanji-cancel"
internal const val MISSING_KANJI_MINIMUM_RANK_TAG = "missing-kanji-minimum-rank"
internal const val MISSING_KANJI_MAXIMUM_RANK_TAG = "missing-kanji-maximum-rank"
internal const val MISSING_KANJI_APPLY_RANGE_TAG = "missing-kanji-apply-range"
internal const val MISSING_KANJI_ADD_TO_KANI_TAG = "missing-kanji-add-to-kani"
internal const val MISSING_KANJI_CREATE_ANKI_TAG = "missing-kanji-create-anki"
internal const val MISSING_KANJI_CONFIRM_ADD_TAG = "missing-kanji-confirm-add"
internal const val MISSING_KANJI_REMOVE_TAG = "missing-kanji-remove"

internal fun missingKanjiRowTag(literal: String): String = "missing-kanji-row-$literal"

internal fun missingKanjiCheckboxTag(literal: String): String = "missing-kanji-checkbox-$literal"

internal fun missingKanjiPresetTag(preset: MissingKanjiPreset): String =
    "missing-kanji-preset-${preset.storedValue}"

private val MissingKanjiSelectionSaver = listSaver<MissingKanjiSelection, String>(
    save = { selection -> selection.selectedLiterals.toList() },
    restore = MissingKanjiSelection::from,
)

@Composable
internal fun MissingKanjiScreen(model: MissingKanjiScreenModel) {
    val report = (model.content as? MissingKanjiContentModel.Report)?.report
    var selection by rememberSaveable(stateSaver = MissingKanjiSelectionSaver) {
        mutableStateOf(MissingKanjiSelection.empty())
    }
    var detailLiteral by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAddToKani by remember { mutableStateOf<Set<String>?>(null) }
    var pendingRemoveFromKani by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf(model.frequency.searchQuery) }
    val listState = rememberLazyListState()
    val visibleRows = remember(report?.rows, searchQuery) {
        report?.let { filterMissingKanjiRows(it.rows, searchQuery) }.orEmpty()
    }

    LaunchedEffect(report?.reportKey) {
        val available = report?.rows
            ?.asSequence()
            ?.map(MissingKanjiRowModel::literal)
            ?.toHashSet()
            .orEmpty()
        selection = MissingKanjiSelection.from(
            selection.selectedLiterals.filter(available::contains),
        )
        if (detailLiteral !in available) {
            detailLiteral = null
        }
    }
    LaunchedEffect(searchQuery) {
        if (searchQuery != model.frequency.searchQuery) {
            delay(SEARCH_PERSIST_DEBOUNCE_MS)
            model.onSearchQueryChanged(searchQuery)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(MISSING_KANJI_SCREEN_TAG),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(MISSING_KANJI_LIST_TAG),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "home") {
                HomeFullWidthHomeButton(
                    label = MissingKanjiTextCopy.homeLabel(),
                    onClick = model.onHome,
                )
            }
            item(key = "title") {
                MissingKanjiHeader()
            }

            when (val content = model.content) {
                MissingKanjiContentModel.FirstRun -> {
                    item(key = "first-run") {
                        MissingKanjiStateSection(
                            title = MissingKanjiTextCopy.firstRunTitle(),
                            body = MissingKanjiTextCopy.firstRunBody(),
                        )
                    }
                    item(key = "frequency") {
                        MissingKanjiFrequencyControls(model)
                    }
                    item(key = "primary-action") {
                        MissingKanjiPrimaryAction(model)
                    }
                }

                MissingKanjiContentModel.AnkiDroidMissing -> {
                    item(key = "anki-missing") {
                        MissingKanjiStateSection(
                            title = MissingKanjiTextCopy.ankiDroidMissingTitle(),
                            body = MissingKanjiTextCopy.ankiDroidMissingBody(),
                        )
                    }
                    item(key = "primary-action") {
                        MissingKanjiPrimaryAction(model)
                    }
                }

                MissingKanjiContentModel.PermissionRequired -> {
                    item(key = "permission") {
                        MissingKanjiStateSection(
                            title = MissingKanjiTextCopy.permissionTitle(),
                            body = MissingKanjiTextCopy.permissionBody(),
                        )
                    }
                    item(key = "primary-action") {
                        MissingKanjiPrimaryAction(model)
                    }
                }

                is MissingKanjiContentModel.Scanning -> {
                    item(key = "scanning") {
                        MissingKanjiScanningSection(
                            progress = content.progress,
                            onCancel = model.onCancelScan,
                        )
                    }
                }

                is MissingKanjiContentModel.Error -> {
                    item(key = "error") {
                        MissingKanjiStateSection(
                            title = MissingKanjiTextCopy.scanErrorTitle(content.failureCode),
                            body = MissingKanjiTextCopy.scanErrorBody(content.failureCode),
                        )
                    }
                    item(key = "frequency") {
                        MissingKanjiFrequencyControls(model)
                    }
                    item(key = "primary-action") {
                        MissingKanjiPrimaryAction(model)
                    }
                }

                is MissingKanjiContentModel.Report -> {
                    val uiReport = content.report
                    item(key = "summary") {
                        MissingKanjiReportSummary(uiReport)
                    }
                    item(key = "scan-again") {
                        MissingKanjiPrimaryAction(model)
                    }
                    item(key = "frequency") {
                        MissingKanjiFrequencyControls(model)
                    }
                    if (uiReport.rows.isEmpty()) {
                        item(key = "empty-report") {
                            MissingKanjiEmptyReport(uiReport)
                        }
                    } else {
                        item(key = "search-and-selection") {
                            MissingKanjiSearchAndSelection(
                                report = uiReport,
                                visibleRows = visibleRows,
                                query = searchQuery,
                                onQueryChanged = { searchQuery = it },
                                selection = selection,
                                onSelectionChanged = { selection = it },
                            )
                        }
                        if (
                            !model.destinations.addToKaniEnabled &&
                            !model.destinations.createAnkiDeckEnabled
                        ) {
                            item(key = "destination-preview") {
                                MissingKanjiDestinationBar(
                                    selected = selection,
                                    destinations = model.destinations.copy(
                                        onAddToKani = { literals -> pendingAddToKani = literals },
                                    ),
                                )
                            }
                        }
                        if (visibleRows.isEmpty()) {
                            item(key = "empty-search") {
                                MissingKanjiStateSection(
                                    title = MissingKanjiTextCopy.noSearchResultsTitle(),
                                    body = MissingKanjiTextCopy.noSearchResultsBody(),
                                )
                            }
                        } else {
                            items(
                                items = visibleRows,
                                key = MissingKanjiRowModel::literal,
                                contentType = { "missing-kanji-row" },
                            ) { row ->
                                MissingKanjiResultRow(
                                    row = row,
                                    selected = selection.isSelected(row.literal),
                                    onToggle = {
                                        selection = selection.toggle(row.literal)
                                    },
                                    onDetails = {
                                        detailLiteral = row.literal
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item(key = "bottom-space") {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        if (
            report != null &&
            report.rows.isNotEmpty() &&
            (
                model.destinations.addToKaniEnabled ||
                model.destinations.createAnkiDeckEnabled
            )
        ) {
            MissingKanjiDestinationBar(
                selected = selection,
                destinations = model.destinations.copy(
                    onAddToKani = { literals -> pendingAddToKani = literals },
                ),
            )
        }
    }

    val detail = report?.rows?.firstOrNull { row -> row.literal == detailLiteral }
    if (detail != null) {
        MissingKanjiDetailsDialog(
            row = detail,
            onDismiss = { detailLiteral = null },
            onRemove = if (detail.canRemoveFromKani) {
                {
                    detailLiteral = null
                    pendingRemoveFromKani = detail.literal
                }
            } else {
                null
            },
        )
    }
    pendingAddToKani?.let { literals ->
        MissingKanjiAddConfirmationDialog(
            count = literals.size,
            newPerDay = model.destinations.newPerDay,
            onConfirm = {
                pendingAddToKani = null
                model.destinations.onAddToKani(literals)
            },
            onDismiss = { pendingAddToKani = null },
        )
    }
    pendingRemoveFromKani?.let { literal ->
        MissingKanjiRemoveConfirmationDialog(
            literal = literal,
            onConfirm = {
                pendingRemoveFromKani = null
                model.destinations.onRemoveFromKani(literal)
            },
            onDismiss = { pendingRemoveFromKani = null },
        )
    }
    model.operationResult?.let { result ->
        MissingKanjiOperationResultDialog(
            result = result,
            onDismiss = model.onDismissOperationResult,
            onStudyNow = model.onStudyNow,
        )
    }
}

@Composable
private fun MissingKanjiHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = MissingKanjiTextCopy.title(),
            color = KaniUiTokens.Ink,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Text(
            text = MissingKanjiTextCopy.subtitle(),
            color = KaniUiTokens.Muted,
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.sp),
        )
    }
}

@Composable
private fun MissingKanjiStateSection(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(color = KaniUiTokens.PanelBorder)
        Text(
            text = title,
            color = KaniUiTokens.Ink,
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 0.sp),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            color = KaniUiTokens.Muted,
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.sp),
        )
    }
}

@Composable
private fun MissingKanjiScanningSection(
    progress: MissingKanjiScanProgressState,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MissingKanjiStateSection(
            title = MissingKanjiTextCopy.scanningTitle(),
            body = MissingKanjiTextCopy.scanningBody(),
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = KaniUiTokens.Teal,
        )
        Text(
            text = MissingKanjiTextCopy.scanningProgress(
                notesScanned = progress.notesScanned,
                uniqueKanji = progress.uniqueKanjiCount,
            ),
            color = KaniUiTokens.Ink,
            fontWeight = FontWeight.Bold,
        )
        if (progress.skippedNotes > 0) {
            Text(
                text = MissingKanjiTextCopy.scanningSkipped(progress.skippedNotes),
                color = KaniUiTokens.Coral,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (progress.isCancelling) {
            Text(
                text = MissingKanjiTextCopy.cancellingLabel(),
                color = KaniUiTokens.Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        KaniOutlinedButton(
            label = MissingKanjiTextCopy.cancelLabel(),
            modifier = Modifier.testTag(MISSING_KANJI_CANCEL_TAG),
            enabled = !progress.isCancelling,
            onClick = onCancel,
        )
    }
}

@Composable
private fun MissingKanjiPrimaryAction(model: MissingKanjiScreenModel) {
    val label = when (model.primaryAction) {
        MissingKanjiPrimaryAction.SCAN -> MissingKanjiTextCopy.scanAnkiLabel()
        MissingKanjiPrimaryAction.SCAN_AGAIN,
        MissingKanjiPrimaryAction.RETRY -> MissingKanjiTextCopy.scanAgainLabel()
        MissingKanjiPrimaryAction.GRANT_PERMISSION -> MissingKanjiTextCopy.grantAccessLabel()
        MissingKanjiPrimaryAction.INSTALL_ANKIDROID -> MissingKanjiTextCopy.installAnkiDroidLabel()
    }
    KaniPrimaryButton(
        label = label,
        modifier = Modifier.testTag(MISSING_KANJI_PRIMARY_ACTION_TAG),
        onClick = model.onPrimaryAction,
    )
}

@Composable
private fun MissingKanjiFrequencyControls(model: MissingKanjiScreenModel) {
    var selectedPreset by rememberSaveable(model.frequency.preset) {
        mutableStateOf(model.frequency.preset)
    }
    var minimumText by rememberSaveable(model.frequency.range.minimumRank) {
        mutableStateOf(model.frequency.range.minimumRank.toString())
    }
    var maximumText by rememberSaveable(model.frequency.range.maximumRank) {
        mutableStateOf(model.frequency.range.maximumRank.toString())
    }
    var includeUnranked by rememberSaveable(model.frequency.range.includeUnranked) {
        mutableStateOf(model.frequency.range.includeUnranked)
    }
    var previewCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var previewLoading by rememberSaveable { mutableStateOf(false) }
    var latestPreviewRange by remember {
        mutableStateOf<MissingKanjiFrequencyRange?>(null)
    }
    val rangeResult = parseMissingKanjiRange(minimumText, maximumText, includeUnranked)

    if (selectedPreset == MissingKanjiPreset.CUSTOM) {
        LaunchedEffect(minimumText, maximumText, includeUnranked) {
            val valid = rangeResult as? MissingKanjiRangeInputResult.Valid
            if (valid == null) {
                latestPreviewRange = null
                previewCount = null
                previewLoading = false
            } else {
                delay(RANGE_PREVIEW_DEBOUNCE_MS)
                latestPreviewRange = valid.range
                previewLoading = true
                model.onRangePreview(valid.range) { count ->
                    if (latestPreviewRange == valid.range) {
                        previewCount = count
                        previewLoading = false
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(color = KaniUiTokens.PanelBorder)
        Text(
            text = MissingKanjiTextCopy.frequencyTitle(),
            color = KaniUiTokens.Ink,
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.sp),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = MissingKanjiTextCopy.frequencyBody(),
            color = KaniUiTokens.Muted,
            style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = MissingKanjiPreset.entries,
                key = MissingKanjiPreset::storedValue,
            ) { preset ->
                val label = preset.range?.maximumRank?.let(MissingKanjiTextCopy::topPresetLabel)
                    ?: MissingKanjiTextCopy.customLabel()
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = {
                        selectedPreset = preset
                        preset.range?.let { range ->
                            minimumText = range.minimumRank.toString()
                            maximumText = range.maximumRank.toString()
                            includeUnranked = false
                            model.onRangeApplied(preset, range)
                        }
                    },
                    label = {
                        Text(
                            text = label,
                            letterSpacing = 0.sp,
                            maxLines = 1,
                        )
                    },
                    modifier = Modifier.testTag(missingKanjiPresetTag(preset)),
                )
            }
        }
        if (selectedPreset == MissingKanjiPreset.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MissingKanjiRankField(
                    value = minimumText,
                    label = MissingKanjiTextCopy.minimumRankLabel(),
                    testTag = MISSING_KANJI_MINIMUM_RANK_TAG,
                    modifier = Modifier.weight(1f),
                    onValueChange = { minimumText = it.filter(Char::isDigit).take(9) },
                )
                MissingKanjiRankField(
                    value = maximumText,
                    label = MissingKanjiTextCopy.maximumRankLabel(),
                    testTag = MISSING_KANJI_MAXIMUM_RANK_TAG,
                    modifier = Modifier.weight(1f),
                    onValueChange = { maximumText = it.filter(Char::isDigit).take(9) },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Checkbox,
                        onClick = { includeUnranked = !includeUnranked },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = includeUnranked,
                    onCheckedChange = { includeUnranked = it },
                    colors = CheckboxDefaults.colors(checkedColor = KaniUiTokens.Teal),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = MissingKanjiTextCopy.includeUnrankedLabel(),
                        color = KaniUiTokens.Ink,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = MissingKanjiTextCopy.includeUnrankedHelper(),
                        color = KaniUiTokens.Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            when {
                rangeResult is MissingKanjiRangeInputResult.Invalid -> Text(
                    text = MissingKanjiTextCopy.invalidRangeMessage(rangeResult.reason),
                    color = KaniUiTokens.Coral,
                    style = MaterialTheme.typography.bodySmall,
                )

                previewLoading -> Text(
                    text = MissingKanjiTextCopy.expectedEligibleLoading(),
                    color = KaniUiTokens.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )

                previewCount != null -> Text(
                    text = MissingKanjiTextCopy.expectedEligibleCount(requireNotNull(previewCount)),
                    color = KaniUiTokens.Teal,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            KaniPrimaryButton(
                label = MissingKanjiTextCopy.applyRangeLabel(),
                modifier = Modifier.testTag(MISSING_KANJI_APPLY_RANGE_TAG),
                minHeightDp = 48,
                enabled = rangeResult is MissingKanjiRangeInputResult.Valid && !previewLoading,
                onClick = {
                    val valid = rangeResult as? MissingKanjiRangeInputResult.Valid
                    if (valid != null) {
                        model.onRangeApplied(MissingKanjiPreset.CUSTOM, valid.range)
                    }
                },
            )
        }
    }
}

@Composable
private fun MissingKanjiRankField(
    value: String,
    label: String,
    testTag: String,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .testTag(testTag)
            .semantics { contentDescription = label },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun MissingKanjiReportSummary(report: MissingKanjiReportUiModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = MissingKanjiTextCopy.lastScanLabel(report.scan.completedAtMillis),
            color = KaniUiTokens.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        report.staleReason?.let { reason ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = KaniUiTokens.Coral.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, KaniUiTokens.Coral.copy(alpha = 0.55f)),
            ) {
                Text(
                    text = MissingKanjiTextCopy.staleResultsLabel(reason.copyKey),
                    modifier = Modifier.padding(12.dp),
                    color = KaniUiTokens.Ink,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (report.scan.skippedNotes > 0) {
            Text(
                text = MissingKanjiTextCopy.malformedRowsWarning(report.scan.skippedNotes),
                color = KaniUiTokens.Coral,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        MissingKanjiMetricRow(
            first = MissingKanjiTextCopy.notesScannedMetric(report.scan.notesScanned),
            second = MissingKanjiTextCopy.uniqueAnkiMetric(report.scan.uniqueAnkiKanjiCount),
        )
        MissingKanjiMetricRow(
            first = MissingKanjiTextCopy.eligibleMetric(report.eligibleDictionaryKanjiCount),
            second = MissingKanjiTextCopy.missingMetric(report.missingKanjiCount),
        )
    }
}

@Composable
private fun MissingKanjiMetricRow(
    first: String,
    second: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = first,
            modifier = Modifier.weight(1f),
            color = KaniUiTokens.Ink,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = second,
            modifier = Modifier.weight(1f),
            color = KaniUiTokens.Ink,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MissingKanjiSearchAndSelection(
    report: MissingKanjiReportUiModel,
    visibleRows: List<MissingKanjiRowModel>,
    query: String,
    onQueryChanged: (String) -> Unit,
    selection: MissingKanjiSelection,
    onSelectionChanged: (MissingKanjiSelection) -> Unit,
) {
    val allVisibleSelected = visibleRows.isNotEmpty() &&
        visibleRows.all { row -> selection.isSelected(row.literal) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(color = KaniUiTokens.PanelBorder)
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChanged(it.take(MAX_SEARCH_CHARS)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MISSING_KANJI_SEARCH_TAG),
            singleLine = true,
            label = { Text(MissingKanjiTextCopy.searchLabel()) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChanged("") },
                        modifier = Modifier.semantics {
                            contentDescription = MissingKanjiTextCopy.clearSearchDescription()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close_24),
                            contentDescription = null,
                        )
                    }
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = MissingKanjiTextCopy.visibleResultCount(
                        visible = visibleRows.size,
                        total = report.rows.size,
                    ),
                    color = KaniUiTokens.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = MissingKanjiTextCopy.selectedCount(selection.size),
                    color = KaniUiTokens.Teal,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            KaniOutlinedButton(
                label = if (allVisibleSelected) {
                    MissingKanjiTextCopy.clearVisibleLabel(visibleRows.size)
                } else {
                    MissingKanjiTextCopy.selectVisibleLabel(visibleRows.size)
                },
                modifier = Modifier
                    .width(190.dp)
                    .testTag(MISSING_KANJI_SELECT_VISIBLE_TAG),
                minHeightDp = 48,
                textSizeSp = 13,
                enabled = visibleRows.isNotEmpty(),
                onClick = {
                    val visibleLiterals = visibleRows.map(MissingKanjiRowModel::literal)
                    onSelectionChanged(
                        if (allVisibleSelected) {
                            MissingKanjiSelection.from(
                                selection.selectedLiterals - visibleLiterals.toSet(),
                            )
                        } else {
                            MissingKanjiSelection.from(
                                selection.selectedLiterals + visibleLiterals,
                            )
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun MissingKanjiEmptyReport(report: MissingKanjiReportUiModel) {
    val noEligible = report.eligibleDictionaryKanjiCount == 0
    MissingKanjiStateSection(
        title = if (noEligible) {
            MissingKanjiTextCopy.noEligibleTitle()
        } else {
            MissingKanjiTextCopy.noneMissingTitle()
        },
        body = if (noEligible) {
            MissingKanjiTextCopy.noEligibleBody()
        } else {
            MissingKanjiTextCopy.noneMissingBody()
        },
    )
}

@Composable
private fun MissingKanjiResultRow(
    row: MissingKanjiRowModel,
    selected: Boolean,
    onToggle: () -> Unit,
    onDetails: () -> Unit,
) {
    val meaning = row.primaryMeaning.ifBlank(MissingKanjiTextCopy::unknownMeaningLabel)
    val reading = row.primaryReading.ifBlank(MissingKanjiTextCopy::noReadingLabel)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .testTag(missingKanjiRowTag(row.literal))
            .semantics {
                contentDescription = MissingKanjiTextCopy.rowDescription(
                    literal = row.literal,
                    meaning = meaning,
                    reading = reading,
                    rank = row.jitenRank,
                    selected = selected,
                )
            }
            .clickable(role = Role.Button, onClick = onDetails),
        shape = RoundedCornerShape(8.dp),
        color = KaniUiTokens.White,
        border = BorderStroke(
            1.dp,
            if (selected) KaniUiTokens.Teal else KaniUiTokens.PanelBorder,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                modifier = Modifier
                    .testTag(missingKanjiCheckboxTag(row.literal))
                    .semantics {
                        contentDescription = MissingKanjiTextCopy.selectionDescription(row.literal)
                    },
                colors = CheckboxDefaults.colors(checkedColor = KaniUiTokens.Teal),
            )
            Text(
                text = row.literal,
                modifier = Modifier.width(54.dp),
                color = KaniUiTokens.Ink,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = meaning,
                    color = KaniUiTokens.Ink,
                    style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = reading,
                    color = KaniUiTokens.Teal,
                    style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = MissingKanjiTextCopy.rankLabel(row.jitenRank),
                    color = KaniUiTokens.Muted,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                )
                if (row.inKani) {
                    Text(
                        text = MissingKanjiTextCopy.inKaniLabel(),
                        color = KaniUiTokens.Teal,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            IconButton(onClick = onDetails) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward_24),
                    contentDescription = MissingKanjiTextCopy.detailsTitle(row.literal),
                    tint = KaniUiTokens.Muted,
                )
            }
        }
    }
}

@Composable
private fun MissingKanjiDestinationBar(
    selected: MissingKanjiSelection,
    destinations: MissingKanjiDestinationModel,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = KaniUiTokens.PanelFill,
        border = BorderStroke(1.dp, KaniUiTokens.PanelBorder),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = MissingKanjiTextCopy.selectedCount(selected.size),
                    color = KaniUiTokens.Ink,
                    fontWeight = FontWeight.Bold,
                )
            }
            val addButton: @Composable (Modifier) -> Unit = { modifier ->
                KaniOutlinedButton(
                    label = MissingKanjiTextCopy.addToKaniLabel(),
                    modifier = modifier.testTag(MISSING_KANJI_ADD_TO_KANI_TAG),
                    minHeightDp = 48,
                    textSizeSp = 14,
                    enabled = destinations.addToKaniEnabled &&
                        !destinations.operationInProgress &&
                        selected.size > 0,
                    onClick = {
                        destinations.onAddToKani(selected.selectedLiterals)
                    },
                )
            }
            val ankiButton: @Composable (Modifier) -> Unit = { modifier ->
                KaniOutlinedButton(
                    label = MissingKanjiTextCopy.createAnkiDeckLabel(),
                    modifier = modifier.testTag(MISSING_KANJI_CREATE_ANKI_TAG),
                    minHeightDp = 48,
                    textSizeSp = 14,
                    enabled = destinations.createAnkiDeckEnabled &&
                        !destinations.operationInProgress &&
                        selected.size > 0,
                    onClick = {
                        destinations.onCreateAnkiDeck(selected.selectedLiterals)
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                addButton(Modifier.weight(1f))
                ankiButton(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MissingKanjiDetailsDialog(
    row: MissingKanjiRowModel,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = MissingKanjiTextCopy.detailsTitle(row.literal),
                letterSpacing = 0.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = row.literal,
                    color = KaniUiTokens.Ink,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                )
                MissingKanjiDetailLine(
                    label = MissingKanjiTextCopy.meaningsLabel(),
                    values = row.meanings,
                    separator = ", ",
                )
                MissingKanjiDetailLine(
                    label = MissingKanjiTextCopy.onReadingsLabel(),
                    values = row.onReadings,
                    separator = "、",
                )
                MissingKanjiDetailLine(
                    label = MissingKanjiTextCopy.kunReadingsLabel(),
                    values = row.kunReadings,
                    separator = "、",
                )
                Text(
                    text = MissingKanjiTextCopy.rankLabel(row.jitenRank),
                    color = KaniUiTokens.Teal,
                    fontWeight = FontWeight.Bold,
                )
                if (row.inKani) {
                    Text(
                        text = MissingKanjiTextCopy.inKaniLabel(),
                        color = KaniUiTokens.Teal,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaniUiTokens.Primary,
                ),
            ) {
                Text(MissingKanjiTextCopy.closeLabel())
            }
        },
        dismissButton = if (onRemove == null) {
            null
        } else {
            {
                TextButton(
                    modifier = Modifier.testTag(MISSING_KANJI_REMOVE_TAG),
                    onClick = onRemove,
                ) {
                    Text(MissingKanjiTextCopy.removeFromKaniLabel())
                }
            }
        },
    )
}

@Composable
private fun MissingKanjiAddConfirmationDialog(
    count: Int,
    newPerDay: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(MissingKanjiTextCopy.addToKaniConfirmationTitle()) },
        text = {
            Text(
                MissingKanjiTextCopy.addToKaniConfirmationBody(
                    count = count,
                    newPerDay = newPerDay,
                ),
            )
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag(MISSING_KANJI_CONFIRM_ADD_TAG),
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = KaniUiTokens.Primary),
            ) {
                Text(MissingKanjiTextCopy.confirmAddToKaniLabel())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(MissingKanjiTextCopy.closeLabel())
            }
        },
    )
}

@Composable
private fun MissingKanjiRemoveConfirmationDialog(
    literal: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(MissingKanjiTextCopy.removeFromKaniConfirmationTitle(literal)) },
        text = { Text(MissingKanjiTextCopy.removeFromKaniConfirmationBody()) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = KaniUiTokens.Primary),
            ) {
                Text(MissingKanjiTextCopy.removeFromKaniLabel())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(MissingKanjiTextCopy.closeLabel())
            }
        },
    )
}

@Composable
private fun MissingKanjiOperationResultDialog(
    result: MissingKanjiOperationResultModel,
    onDismiss: () -> Unit,
    onStudyNow: () -> Unit,
) {
    val title: String
    val body: String
    val canStudyNow: Boolean
    when (result) {
        is MissingKanjiOperationResultModel.KaniAdmission -> {
            title = MissingKanjiTextCopy.kaniAdmissionResultTitle()
            body = MissingKanjiTextCopy.kaniAdmissionResultBody(
                added = result.addedCount,
                alreadyInKani = result.alreadyInKaniCount,
                admittedNow = result.admittedNowCount,
                deferred = result.deferredCount,
                skipped = result.skippedMissingMeaningCount +
                    result.skippedMissingReadingCount +
                    result.invalidCount,
            )
            canStudyNow = result.admittedNowCount > 0
        }
        is MissingKanjiOperationResultModel.KaniRemoval -> {
            if (result.removed || result.reviewed) {
                title = MissingKanjiTextCopy.kaniAdmissionResultTitle()
                body = if (result.reviewed) {
                    MissingKanjiTextCopy.reviewedSourceKeptBody(result.literal)
                } else {
                    MissingKanjiTextCopy.removedFromKaniBody(result.literal)
                }
            } else {
                title = MissingKanjiTextCopy.operationFailedTitle()
                body = MissingKanjiTextCopy.operationFailedBody()
            }
            canStudyNow = false
        }
        MissingKanjiOperationResultModel.Failed -> {
            title = MissingKanjiTextCopy.operationFailedTitle()
            body = MissingKanjiTextCopy.operationFailedBody()
            canStudyNow = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onClick = if (canStudyNow) onStudyNow else onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = KaniUiTokens.Primary),
            ) {
                Text(
                    if (canStudyNow) {
                        MissingKanjiTextCopy.studyNowLabel()
                    } else {
                        MissingKanjiTextCopy.closeLabel()
                    },
                )
            }
        },
        dismissButton = if (!canStudyNow) {
            null
        } else {
            {
                TextButton(onClick = onDismiss) {
                    Text(MissingKanjiTextCopy.closeLabel())
                }
            }
        },
    )
}

@Composable
private fun MissingKanjiDetailLine(
    label: String,
    values: List<String>,
    separator: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = KaniUiTokens.Muted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = values.joinToString(separator).ifBlank(MissingKanjiTextCopy::noValuesLabel),
            color = KaniUiTokens.Ink,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val MAX_SEARCH_CHARS = 128
private const val SEARCH_PERSIST_DEBOUNCE_MS = 300L
private const val RANGE_PREVIEW_DEBOUNCE_MS = 250L
