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
)
