package dev.bee.kanjianki

data class HomeFocusQueueCardModel(
    val kanji: String,
    val meaning: String,
    val sourceEvidence: String,
    val reasonLine: String,
    val body: String,
    val tags: List<HomeFocusQueueTagModel>,
    /** Legacy palette ARGB; resolve with [kaniColor] at render time. */
    val accentColor: Int,
    val onClick: () -> Unit,
)

data class HomeFocusQueueTagModel(
    val label: String,
    /** Legacy palette ARGB; resolve with [kaniColor] at render time. */
    val color: Int,
)

data class HomeFocusQueuePanelModel(
    val planText: String,
    val emptyTitle: String?,
    val emptyBody: String?,
    val showSyncButton: Boolean,
    val cards: List<HomeFocusQueueCardModel>,
)
