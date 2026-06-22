@file:JvmName("MainActivityStudyAnswerDetailsCompose")
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.core.net.toUri
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import java.util.Locale
import kotlinx.coroutines.delay

private const val ANKI_DROID_PACKAGE = "com.ichi2.anki"
private const val ANKI_DROID_FLASHCARDS_AUTHORITY = "com.ichi2.anki.flashcards"

private val StudyAnswerPlum: androidx.compose.ui.graphics.Color @Composable get() = KaniTheme.colors.plum
private val StudyAnswerMuted: androidx.compose.ui.graphics.Color @Composable get() = KaniTheme.colors.muted
private val StudyAnswerPanelFill: androidx.compose.ui.graphics.Color @Composable get() = KaniTheme.colors.panel
private val StudyAnswerPanelSoft: androidx.compose.ui.graphics.Color @Composable get() = KaniTheme.colors.panelSoft
private val StudyAnswerPanelFillSoft: androidx.compose.ui.graphics.Color @Composable get() = KaniTheme.colors.panelFill
private val StudyAnswerBorder: androidx.compose.ui.graphics.Color @Composable get() = KaniTheme.colors.border
private val StudyAnswerBorderSoft: androidx.compose.ui.graphics.Color @Composable get() = KaniTheme.colors.borderSoft
private val StudyAnswerPillFill: androidx.compose.ui.graphics.Color @Composable get() = KaniTheme.colors.pill

private val TEST_TAG_SANITIZE_REGEX = Regex("[^a-z0-9]+")

internal fun studyAnswerPanelStateKey(session: RecordsSchedulerModels.StudySession): String {
    return listOf(
        session.token,
        session.taskType,
        session.prompt,
        session.item?.kanji.orEmpty(),
        session.row?.kanji.orEmpty(),
        session.row?.primaryMeaning.orEmpty(),
        session.row?.reading.orEmpty(),
        session.row?.browserSearch.orEmpty(),
    ).joinToString("|")
}

internal fun studyAnswerPanelStateKey(model: StudyAnswerPanelModel): String {
    if (model.stateKey.isNotBlank()) {
        return model.stateKey
    }
    return listOf(
        model.title,
        model.glyph,
        model.glyphSizeSp.toString(),
        model.helperText.orEmpty(),
        model.kanjiDetails?.kanji.orEmpty(),
        model.lines.joinToString(separator = "¦") { it.text },
    ).joinToString("|")
}

internal fun studyAnswerKanjiDetailsModel(
    activity: MainActivityStudy,
    session: RecordsSchedulerModels.StudySession,
): StudyAnswerKanjiDetailsModel? {
    val kanji = session.item?.kanji?.trim().orEmpty()
    if (kanji.isEmpty()) {
        return null
    }
    val dictionaryEntry = activity.currentDictionaryLookup().lookupKanji(kanji)
    val examples = session.row?.examples.orEmpty()
    val currentExample = activity.exampleForSession(session)
    return studyAnswerKanjiDetailsModel(
        kanji = kanji,
        dictionaryEntry = dictionaryEntry,
        examples = examples,
        currentExample = currentExample,
        showAllUsedInAnki = false,
        openAnkiDroidSupported = activity.gateway.status().canSync,
        deckNamesByCardId = emptyMap(),
        modelNamesByNoteId = emptyMap(),
        strokeOrderAssetAvailable = false,
        strokeOrderAssetReference = null,
        breakdownComponentRows = emptyList(),
    )
}

internal fun studyAnswerAccordionHeaderTestTag(label: String): String {
    return "study-answer-section-${sanitizeTestTagPart(label)}"
}

internal fun studyAnswerUsedInAnkiRowTestTag(index: Int): String {
    return "study-answer-used-in-anki-row-$index"
}

internal fun studyAnswerUsedInAnkiToggleTestTag(): String {
    return "study-answer-used-in-anki-toggle"
}

private fun sanitizeTestTagPart(value: String): String {
    return TEST_TAG_SANITIZE_REGEX.replace(value.lowercase(Locale.ROOT), "-").trim('-').ifBlank {
        "section"
    }
}

@Composable
internal fun StudyAnswerKanjiDetailsStack(
    details: StudyAnswerKanjiDetailsModel,
    panelStateKey: String,
    modifier: Modifier = Modifier,
    onAnkiTapAction: ((StudyAnswerAnkiTapActionModel) -> Unit)? = null,
    initialExpandedSectionLabel: String? = null,
    initialUsedInAnkiShowAll: Boolean = false,
) {
    var expandedSectionLabel by rememberSaveable(panelStateKey) { mutableStateOf(initialExpandedSectionLabel) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StudyAnswerDictionarySection(
            section = details.details,
            expanded = expandedSectionLabel == details.details.label,
            onToggle = {
                expandedSectionLabel = if (expandedSectionLabel == details.details.label) {
                    null
                } else {
                    details.details.label
                }
            },
        )
        StudyAnswerBreakdownSection(
            section = details.breakdown,
            expanded = expandedSectionLabel == details.breakdown.label,
            onToggle = {
                expandedSectionLabel = if (expandedSectionLabel == details.breakdown.label) {
                    null
                } else {
                    details.breakdown.label
                }
            },
        )
        StudyAnswerStrokeOrderSection(
            section = details.strokeOrder,
            expanded = expandedSectionLabel == details.strokeOrder.label,
            onToggle = {
                expandedSectionLabel = if (expandedSectionLabel == details.strokeOrder.label) {
                    null
                } else {
                    details.strokeOrder.label
                }
            },
        )
        StudyAnswerUsedInAnkiSection(
            section = details.usedInAnki,
            expanded = expandedSectionLabel == details.usedInAnki.label,
            panelStateKey = panelStateKey,
            onToggle = {
                expandedSectionLabel = if (expandedSectionLabel == details.usedInAnki.label) {
                    null
                } else {
                    details.usedInAnki.label
                }
            },
            initialUsedInAnkiShowAll = initialUsedInAnkiShowAll,
            onAnkiTapAction = onAnkiTapAction,
        )
        StudyAnswerWhyThisCardSection(
            section = details.whyThisCard,
            expanded = expandedSectionLabel == details.whyThisCard.label,
            onToggle = {
                expandedSectionLabel = if (expandedSectionLabel == details.whyThisCard.label) {
                    null
                } else {
                    details.whyThisCard.label
                }
            },
        )
    }
}

@Composable
private fun StudyAnswerDictionarySection(
    section: StudyAnswerDetailSectionModel<StudyAnswerDictionaryMetadataModel>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    StudyAnswerAccordionSection(
        label = section.label,
        summary = section.summary,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        when (section.contentState) {
            StudyAnswerSectionContentState.READY -> {
                section.body?.let { body ->
                    StudyAnswerDictionaryMetadataBody(body)
                } ?: StudyAnswerEmptyState(section.emptyTitle, section.emptyBody)
            }
            StudyAnswerSectionContentState.EMPTY,
            StudyAnswerSectionContentState.UNAVAILABLE -> {
                StudyAnswerEmptyState(section.emptyTitle, section.emptyBody)
            }
        }
    }
}

@Composable
private fun StudyAnswerBreakdownSection(
    section: StudyAnswerDetailSectionModel<StudyAnswerBreakdownModel>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    StudyAnswerAccordionSection(
        label = section.label,
        summary = section.summary,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        when (section.contentState) {
            StudyAnswerSectionContentState.READY -> {
                section.body?.let { body ->
                    StudyAnswerBreakdownBody(body)
                } ?: StudyAnswerEmptyState(section.emptyTitle, section.emptyBody)
            }
            StudyAnswerSectionContentState.EMPTY,
            StudyAnswerSectionContentState.UNAVAILABLE -> {
                StudyAnswerEmptyState(section.emptyTitle, section.emptyBody)
            }
        }
    }
}

@Composable
private fun StudyAnswerStrokeOrderSection(
    section: StudyAnswerDetailSectionModel<StudyAnswerStrokeOrderModel>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    StudyAnswerAccordionSection(
        label = section.label,
        summary = section.summary,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        when (section.contentState) {
            StudyAnswerSectionContentState.READY -> {
                section.body?.let { body ->
                    StudyAnswerStrokeOrderBody(body)
                } ?: StudyAnswerEmptyState(section.emptyTitle, section.emptyBody)
            }
            StudyAnswerSectionContentState.EMPTY,
            StudyAnswerSectionContentState.UNAVAILABLE -> {
                StudyAnswerEmptyState(section.emptyTitle, section.emptyBody)
            }
        }
    }
}

@Composable
private fun StudyAnswerUsedInAnkiSection(
    section: StudyAnswerDetailSectionModel<StudyAnswerUsedInAnkiModel>,
    expanded: Boolean,
    panelStateKey: String,
    onToggle: () -> Unit,
    initialUsedInAnkiShowAll: Boolean,
    onAnkiTapAction: ((StudyAnswerAnkiTapActionModel) -> Unit)? = null,
) {
    StudyAnswerAccordionSection(
        label = section.label,
        summary = section.summary,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        when (section.contentState) {
            StudyAnswerSectionContentState.READY -> {
                section.body?.let { body ->
                    StudyAnswerUsedInAnkiBody(
                        body = body,
                        panelStateKey = panelStateKey,
                        initialShowAll = initialUsedInAnkiShowAll,
                        onAnkiTapAction = onAnkiTapAction,
                    )
                } ?: StudyAnswerEmptyState(section.emptyTitle, section.emptyBody)
            }
            StudyAnswerSectionContentState.EMPTY,
            StudyAnswerSectionContentState.UNAVAILABLE -> {
                StudyAnswerEmptyState(section.emptyTitle, section.emptyBody)
            }
        }
    }
}

@Composable
private fun StudyAnswerWhyThisCardSection(
    section: StudyAnswerDetailSectionModel<StudyAnswerWhyThisCardModel>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    StudyAnswerAccordionSection(
        label = section.label,
        summary = section.summary,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        when (section.contentState) {
            StudyAnswerSectionContentState.READY -> {
                section.body?.let { body ->
                    StudyAnswerWhyThisCardBody(body)
                } ?: StudyAnswerEmptyState(section.emptyTitle, section.emptyBody)
            }
            StudyAnswerSectionContentState.EMPTY,
            StudyAnswerSectionContentState.UNAVAILABLE -> {
                StudyAnswerEmptyState(section.emptyTitle, section.emptyBody)
            }
        }
    }
}

@Composable
private fun StudyAnswerAccordionSection(
    label: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag(studyAnswerAccordionHeaderTestTag(label))
            .semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .clickable(
                role = Role.Button,
                onClick = onToggle,
            ),
        shape = RoundedCornerShape(16.dp),
        color = StudyAnswerPanelSoft,
        border = BorderStroke(1.dp, StudyAnswerBorder),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = StudyAnswerPlum,
                    style = detailTextStyle(14),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        color = StudyAnswerMuted,
                        style = detailTextStyle(12),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Text(
                    text = if (expanded) "▴" else "▾",
                    color = StudyAnswerMuted,
                    style = detailTextStyle(18),
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 0.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun StudyAnswerDictionaryMetadataBody(body: StudyAnswerDictionaryMetadataModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (body.meanings.isNotEmpty()) {
            StudyAnswerSectionHeading("Meanings")
            StudyAnswerChipFlow(body.meanings)
        }
        if (body.readingGroups.isNotEmpty()) {
            body.readingGroups.forEach { group ->
                StudyAnswerSectionHeading(group.label)
                StudyAnswerChipFlow(group.readings)
            }
        }
        StudyAnswerMetricGrid(
            metrics = listOf(
                StudyAnswerMetric(
                    label = "Strokes",
                    value = body.strokeCount?.toString() ?: "Not available",
                    available = body.strokeCount != null,
                ),
                StudyAnswerMetric(
                    label = "Grade",
                    value = body.grade?.toString() ?: "Not graded",
                    available = body.grade != null,
                ),
                StudyAnswerMetric(
                    label = "Radical",
                    value = body.radical?.toString() ?: "Not available",
                    available = body.radical != null,
                ),
                StudyAnswerMetric(
                    label = "Frequency",
                    value = body.frequency?.toString() ?: "Not available",
                    available = body.frequency != null,
                ),
                StudyAnswerMetric(
                    label = "Jiten rank",
                    value = body.jitenRank?.toString() ?: "Not available",
                    available = body.jitenRank != null,
                ),
            ),
        )
    }
}

@Composable
private fun StudyAnswerBreakdownBody(body: StudyAnswerBreakdownModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        body.radicalNumber?.let { radical ->
            StudyAnswerMetricGrid(
                metrics = listOf(
                    StudyAnswerMetric(
                        label = "Radical",
                        value = radical.toString(),
                        available = true,
                    ),
                ),
            )
        }
        if (body.componentRows.isNotEmpty()) {
            StudyAnswerSectionHeading("Components")
            StudyAnswerChipFlow(body.componentRows)
        }
        body.fallbackCopy?.let { fallback ->
            StudyAnswerBodyNote(fallback)
        }
    }
}

@Composable
private fun StudyAnswerStrokeOrderBody(body: StudyAnswerStrokeOrderModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        body.strokeCount?.let { strokeCount ->
            StudyAnswerMetricGrid(
                metrics = listOf(
                    StudyAnswerMetric(
                        label = "Stroke count",
                        value = strokeCount.toString(),
                        available = true,
                    ),
                ),
            )
        }
        when (body.availability) {
            StudyAnswerStrokeOrderAvailability.ASSET_AVAILABLE -> {
                body.assetReference?.takeIf { it.isNotBlank() }?.let { reference ->
                    StudyAnswerBodyNote("Planned: $reference")
                } ?: StudyAnswerBodyNote("Animated guide ready")
            }
            StudyAnswerStrokeOrderAvailability.COUNT_ONLY -> {
                body.fallbackCopy?.let { fallback ->
                    StudyAnswerBodyNote(fallback)
                }
            }
            StudyAnswerStrokeOrderAvailability.UNAVAILABLE -> {
                body.fallbackCopy?.let { fallback ->
                    StudyAnswerBodyNote(fallback)
                }
            }
        }
    }
}

@Composable
private fun StudyAnswerUsedInAnkiBody(
    body: StudyAnswerUsedInAnkiModel,
    panelStateKey: String,
    initialShowAll: Boolean,
    onAnkiTapAction: ((StudyAnswerAnkiTapActionModel) -> Unit)?,
) {
    val storageKey = "$panelStateKey|${USED_IN_ANKI_LABEL_KEY}"
    var showAll by rememberSaveable(storageKey) { mutableStateOf(initialShowAll || body.showAll) }
    var feedbackMessage by rememberSaveable(storageKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(feedbackMessage) {
        val message = feedbackMessage ?: return@LaunchedEffect
        delay(2200)
        if (feedbackMessage == message) {
            feedbackMessage = null
        }
    }
    val rows = if (showAll) body.rows else body.visibleRows
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        feedbackMessage?.let { message ->
            StudyAnswerInlineFeedbackBanner(message = message)
        }
        rows.forEachIndexed { index, row ->
            StudyAnswerUsedInAnkiRow(
                row = row,
                index = index,
                onAnkiTapAction = onAnkiTapAction,
                onFeedback = { message -> feedbackMessage = message },
            )
        }
        if (body.rows.size > body.visibleRowLimit) {
            StudyAnswerUsedInAnkiToggle(
                totalRows = body.rows.size,
                showAll = showAll,
                onClick = { showAll = !showAll },
            )
        }
    }
}

@Composable
private fun StudyAnswerWhyThisCardBody(body: StudyAnswerWhyThisCardModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        body.sourceExpression?.let { expression ->
            StudyAnswerBodyNote("From: $expression", bold = true)
        }
        body.sourceReading?.let { reading ->
            StudyAnswerBodyNote("Reading: $reading")
        }
        if (body.previewExamples.isNotEmpty()) {
            StudyAnswerSectionHeading("Also appears in...")
            body.previewExamples.forEach { preview ->
                StudyAnswerPreviewExampleCard(preview)
            }
        } else if (body.fallbackCopy != null && body.sourceExpression.isNullOrBlank()) {
            StudyAnswerBodyNote(body.fallbackCopy)
        }
    }
}

@Composable
private fun StudyAnswerEmptyState(
    title: String?,
    body: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = StudyAnswerPanelFillSoft,
        border = BorderStroke(1.dp, StudyAnswerBorderSoft),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    color = StudyAnswerPlum,
                    style = detailTextStyle(13),
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!body.isNullOrBlank()) {
                Text(
                    text = body,
                    color = StudyAnswerMuted,
                    style = detailTextStyle(12),
                )
            }
        }
    }
}

@Composable
private fun StudyAnswerInlineFeedbackBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = StudyAnswerPanelFillSoft,
        border = BorderStroke(1.dp, StudyAnswerBorderSoft),
    ) {
        Text(
            text = message,
            color = StudyAnswerPlum,
            style = detailTextStyle(12),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StudyAnswerBodyNote(
    text: String,
    bold: Boolean = false,
) {
    Text(
        text = text,
        color = StudyAnswerMuted,
        style = detailTextStyle(if (bold) 13 else 12),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
private fun StudyAnswerSectionHeading(text: String) {
    Text(
        text = text,
        color = StudyAnswerPlum,
        style = detailTextStyle(12),
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun StudyAnswerChipFlow(values: List<String>) {
    if (values.isEmpty()) {
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        values.forEach { value ->
            StudyAnswerChip(value)
        }
    }
}

@Composable
private fun StudyAnswerChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = StudyAnswerPillFill,
        border = BorderStroke(1.dp, StudyAnswerBorderSoft),
    ) {
        Text(
            text = text,
            color = StudyAnswerPlum,
            style = detailTextStyle(12),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StudyAnswerMetricGrid(metrics: List<StudyAnswerMetric>) {
    if (metrics.isEmpty()) {
        return
    }
    val twoColumn = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp() > 360.dp
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!twoColumn) {
            metrics.forEach { metric ->
                StudyAnswerMetricCard(metric)
            }
        } else {
            metrics.chunked(2).forEach { rowMetrics ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowMetrics.forEach { metric ->
                        StudyAnswerMetricCard(
                            metric = metric,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowMetrics.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyAnswerMetricCard(
    metric: StudyAnswerMetric,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = StudyAnswerPanelFillSoft,
        border = BorderStroke(1.dp, StudyAnswerBorderSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = metric.label,
                color = StudyAnswerMuted,
                style = detailTextStyle(11),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = metric.value,
                color = if (metric.available) StudyAnswerPlum else StudyAnswerMuted,
                style = detailTextStyle(12),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StudyAnswerPreviewExampleCard(example: StudyAnswerWhyThisCardExampleModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = StudyAnswerPanelFillSoft,
        border = BorderStroke(1.dp, StudyAnswerBorderSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (example.expression.isNotBlank()) {
                Text(
                    text = example.expression,
                    color = StudyAnswerPlum,
                    style = detailTextStyle(13),
                    fontWeight = FontWeight.Bold,
                )
            }
            if (example.reading.isNotBlank()) {
                Text(
                    text = example.reading,
                    color = StudyAnswerMuted,
                    style = detailTextStyle(12),
                )
            }
            if (example.meaning.isNotBlank()) {
                Text(
                    text = example.meaning,
                    color = StudyAnswerMuted,
                    style = detailTextStyle(12),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StudyAnswerUsedInAnkiRow(
    row: StudyAnswerUsedInAnkiRowModel,
    index: Int,
    onAnkiTapAction: ((StudyAnswerAnkiTapActionModel) -> Unit)?,
    onFeedback: (String) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val actionEnabled = row.tapAction !is StudyAnswerAnkiTapActionModel.Unavailable
    val surfaceModifier = if (actionEnabled) {
        Modifier
            .fillMaxWidth()
            .testTag(studyAnswerUsedInAnkiRowTestTag(index))
            .clickable(
                role = Role.Button,
                onClick = {
                    onAnkiTapAction?.invoke(row.tapAction)
                    performStudyAnswerAnkiTapAction(
                        context = context,
                        clipboardManager = clipboardManager,
                        action = row.tapAction,
                        onFeedback = onFeedback,
                    )
                },
            )
    } else {
        Modifier
            .fillMaxWidth()
            .testTag(studyAnswerUsedInAnkiRowTestTag(index))
    }
    Surface(
        modifier = surfaceModifier,
        shape = RoundedCornerShape(14.dp),
        color = StudyAnswerPanelFillSoft,
        border = BorderStroke(
            1.dp,
            if (row.isPrimarySource) StudyAnswerBorder else StudyAnswerBorderSoft,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = row.expression,
                    color = StudyAnswerPlum,
                    style = detailTextStyle(14),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.isPrimarySource) {
                    StudyAnswerChip("Current")
                }
            }
            val secondaryText = listOf(row.reading.trim(), row.meaning.trim())
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            if (secondaryText.isNotBlank()) {
                Text(
                    text = secondaryText,
                    color = StudyAnswerMuted,
                    style = detailTextStyle(12),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val labels = listOfNotNull(row.sourceLabel, row.deckLabel, row.modelLabel)
            if (labels.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    labels.forEach { label ->
                        StudyAnswerChip(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyAnswerUsedInAnkiToggle(
    totalRows: Int,
    showAll: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(studyAnswerUsedInAnkiToggleTestTag())
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(14.dp),
        color = StudyAnswerPanelFillSoft,
        border = BorderStroke(1.dp, StudyAnswerBorderSoft),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (showAll) "Show fewer" else "Show all $totalRows",
                color = StudyAnswerPlum,
                style = detailTextStyle(13),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (showAll) "▴" else "▾",
                color = StudyAnswerMuted,
                style = detailTextStyle(18),
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

private fun performStudyAnswerAnkiTapAction(
    context: Context,
    clipboardManager: ClipboardManager,
    action: StudyAnswerAnkiTapActionModel,
    onFeedback: (String) -> Unit = {},
) {
    when (action) {
        is StudyAnswerAnkiTapActionModel.OpenAnkiDroid -> {
            if (!launchStudyAnswerAnkiDroid(context, action)) {
                val fallback = studyAnswerAnkiTapAction(
                    noteId = action.noteId,
                    cardId = action.cardId,
                    openAnkiDroidSupported = false,
                )
                if (fallback !== StudyAnswerAnkiTapActionModel.Unavailable) {
                    performStudyAnswerAnkiTapAction(
                        context = context,
                        clipboardManager = clipboardManager,
                        action = fallback,
                        onFeedback = onFeedback,
                    )
                }
            }
        }
        is StudyAnswerAnkiTapActionModel.CopyId -> {
            clipboardManager.setText(AnnotatedString(action.value.toString()))
            onFeedback(action.toastMessage)
        }
        StudyAnswerAnkiTapActionModel.Unavailable -> Unit
    }
}

private fun launchStudyAnswerAnkiDroid(
    context: Context,
    action: StudyAnswerAnkiTapActionModel.OpenAnkiDroid,
): Boolean {
    val uri = studyAnswerAnkiViewUri(action.noteId, action.cardId) ?: return false
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .setPackage(ANKI_DROID_PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(context.packageManager) == null) {
        return false
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}

private fun studyAnswerAnkiViewUri(
    noteId: Long?,
    cardId: Long?,
): Uri? {
    return when {
        noteId != null -> "content://$ANKI_DROID_FLASHCARDS_AUTHORITY/notes/$noteId/cards".toUri()
        cardId != null -> "content://$ANKI_DROID_FLASHCARDS_AUTHORITY/cards/$cardId".toUri()
        else -> null
    }
}

private data class StudyAnswerMetric(
    val label: String,
    val value: String,
    val available: Boolean,
)

private fun detailTextStyle(sizeSp: Int): TextStyle {
    val size = sizeSp.sp
    return TextStyle(
        fontSize = size,
        lineHeight = size * 1.05f,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}

private const val USED_IN_ANKI_LABEL_KEY = "used-in-anki"
