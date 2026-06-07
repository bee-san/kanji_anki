package dev.bee.kanjianki

data class HomeScreenModel(
    val title: String,
    val subtitle: String,
    val metrics: List<HomeMetricModel>,
    val deckOverviewRows: List<String>,
    val showSyncCta: Boolean,
    val syncLabel: String,
    val studyLabel: String,
    val onSync: () -> Unit,
    val onStudy: () -> Unit,
    val actions: List<HomeActionModel>,
    val focusTitle: String,
    val focusActionLabel: String?,
    val onFocusAction: (() -> Unit)?,
    val emptyTitle: String?,
    val emptyBody: String?,
    val previewCards: List<HomeFocusQueueCardModel>,
)
