package dev.bee.kanjianki

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.DateTextPolicy
import dev.bee.kanjianki.core.FocusQueueCopy
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.StudyStatsStore

internal fun homeRecentMistakesCardTestTag(kanji: String): String = "home-recent-mistakes-card-$kanji"

private fun homeRecentMistakesCardDescription(model: HomeRecentMistakesCardModel): String {
    return listOfNotNull(
        "Recent mistakes card",
        model.kanji,
        model.title,
        model.subtitle,
        model.sourceEvidence?.takeIf { it.isNotBlank() }
    ).joinToString(", ")
}

internal fun homeRecentMistakesPanelModel(
    home: MainActivityHome,
    mistakes: List<StudyStatsStore.RecentMistake>,
    rowsByKanji: Map<String, RecordsImportModels.DashboardRow>,
): HomeRecentMistakesPanelModel {
    return homeRecentMistakesPanelModel(
        mistakes = mistakes,
        rowsByKanji = rowsByKanji,
        onCardClick = { kanji -> home.renderDetail(kanji, false, "") },
    )
}

internal fun homeRecentMistakesPanelModel(
    mistakes: List<StudyStatsStore.RecentMistake>,
    rowsByKanji: Map<String, RecordsImportModels.DashboardRow>,
    onCardClick: (String) -> Unit,
): HomeRecentMistakesPanelModel {
    val cards = buildList(mistakes.size) {
        mistakes.forEach { mistake ->
            val row = rowsByKanji[mistake.kanji]
            add(
                HomeRecentMistakesCardModel(
                    kanji = mistake.kanji,
                    title = HomeTextCopy.recentMistakeTitle(row?.let { StudyTextCopy.rowMeaning(it) } ?: ""),
                    subtitle = HomeTextCopy.recentMistakeSubtitle(
                        mistake.rating,
                        DateTextPolicy.timelineDate(mistake.reviewedAtMillis)
                    ),
                    sourceEvidence = row?.let { FocusQueueCopy.sourceEvidenceText(it) },
                    accentColor = recentMistakeAccentColor(mistake.rating),
                    onClick = { onCardClick(mistake.kanji) },
                    traceSection = buttonTraceSection("recent-mistake-${mistake.kanji}"),
                )
            )
        }
    }
    return HomeRecentMistakesPanelModel(
        emptyTitle = HomeTextCopy.noRecentMistakesTitle(),
        emptyBody = HomeTextCopy.noRecentMistakesBody(),
        cards = cards
    )
}

@Composable
fun HomeRecentMistakesPanel(model: HomeRecentMistakesPanelModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (model.cards.isEmpty()) {
            HomeEmptyState(
                title = model.emptyTitle,
                body = model.emptyBody,
                style = model.emptyStyle
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                model.cards.forEach { card ->
                    HomeRecentMistakesCard(card)
                }
            }
        }
    }
}

@Composable
private fun HomeRecentMistakesCard(model: HomeRecentMistakesCardModel) {
    val accentColor = kaniColor(model.accentColor)
    val cardFill = accentColor.copy(alpha = 0.06f)
    val cardStroke = accentColor.copy(alpha = 0.58f)
    val tileFill = accentColor.copy(alpha = 0.14f)
    val tileStroke = accentColor.copy(alpha = 0.34f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(homeRecentMistakesCardTestTag(model.kanji))
            .semantics(mergeDescendants = true) {
                contentDescription = homeRecentMistakesCardDescription(model)
            }
            .clickable(
                role = Role.Button,
                onClick = {
                    withUiTrace(model.traceSection) {
                        model.onClick()
                    }
                }
            ),
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
                    .size(70.dp)
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
                Text(text = model.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = model.subtitle, style = MaterialTheme.typography.bodySmall, color = KaniTheme.colors.greyText)
                model.sourceEvidence?.let { evidence ->
                    Text(text = evidence, style = MaterialTheme.typography.bodySmall, color = KaniTheme.colors.ink)
                }
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_forward_24),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun recentMistakeAccentColor(rating: String): Int {
    return when (rating) {
        StudyRatings.AGAIN -> MainActivityUiSupport.CORAL
        StudyRatings.HARD -> MainActivityUiSupport.GOLD
        else -> MainActivityUiSupport.PINK_STROKE
    }
}
