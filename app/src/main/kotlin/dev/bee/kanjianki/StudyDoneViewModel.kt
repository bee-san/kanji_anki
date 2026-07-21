package dev.bee.kanjianki

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dev.bee.kanjianki.core.RecordsSchedulerModels

internal data class StudyDonePresentation(
    val modeLabel: String,
    val title: String,
    val headline: String?,
    val body: String,
    val summaryLines: List<String>,
    val showDoneActions: Boolean,
    val availableStudyMoreNewCards: Int,
    val showBackHome: Boolean,
    val backHomePrimary: Boolean,
) {
    fun toScreenModel(
        studyMoreDialog: StudyMoreNewCardsDialogModel?,
        onStudyMore: Runnable,
        onContinueAll: Runnable,
        onBackHome: Runnable,
    ): StudyDoneScreenModel = StudyDoneScreenModel(
        modeLabel = modeLabel,
        title = title,
        headline = headline,
        body = body,
        summaryLines = summaryLines,
        showDoneActions = showDoneActions,
        availableStudyMoreNewCards = availableStudyMoreNewCards,
        showBackHome = showBackHome,
        backHomePrimary = backHomePrimary,
        onStudyMore = onStudyMore,
        onContinueAll = onContinueAll,
        onBackHome = onBackHome,
        studyMoreDialog = studyMoreDialog,
    )

    companion object {
        fun from(model: StudyDoneScreenModel): StudyDonePresentation = StudyDonePresentation(
            modeLabel = model.modeLabel,
            title = model.title,
            headline = model.headline,
            body = model.body,
            summaryLines = model.summaryLines,
            showDoneActions = model.showDoneActions,
            availableStudyMoreNewCards = model.availableStudyMoreNewCards,
            showBackHome = model.showBackHome,
            backHomePrimary = model.backHomePrimary,
        )
    }
}

/**
 * Saved, callback-free state for the terminal Study route.
 */
internal class StudyDoneViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var renderedPlan: RecordsSchedulerModels.AdaptiveLoadPlan? =
        savedStateHandle.get<Bundle>(KEY_PLAN)?.toAdaptiveLoadPlan()
        private set

    var presentation: StudyDonePresentation? =
        savedStateHandle.get<Bundle>(KEY_PRESENTATION)?.toStudyDonePresentation()
        private set

    var completionReason: StudyRouteCompletionReason? =
        savedStateHandle.get<String>(KEY_COMPLETION_REASON)
            ?.let { value -> StudyRouteCompletionReason.entries.firstOrNull { it.name == value } }
        private set

    var dialogInitialCount: Int? = savedStateHandle[KEY_DIALOG_INITIAL_COUNT]
        private set

    var dialogRequestText: String? = savedStateHandle[KEY_DIALOG_REQUEST_TEXT]
        private set

    var cachedStudyMoreSnapshot: MainActivityStudyDoneActions.StudyMoreNewCardsSnapshot? = null

    var cachedStudyMoreAvailability: Int? = null

    fun install(
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        model: StudyDoneScreenModel,
        reason: StudyRouteCompletionReason,
    ) {
        renderedPlan = plan
        presentation = StudyDonePresentation.from(model)
        completionReason = reason
        if (plan == null) {
            savedStateHandle.remove<Bundle>(KEY_PLAN)
        } else {
            savedStateHandle[KEY_PLAN] = plan.toBundle()
        }
        savedStateHandle[KEY_PRESENTATION] = presentation!!.toBundle()
        savedStateHandle[KEY_COMPLETION_REASON] = reason.name
    }

    fun showDialog(initialCount: Int) {
        dialogInitialCount = initialCount
        dialogRequestText = initialCount.toString()
        savedStateHandle[KEY_DIALOG_INITIAL_COUNT] = initialCount
        savedStateHandle[KEY_DIALOG_REQUEST_TEXT] = dialogRequestText
    }

    fun updateDialogRequestText(requestText: String) {
        if (dialogInitialCount != null) {
            dialogRequestText = requestText
            savedStateHandle[KEY_DIALOG_REQUEST_TEXT] = requestText
        }
    }

    fun hideDialog() {
        dialogInitialCount = null
        dialogRequestText = null
        savedStateHandle.remove<Int>(KEY_DIALOG_INITIAL_COUNT)
        savedStateHandle.remove<String>(KEY_DIALOG_REQUEST_TEXT)
    }

    fun clearStudyMoreCache() {
        cachedStudyMoreSnapshot = null
        cachedStudyMoreAvailability = null
    }

    fun clear() {
        renderedPlan = null
        presentation = null
        completionReason = null
        savedStateHandle.remove<Bundle>(KEY_PLAN)
        savedStateHandle.remove<Bundle>(KEY_PRESENTATION)
        savedStateHandle.remove<String>(KEY_COMPLETION_REASON)
        hideDialog()
        clearStudyMoreCache()
    }

    private companion object {
        const val KEY_PLAN = "study-done-plan"
        const val KEY_PRESENTATION = "study-done-presentation"
        const val KEY_COMPLETION_REASON = "study-done-completion-reason"
        const val KEY_DIALOG_INITIAL_COUNT = "study-done-dialog-initial-count"
        const val KEY_DIALOG_REQUEST_TEXT = "study-done-dialog-request-text"
    }
}

private fun StudyDonePresentation.toBundle(): Bundle = Bundle().apply {
    putString("mode-label", modeLabel)
    putString("title", title)
    putString("headline", headline)
    putString("body", body)
    putStringArrayList("summary-lines", ArrayList(summaryLines))
    putBoolean("show-done-actions", showDoneActions)
    putInt("available-study-more-new-cards", availableStudyMoreNewCards)
    putBoolean("show-back-home", showBackHome)
    putBoolean("back-home-primary", backHomePrimary)
}

private fun Bundle.toStudyDonePresentation(): StudyDonePresentation? = runCatching {
    StudyDonePresentation(
        modeLabel = getString("mode-label") ?: return null,
        title = getString("title") ?: return null,
        headline = getString("headline"),
        body = getString("body") ?: return null,
        summaryLines = getStringArrayList("summary-lines").orEmpty(),
        showDoneActions = getBoolean("show-done-actions"),
        availableStudyMoreNewCards = getInt("available-study-more-new-cards").coerceAtLeast(0),
        showBackHome = getBoolean("show-back-home"),
        backHomePrimary = getBoolean("back-home-primary"),
    )
}.getOrNull()

private fun RecordsSchedulerModels.AdaptiveLoadPlan.toBundle(): Bundle = Bundle().apply {
    putBoolean("auto-mode", autoMode)
    putInt("workload-percent", workloadPercent)
    putInt("target", target)
    putInt("remaining", remaining)
    putStringArrayList("focus-kanji", ArrayList(focusKanji))
    putInt("new-admission-limit", newAdmissionLimit)
    putBoolean("all-kanji-mode", allKanjiMode)
    putString("status", status)
}

private fun Bundle.toAdaptiveLoadPlan(): RecordsSchedulerModels.AdaptiveLoadPlan? = runCatching {
    RecordsSchedulerModels.AdaptiveLoadPlan(
        getBoolean("auto-mode"),
        getInt("workload-percent"),
        getInt("target"),
        getInt("remaining"),
        getStringArrayList("focus-kanji").orEmpty(),
        getInt("new-admission-limit"),
        getBoolean("all-kanji-mode"),
        getString("status"),
    )
}.getOrNull()
