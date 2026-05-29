package dev.bee.kanjianki

data class StudyDoneScreenModel(
    val modeLabel: String,
    val title: String,
    val headline: String?,
    val body: String,
    val summaryLines: List<String>,
    val showDoneActions: Boolean,
    val availableStudyMoreNewCards: Int,
    val showBackHome: Boolean,
    val backHomePrimary: Boolean,
    val onStudyMore: Runnable,
    val onContinueAll: Runnable,
    val onBackHome: Runnable,
    val studyMoreDialog: StudyMoreNewCardsDialogModel? = null,
)

data class StudyMoreNewCardsDialogModel(
    val title: String,
    val message: String,
    val inputLabel: String,
    val initialCount: Int,
    val confirmLabel: String,
    val cancelLabel: String,
    val onConfirm: (String) -> Boolean,
    val onDismiss: Runnable,
)
