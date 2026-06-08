package dev.bee.kanjianki

import androidx.compose.ui.graphics.Color as ComposeColor

data class HomeRecentMistakesCardModel(
    val kanji: String,
    val title: String,
    val subtitle: String,
    val sourceEvidence: String?,
    val accentColor: ComposeColor,
    val onClick: () -> Unit,
    val traceSection: String = "",
)

data class HomeRecentMistakesPanelModel(
    val emptyTitle: String,
    val emptyBody: String,
    val cards: List<HomeRecentMistakesCardModel>,
    val emptyStyle: HomeEmptyStateStyle = HomeEmptyStateStyle.Panel,
)
