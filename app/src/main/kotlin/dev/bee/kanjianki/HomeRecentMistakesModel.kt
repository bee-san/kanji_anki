package dev.bee.kanjianki

data class HomeRecentMistakesCardModel(
    val kanji: String,
    val title: String,
    val subtitle: String,
    val sourceEvidence: String?,
    /** Legacy palette ARGB; resolve with [kaniColor] at render time. */
    val accentColor: Int,
    val onClick: () -> Unit,
    val traceSection: String = "",
)

data class HomeRecentMistakesPanelModel(
    val emptyTitle: String,
    val emptyBody: String,
    val cards: List<HomeRecentMistakesCardModel>,
    val emptyStyle: HomeEmptyStateStyle = HomeEmptyStateStyle.Panel,
)
