@file:JvmName("MainActivityHomeBrowseDetailCompose")

package dev.bee.kanjianki

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object BrowseMnemonicNoteTestTags {
    const val INPUT = "browse-mnemonic-note-input"
    const val SAVE = "browse-mnemonic-note-save"
}

@Composable
internal fun BrowseDetailScreen(model: BrowseDetailScreenModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        BrowseDetailHero(model.hero)
        BrowseDetailIdentity(model.identity)
        model.strokeOrder?.let { strokeOrder ->
            KaniStrokeOrderDiagram(strokeOrder)
        }
        Box(modifier = Modifier.padding(top = 10.dp)) {
            BrowseDetailInfoPanel(model.reason)
        }
        model.localInventory?.let { localInventory ->
            BrowseDetailInfoPanel(localInventory)
        }
        BrowseMnemonicNoteEditor(model.mnemonicNote)
        BrowseDetailActions(model.actions)
        Box(modifier = Modifier.padding(top = 12.dp)) {
            RecoveryTimelinePanels(model.timeline)
        }
        if (model.examples.isNotEmpty()) {
            Text(
                text = model.examplesTitle,
                modifier = Modifier.padding(top = 12.dp),
                color = BrowseInk,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            model.examples.forEach { example ->
                BrowseExampleCard(example)
            }
        }
    }
}

@Composable
fun BrowseMnemonicNoteEditor(model: BrowseMnemonicNoteModel) {
    var note by remember(model.initialNote) { mutableStateOf(model.initialNote) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        shape = BrowseCardShape,
        color = BrowseWhite,
        border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = model.title,
                color = BrowseInk,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(BrowseMnemonicNoteTestTags.INPUT)
                    .semantics { contentDescription = model.fieldLabel },
                label = { Text(model.fieldLabel) },
                supportingText = { Text(model.helper) },
                minLines = 3,
                maxLines = 8,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = BrowseInk),
            )
            KaniPrimaryButton(
                label = model.saveLabel,
                modifier = Modifier.testTag(BrowseMnemonicNoteTestTags.SAVE),
                minHeightDp = 48,
            ) {
                val normalized = note.trim()
                note = normalized
                model.onSave(normalized)
            }
        }
    }
}

@Composable
fun BrowseDetailMissing(model: BrowseDetailMissingModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeFullWidthHomeButton(
            label = model.homeLabel,
            onClick = {
                withButtonTrace(model.homeLabel) {
                    model.onHome.run()
                }
            }
        )
        Box(modifier = Modifier.padding(vertical = 8.dp)) {
            HomeEmptyState(
                title = model.title,
                body = model.body,
                style = HomeEmptyStateStyle.LegacyBand
            )
        }
    }
}

@Composable
fun BrowseDetailHero(model: BrowseDetailHeroModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeFullWidthHomeButton(
            label = model.navigationLabel,
            onClick = {
                withButtonTrace(model.navigationLabel) {
                    model.onNavigate.run()
                }
            }
        )
        Text(
            text = model.kanji,
            modifier = Modifier.fillMaxWidth(),
            color = BrowseInk,
            fontSize = 92.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = browseNoFontPaddingStyle(92)
        )
    }
}

@Composable
fun BrowseDetailIdentity(model: BrowseDetailIdentityModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        model.stateBadges.forEach { badge ->
            BrowseChip(label = badge.label, color = kaniColor(badge.color))
        }
        Text(
            text = model.title,
            color = BrowseInk,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            style = browseNoFontPaddingStyle(25)
        )
        if (model.reading.isNotEmpty()) {
            Text(
                text = model.reading,
                color = BrowseTeal,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BrowseDetailActions(model: BrowseDetailActionsModel) {
    var copied by remember(model.copyLabel) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        model.reviewLabel?.let { label ->
            Button(
                onClick = {
                    withButtonTrace(label) {
                        model.onReview?.run()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrowseCoral,
                    contentColor = BrowseWhite
                )
            ) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        model.copyLabel?.let { label ->
            OutlinedButton(
                onClick = {
                    copied = true
                    withButtonTrace(label) {
                        model.onCopy?.run()
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = BrowseWhite,
                    contentColor = BrowseInk
                )
            ) {
                Text(
                    text = if (copied) model.copiedLabel else label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        OutlinedButton(
            onClick = {
                withButtonTrace(model.suspendLabel) {
                    model.onSuspend.run()
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, KaniTheme.colors.borderSoft),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = BrowseWhite,
                contentColor = BrowseInk
            )
        ) {
            Text(
                text = model.suspendLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BrowseDetailInfoPanel(model: BrowseDetailPanelModel) {
    val accent = kaniColor(model.color)
    val band = model.style == BrowseDetailPanelStyle.BAND
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (band) 8.dp else 7.dp),
        shape = BrowseCardShape,
        color = if (band) accent else BrowseWhite,
        border = BorderStroke(1.dp, accent)
    ) {
        Column(
            modifier = Modifier.padding(if (band) 20.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (model.title.isNotBlank()) {
                Text(
                    text = model.title,
                    color = if (band) BrowseWhite else BrowseInk,
                    fontSize = if (band) 22.sp else 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            model.lines.forEach { line ->
                Text(
                    text = line,
                    color = if (band) BrowseWhite else BrowseMuted,
                    fontSize = if (band) 17.sp else 15.sp
                )
            }
        }
    }
}

@Composable
private fun BrowseChip(label: String, color: ComposeColor) {
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

internal object StrokeOrderTestTags {
    const val SECTION = "stroke-order-section"
    const val PANEL_PREFIX = "stroke-order-panel-"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KaniStrokeOrderDiagram(model: BrowseStrokeOrderModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .testTag(StrokeOrderTestTags.SECTION),
    ) {
        Text(
            text = model.title,
            color = BrowseInk,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            model.panels.forEach { panel ->
                StrokeOrderPanelCell(panel)
            }
        }
        model.overflowText?.let { text ->
            Text(
                text = text,
                color = BrowseMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun StrokeOrderPanelCell(panel: BrowseStrokeOrderPanelModel) {
    val accent = KaniTheme.colors.teal
    val dimmedColor = BrowseInk.copy(alpha = 0.2f)
    val dotRadius = 4f
    Surface(
        modifier = Modifier
            .width(56.dp)
            .aspectRatio(1f)
            .testTag(StrokeOrderTestTags.PANEL_PREFIX + panel.strokeNumber),
        shape = RoundedCornerShape(6.dp),
        color = BrowseWhite,
        border = BorderStroke(0.5.dp, KaniTheme.colors.borderSoft),
    ) {
        Box(contentAlignment = Alignment.BottomStart) {
            Canvas(modifier = Modifier.matchParentSize().padding(4.dp)) {
                val w = size.width
                val h = size.height
                panel.strokes.forEach { stroke ->
                    val color = if (stroke.highlighted) accent else dimmedColor
                    val strokeWidth = if (stroke.highlighted) 3f else 1.5f
                    val points = stroke.points
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = color,
                            start = Offset(points[i].first * w, points[i].second * h),
                            end = Offset(points[i + 1].first * w, points[i + 1].second * h),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                if (panel.startPointX != null && panel.startPointY != null) {
                    drawCircle(
                        color = accent,
                        radius = dotRadius,
                        center = Offset(panel.startPointX * w, panel.startPointY * h),
                    )
                }
            }
            Text(
                text = panel.strokeNumber.toString(),
                modifier = Modifier.padding(start = 3.dp, bottom = 1.dp),
                color = BrowseMuted,
                fontSize = 9.sp,
            )
        }
    }
}
