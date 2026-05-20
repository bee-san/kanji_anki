@file:JvmName("MainActivityHomeFocusQueueCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.AdaptiveFocusCopy
import dev.bee.kanjianki.core.FocusQueueCopy
import dev.bee.kanjianki.core.FocusQueuePolicy
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyTextCopy

data class HomeFocusQueueCardModel(
    val kanji: String,
    val meaning: String,
    val sourceEvidence: String,
    val reasonLine: String,
    val body: String,
    val tags: List<HomeFocusQueueTagModel>,
    val accentColor: ComposeColor,
    val onClick: () -> Unit,
)

data class HomeFocusQueueTagModel(
    val label: String,
    val color: ComposeColor,
)

data class HomeFocusQueuePanelModel(
    val planText: String,
    val emptyTitle: String?,
    val emptyBody: String?,
    val showSyncButton: Boolean,
    val cards: List<HomeFocusQueueCardModel>,
)

internal fun homeFocusQueueCardTestTag(kanji: String): String = "home-focus-queue-card-$kanji"

internal fun homeFocusQueueContentView(
    home: MainActivityHome,
    rows: List<RecordsImportModels.DashboardRow>,
    entries: List<MainActivityBase.QueueEntry>,
    nowMillis: Long,
    plan: RecordsSchedulerModels.AdaptiveLoadPlan?
): View {
    val model = homeFocusQueuePanelModel(home, rows, entries, nowMillis, plan)
    return ComposeView(home).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                Surface {
                    HomeFocusQueuePanel(model = model, onSync = home::confirmSync)
                }
            }
        }
    }
}

internal fun homeFocusQueueCardView(
    home: MainActivityHome,
    entry: MainActivityBase.QueueEntry,
    nowMillis: Long,
): View {
    val model = homeFocusQueueCardModel(home, entry, nowMillis)
    return ComposeView(home).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, home.dp(7), 0, home.dp(7))
        }
        setContent {
            MaterialTheme {
                Surface {
                    HomeFocusQueueCard(model)
                }
            }
        }
    }
}

internal fun homeFocusQueuePanelModel(
    home: MainActivityHome,
    rows: List<RecordsImportModels.DashboardRow>,
    entries: List<MainActivityBase.QueueEntry>,
    nowMillis: Long,
    plan: RecordsSchedulerModels.AdaptiveLoadPlan?
): HomeFocusQueuePanelModel {
    val cards = entries.map { homeFocusQueueCardModel(home, it, nowMillis) }
    return HomeFocusQueuePanelModel(
        planText = AdaptiveFocusCopy.adaptiveFocusText(plan),
        emptyTitle = if (rows.isEmpty()) HomeTextCopy.noKanjiQueuedTitle() else MainActivityBase.EMPTY_ACTIVE_PRACTICE_TITLE,
        emptyBody = if (rows.isEmpty()) HomeTextCopy.focusQueueNoKanjiQueuedBody() else MainActivityBase.EMPTY_ACTIVE_PRACTICE_BODY,
        showSyncButton = rows.isEmpty(),
        cards = cards
    )
}

internal fun homeFocusQueueCardModel(
    home: MainActivityHome,
    entry: MainActivityBase.QueueEntry,
    nowMillis: Long,
): HomeFocusQueueCardModel {
    val row = entry.row
    val item = entry.item
    return HomeFocusQueueCardModel(
        kanji = row.kanji,
        meaning = StudyTextCopy.rowMeaning(row),
        sourceEvidence = FocusQueueCopy.sourceEvidenceText(row),
        reasonLine = FocusQueueCopy.focusReasonLine(row, item, nowMillis, home.settings().matureSupportThreshold),
        body = StudyTextCopy.compact(FocusQueueCopy.queueCardBody(row), 72),
        tags = buildList {
            add(HomeFocusQueueTagModel(FocusQueueCopy.recognitionStageLabel(item), ComposeColor(MainActivityUiSupport.BLUE)))
            if (item.phase == RecordsBase.SchedulerPhase.RELEARNING) {
                add(HomeFocusQueueTagModel(HomeTextCopy.relearningChipLabel(), ComposeColor(MainActivityUiSupport.CORAL)))
            } else if (item.phase == RecordsBase.SchedulerPhase.NEW_LEARNING && item.totalReviews > 0) {
                add(HomeFocusQueueTagModel(MainActivityBase.STATE_LEARNING, ComposeColor(MainActivityUiSupport.TEAL)))
            }
        },
        accentColor = queueAccentColor(item, nowMillis),
        onClick = { home.renderDetail(row.kanji) }
    )
}

@Composable
fun HomeFocusQueuePanel(model: HomeFocusQueuePanelModel, onSync: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = model.planText,
            style = MaterialTheme.typography.bodyMedium,
            color = ComposeColor(0xFF6E6E78)
        )
        if (model.cards.isEmpty()) {
            HomeEmptyState(
                title = requireNotNull(model.emptyTitle),
                body = requireNotNull(model.emptyBody)
            )
            if (model.showSyncButton) {
                Button(
                    onClick = onSync,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeColor(0xFFFF7F9D),
                        contentColor = ComposeColor.White
                    )
                ) {
                    Text(text = HomeTextCopy.syncAnkiDroidLabel())
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                model.cards.forEach { card ->
                    HomeFocusQueueCard(card)
                }
            }
        }
    }
}

@Composable
internal fun HomeFocusQueueCard(model: HomeFocusQueueCardModel) {
    val cardFill = model.accentColor.copy(alpha = 0.06f)
    val cardStroke = model.accentColor.copy(alpha = 0.58f)
    val tileFill = model.accentColor.copy(alpha = 0.14f)
    val tileStroke = model.accentColor.copy(alpha = 0.34f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(homeFocusQueueCardTestTag(model.kanji))
            .clickable(onClick = model.onClick),
        shape = RoundedCornerShape(18.dp),
        color = cardFill,
        border = BorderStroke(1.dp, cardStroke)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(16.dp)),
                color = tileFill,
                border = BorderStroke(1.dp, tileStroke)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = model.kanji,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = model.meaning, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = model.sourceEvidence, style = MaterialTheme.typography.bodySmall, color = ComposeColor(0xFF3D3D48))
                Text(text = model.reasonLine, style = MaterialTheme.typography.bodySmall, color = model.accentColor)
                Text(text = model.body, style = MaterialTheme.typography.bodyMedium, color = ComposeColor(0xFF6E6E78))
                Row {
                    model.tags.forEach { tag ->
                        FocusQueueTag(tag)
                    }
                }
            }
            Text(
                text = ">",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = model.accentColor
            )
        }
    }
}

@Composable
private fun FocusQueueTag(tag: HomeFocusQueueTagModel) {
    Surface(
        modifier = Modifier.padding(top = 7.dp, end = 7.dp, bottom = 2.dp),
        shape = RoundedCornerShape(7.dp),
        color = focusQueueTagFill(tag.color),
        border = BorderStroke(1.dp, tag.color)
    ) {
        Text(
            text = tag.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = tag.color,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun focusQueueTagFill(color: ComposeColor): ComposeColor {
    return when (color) {
        ComposeColor(MainActivityUiSupport.CORAL) -> ComposeColor(0xFFFFEBF3)
        ComposeColor(MainActivityUiSupport.TEAL) -> ComposeColor(0xFFE6FAFB)
        ComposeColor(MainActivityUiSupport.BLUE) -> ComposeColor(0xFFF2EEFF)
        else -> color.copy(alpha = 0.08f)
    }
}

private fun queueAccentColor(item: RecordsStudyModels.StudyItem, nowMillis: Long): ComposeColor {
    return when (FocusQueuePolicy.rowTone(item, nowMillis)) {
        FocusQueuePolicy.QueueTone.DUE -> ComposeColor(0xFFFF4C76)
        FocusQueuePolicy.QueueTone.LEARNING -> ComposeColor(0xFF6E5CE6)
        FocusQueuePolicy.QueueTone.RESTING -> ComposeColor(0xFFF6CAE1)
    }
}
