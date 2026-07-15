package dev.bee.kanjianki

data class HomeScreenModel(
    val title: String,
    val subtitle: String,
    val metrics: List<HomeMetricModel>,
    val todayPlan: HomeTodayPlanModel,
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
    /** Cards remaining in the current/next focus study session. */
    val studyRemainingCount: Int = 0,
    val repairedHandoff: HomeRepairedHandoffCardModel? = null,
    /** Preloaded off the main thread; route rendering must not read update settings. */
    val updatePermissionPrompt: HomeUpdatePermissionPromptSnapshot? = null,
    val updateCheckFailedLine: String? = null,
    val onRetryUpdateCheck: (() -> Unit)? = null,
)
