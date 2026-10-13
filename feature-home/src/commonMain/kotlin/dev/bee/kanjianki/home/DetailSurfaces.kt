package dev.bee.kanjianki.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.CopySearchButton
import dev.bee.kanjianki.presentation.DetailAccent
import dev.bee.kanjianki.presentation.DetailBadge
import dev.bee.kanjianki.presentation.DetailIdentity
import dev.bee.kanjianki.presentation.DetailMissing
import dev.bee.kanjianki.presentation.DetailPanel
import dev.bee.kanjianki.presentation.ExampleCard
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniActionButton
import dev.bee.kanjianki.presentation.KanjiDetail
import dev.bee.kanjianki.presentation.MnemonicEditor
import dev.bee.kanjianki.presentation.NeighborPanel
import dev.bee.kanjianki.presentation.NeighborRow
import dev.bee.kanjianki.presentation.RecoveryTimeline
import dev.bee.kanjianki.presentation.StrokeOrderDiagram
import dev.bee.kanjianki.presentation.StrokePanel
import dev.bee.kanjianki.presentation.TimelineEvent
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.presentation.UiTextResolver
import dev.bee.kanjianki.ui.KaniColors
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val DETAIL_TEST_TAG: String = "kani-detail"
const val DETAIL_MISSING_TEST_TAG: String = "kani-detail-missing"
const val DETAIL_IDENTITY_TEST_TAG: String = "kani-detail-identity"
const val DETAIL_REVIEW_TEST_TAG: String = "kani-detail-review"
const val DETAIL_COPY_SEARCH_TEST_TAG: String = "kani-detail-copy-search"
const val DETAIL_SUSPEND_TEST_TAG: String = "kani-detail-suspend"
const val DETAIL_MNEMONIC_INPUT_TEST_TAG: String = "kani-detail-mnemonic-input"
const val DETAIL_MNEMONIC_SAVE_TEST_TAG: String = "kani-detail-mnemonic-save"
const val DETAIL_STROKE_ORDER_TEST_TAG: String = "kani-detail-stroke-order"
const val DETAIL_NEIGHBORS_TEST_TAG: String = "kani-detail-neighbors"
const val DETAIL_TIMELINE_TEST_TAG: String = "kani-detail-timeline"

/** One stroke-order cell, tagged by the stroke it completes. */
fun detailStrokePanelTestTag(strokeNumber: Int): String = "kani-detail-stroke-$strokeNumber"

/** One neighbour row, tagged by the kanji it opens. */
fun detailNeighborTestTag(kanji: String): String = "kani-detail-neighbor-$kanji"

/**
 * One kanji's detail, from [KanjiDetail] alone.
 *
 * The same surfaces the Android host rendered as `BrowseDetailScreen`, in the same
 * order, from the shared model rather than an `:app`-internal one. The stroke-order
 * diagram, the neighbours panel, and the local-inventory panel are optional on the
 * model and skipped when absent — a kanji with no KanjiVG guide (every kanji on
 * desktop until the Goal 183 assets land) shows the rest of the card rather than a
 * gap.
 *
 * A [KanjiDetail.missing] wins over everything: a kanji with no local record is a
 * short "not found" card, not an empty detail with blank panels.
 */
@Composable
fun KanjiDetailScreen(
    detail: KanjiDetail,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    detail.missing?.let { missing ->
        DetailMissingCard(missing, resolver, modifier)
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(DETAIL_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailHero(detail.kanji)
        DetailIdentityBlock(detail.identity, resolver)
        detail.strokeOrder?.let { StrokeOrderDiagramView(it, resolver) }
        DetailInfoPanel(detail.reason, resolver)
        detail.neighbors?.let { NeighborPanelView(it, resolver, dispatch) }
        detail.localInventory?.let { DetailInfoPanel(it, resolver) }
        MnemonicEditorView(detail.mnemonic, resolver, dispatch)
        DetailActionRow(detail, resolver, dispatch)
        RecoveryTimelineView(detail.timeline, resolver)
        if (detail.examples.isNotEmpty()) {
            for (example in detail.examples) {
                ExampleCardView(example, resolver)
            }
        }
    }
}

@Composable
private fun DetailHero(kanji: String) {
    Text(
        text = kanji,
        modifier = Modifier.fillMaxWidth(),
        color = KaniTheme.colors.ink,
        fontSize = HERO_TEXT_SIZE,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DetailIdentityBlock(identity: DetailIdentity, resolver: UiTextResolver) {
    val title = resolver.resolve(identity.title)
    val reading = resolver.resolve(identity.reading)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DETAIL_IDENTITY_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (badge in identity.badges) {
            DetailChip(badge, resolver)
        }
        Text(
            text = title,
            color = KaniTheme.colors.ink,
            fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
        )
        if (reading.isNotBlank()) {
            Text(
                text = reading,
                color = KaniTheme.colors.teal,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DetailChip(badge: DetailBadge, resolver: UiTextResolver) {
    val label = resolver.resolve(badge.label)
    if (label.isBlank()) return
    val accent = badge.accent.color(KaniTheme.colors)
    Surface(
        shape = KaniUiTokens.PillShape,
        color = accent.copy(alpha = CHIP_FILL_ALPHA),
        border = BorderStroke(1.dp, accent),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = accent,
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * An info panel: a filled band for the reason, a bordered card for local inventory.
 *
 * [DetailPanel.emphasized] is what Android's `BrowseDetailPanelStyle.BAND` vs `CARD`
 * chose between — a band fills with the accent and reads as a headline, a card keeps
 * a white fill with an accent border and reads as a footnote.
 */
@Composable
private fun DetailInfoPanel(panel: DetailPanel, resolver: UiTextResolver) {
    val title = resolver.resolve(panel.title)
    val accent = panel.accent.color(KaniTheme.colors)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = KaniUiTokens.LeafShape,
        color = if (panel.emphasized) accent else KaniTheme.colors.panel,
        border = BorderStroke(1.dp, accent),
    ) {
        Column(
            modifier = Modifier.padding(if (panel.emphasized) 20.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = if (panel.emphasized) KaniUiTokens.readableTextColor(accent) else KaniTheme.colors.ink,
                    fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            for (line in panel.lines.map(resolver::resolve).filter { it.isNotBlank() }) {
                Text(
                    text = line,
                    color = if (panel.emphasized) KaniUiTokens.readableTextColor(accent) else KaniTheme.colors.muted,
                    fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                )
            }
        }
    }
}

/**
 * The mnemonic editor.
 *
 * The typed note is local state seeded from [MnemonicEditor.initial], and only the
 * Save button commits it — the "no actions while editing text" rule Browse's search
 * box follows. Save trims first, so a note that is only whitespace is a clear.
 */
@Composable
private fun MnemonicEditorView(
    editor: MnemonicEditor,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
) {
    var note by rememberSaveable(editor.initial) { mutableStateOf(editor.initial) }
    val fieldLabel = resolver.resolve(editor.fieldLabel)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panel,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = resolver.resolve(editor.title),
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DETAIL_MNEMONIC_INPUT_TEST_TAG)
                    .semantics { contentDescription = fieldLabel },
                label = { Text(fieldLabel) },
                supportingText = { Text(resolver.resolve(editor.helper)) },
                minLines = 3,
                maxLines = 8,
            )
            Button(
                onClick = {
                    val trimmed = note.trim()
                    note = trimmed
                    dispatch(editor.saveAction(trimmed))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ACTION_MIN_HEIGHT)
                    .testTag(DETAIL_MNEMONIC_SAVE_TEST_TAG),
                shape = KaniUiTokens.ButtonShape,
            ) {
                Text(text = resolver.resolve(editor.saveLabel))
            }
        }
    }
}

/**
 * The action row: review, copy-search, and suspend, each conditional but suspend.
 *
 * The copy button flips to its confirmation label after a tap. The write it does is a
 * [KaniAction.RequestCopy] the shell turns into a clipboard effect with the same
 * confirmation, so the toast and the write stay paired.
 */
@Composable
private fun DetailActionRow(
    detail: KanjiDetail,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        detail.actions.review?.let { review ->
            DetailPrimaryButton(review, DETAIL_REVIEW_TEST_TAG, resolver, dispatch)
        }
        detail.actions.copySearch?.let { copy ->
            CopySearchButtonView(copy, resolver, dispatch)
        }
        DetailOutlinedButton(detail.actions.suspend, DETAIL_SUSPEND_TEST_TAG, resolver, dispatch)
    }
}

@Composable
private fun DetailPrimaryButton(
    button: KaniActionButton,
    testTag: String,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
) {
    Button(
        onClick = { dispatch(button.action) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ACTION_MIN_HEIGHT)
            .testTag(testTag),
        shape = KaniUiTokens.ButtonShape,
    ) {
        Text(
            text = resolver.resolve(button.label),
            fontSize = KaniUiTokens.StudyActionTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DetailOutlinedButton(
    button: KaniActionButton,
    testTag: String,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
) {
    OutlinedButton(
        onClick = { dispatch(button.action) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ACTION_MIN_HEIGHT)
            .testTag(testTag),
        shape = KaniUiTokens.LeafShape,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
    ) {
        Text(
            text = resolver.resolve(button.label),
            fontSize = KaniUiTokens.StudyActionTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CopySearchButtonView(
    button: CopySearchButton,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
) {
    var copied by remember(button.search) { mutableStateOf(false) }
    OutlinedButton(
        onClick = {
            copied = true
            dispatch(button.action)
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ACTION_MIN_HEIGHT)
            .testTag(DETAIL_COPY_SEARCH_TEST_TAG),
        shape = KaniUiTokens.LeafShape,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
    ) {
        Text(
            text = resolver.resolve(if (copied) button.copiedLabel else button.label),
            fontSize = KaniUiTokens.StudyActionTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrokeOrderDiagramView(diagram: StrokeOrderDiagram, resolver: UiTextResolver) {
    val overflow = resolver.resolve(diagram.overflow)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DETAIL_STROKE_ORDER_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = resolver.resolve(diagram.title),
            modifier = Modifier.semantics { heading() },
            color = KaniTheme.colors.ink,
            fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (panel in diagram.panels) {
                StrokePanelCell(panel)
            }
        }
        if (overflow.isNotBlank()) {
            Text(
                text = overflow,
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
        }
    }
}

@Composable
private fun StrokePanelCell(panel: StrokePanel) {
    val accent = KaniTheme.colors.teal
    val dimmed = KaniTheme.colors.ink.copy(alpha = DIMMED_STROKE_ALPHA)
    Surface(
        modifier = Modifier
            .width(STROKE_CELL_SIZE)
            .aspectRatio(1f)
            .testTag(detailStrokePanelTestTag(panel.strokeNumber)),
        shape = KaniUiTokens.StudyShapeSmall,
        color = KaniTheme.colors.panel,
        border = BorderStroke(0.5.dp, KaniTheme.colors.borderSoft),
    ) {
        Box(contentAlignment = Alignment.BottomStart) {
            Canvas(modifier = Modifier.matchParentSize().padding(4.dp)) {
                val w = size.width
                val h = size.height
                for (stroke in panel.strokes) {
                    val color = if (stroke.highlighted) accent else dimmed
                    val strokeWidth = if (stroke.highlighted) HIGHLIGHT_STROKE_WIDTH else DIM_STROKE_WIDTH
                    val points = stroke.points
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = color,
                            start = Offset(points[i].x * w, points[i].y * h),
                            end = Offset(points[i + 1].x * w, points[i + 1].y * h),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                val startX = panel.startX
                val startY = panel.startY
                if (startX != null && startY != null) {
                    drawCircle(color = accent, radius = START_DOT_RADIUS, center = Offset(startX * w, startY * h))
                }
            }
            Text(
                text = panel.strokeNumber.toString(),
                modifier = Modifier.padding(start = 3.dp, bottom = 1.dp),
                color = KaniTheme.colors.muted,
                fontSize = STROKE_NUMBER_TEXT_SIZE,
            )
        }
    }
}

@Composable
private fun NeighborPanelView(
    panel: NeighborPanel,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DETAIL_NEIGHBORS_TEST_TAG),
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panel,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = resolver.resolve(panel.title),
                modifier = Modifier.semantics { heading() },
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
                fontWeight = FontWeight.Bold,
            )
            for (row in panel.rows) {
                NeighborRowView(row, resolver, dispatch)
            }
        }
    }
}

@Composable
private fun NeighborRowView(
    row: NeighborRow,
    resolver: UiTextResolver,
    dispatch: (KaniAction) -> Unit,
) {
    val meaning = resolver.resolve(row.meaning)
    val evidence = resolver.resolve(row.evidence)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(detailNeighborTestTag(row.kanji)),
        shape = KaniUiTokens.StudyShapeSmall,
        color = KaniTheme.colors.panelSoft,
        onClick = { dispatch(row.action) },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = row.kanji,
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyWordHeroTextSizeSp.sp,
                fontWeight = FontWeight.Bold,
            )
            if (meaning.isNotBlank()) {
                Text(text = meaning, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
            }
            if (evidence.isNotBlank()) {
                Text(
                    text = evidence,
                    modifier = Modifier.padding(top = 2.dp),
                    color = KaniTheme.colors.coral,
                    fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
                )
            }
        }
    }
}

@Composable
private fun RecoveryTimelineView(timeline: RecoveryTimeline, resolver: UiTextResolver) {
    val empty = resolver.resolve(timeline.empty)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DETAIL_TIMELINE_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = resolver.resolve(timeline.title),
            modifier = Modifier.semantics { heading() },
            color = KaniTheme.colors.ink,
            fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = KaniUiTokens.LeafShape,
            color = KaniTheme.colors.panel,
            border = BorderStroke(1.dp, timeline.statusAccent.color(KaniTheme.colors)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = resolver.resolve(timeline.status),
                    color = KaniTheme.colors.ink,
                    fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = resolver.resolve(timeline.support),
                    color = KaniTheme.colors.muted,
                    fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
                )
            }
        }
        if (empty.isNotBlank()) {
            Text(text = empty, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
        }
        for (event in timeline.events) {
            TimelineEventView(event, resolver)
        }
    }
}

@Composable
private fun TimelineEventView(event: TimelineEvent, resolver: UiTextResolver) {
    val date = resolver.resolve(event.date)
    val title = resolver.resolve(event.title)
    val detail = resolver.resolve(event.detail)
    val source = resolver.resolve(event.source)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panel,
        border = BorderStroke(1.dp, event.accent.color(KaniTheme.colors)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (date.isNotBlank()) {
                Text(text = date, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
            }
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = KaniTheme.colors.ink,
                    fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (detail.isNotBlank()) {
                Text(text = detail, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp)
            }
            if (source.isNotBlank()) {
                Text(
                    text = source,
                    color = KaniTheme.colors.ink,
                    fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ExampleCardView(example: ExampleCard, resolver: UiTextResolver) {
    val accent = example.accent.color(KaniTheme.colors)
    val sentence = resolver.resolve(example.sentence)
    val meaning = resolver.resolve(example.meaning)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panel,
        border = BorderStroke(1.dp, accent),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DetailExampleChip(resolver.resolve(example.source), accent)
            Text(
                text = resolver.resolve(example.expression),
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyHeadingTextSizeSp.sp,
                fontWeight = FontWeight.Bold,
            )
            if (sentence.isNotBlank()) {
                Text(text = sentence, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp)
            }
            if (meaning.isNotBlank()) {
                Text(text = meaning, color = KaniTheme.colors.muted, fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp)
            }
        }
    }
}

@Composable
private fun DetailExampleChip(label: String, accent: Color) {
    if (label.isBlank()) return
    Surface(
        shape = KaniUiTokens.PillShape,
        color = accent.copy(alpha = CHIP_FILL_ALPHA),
        border = BorderStroke(1.dp, accent),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = accent,
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DetailMissingCard(
    missing: DetailMissing,
    resolver: UiTextResolver,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(DETAIL_MISSING_TEST_TAG),
        shape = KaniUiTokens.LeafShape,
        color = KaniTheme.colors.panelSoft,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = resolver.resolve(missing.title),
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = resolver.resolve(missing.body),
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
        }
    }
}

/**
 * The live theme colour for a detail accent.
 *
 * Here rather than on the model for the reason [HomeAccent.color] is: the model must
 * not know which palette is active. Info is the theme's blue, positive its teal,
 * warning its coral — the three hues Android's detail panels used from a fixed
 * palette, now chosen by the active theme.
 */
internal fun DetailAccent.color(colors: KaniColors): Color = when (this) {
    DetailAccent.INFO -> colors.blue
    DetailAccent.POSITIVE -> colors.teal
    DetailAccent.WARNING -> colors.coral
}

private val HERO_TEXT_SIZE = 92.sp
private val STROKE_CELL_SIZE = 56.dp
private val STROKE_NUMBER_TEXT_SIZE = 9.sp
private const val CHIP_FILL_ALPHA = 0.16f
private const val DIMMED_STROKE_ALPHA = 0.2f
private const val HIGHLIGHT_STROKE_WIDTH = 3f
private const val DIM_STROKE_WIDTH = 1.5f
private const val START_DOT_RADIUS = 4f
private val ACTION_MIN_HEIGHT = 54.dp
