package dev.bee.kanjianki

import androidx.compose.ui.graphics.Color as ComposeColor

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
