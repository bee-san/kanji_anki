package dev.bee.kanjianki.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.bee.kanjianki.feature.home.generated.resources.Res
import dev.bee.kanjianki.feature.home.generated.resources.browse_deselect_all
import dev.bee.kanjianki.feature.home.generated.resources.browse_empty_body
import dev.bee.kanjianki.feature.home.generated.resources.browse_empty_title
import dev.bee.kanjianki.feature.home.generated.resources.browse_result_count
import dev.bee.kanjianki.feature.home.generated.resources.browse_result_none
import dev.bee.kanjianki.feature.home.generated.resources.browse_result_truncated
import dev.bee.kanjianki.feature.home.generated.resources.browse_row_description
import dev.bee.kanjianki.feature.home.generated.resources.browse_row_not_selected
import dev.bee.kanjianki.feature.home.generated.resources.browse_row_selected
import dev.bee.kanjianki.feature.home.generated.resources.browse_search_action
import dev.bee.kanjianki.feature.home.generated.resources.browse_search_hint
import dev.bee.kanjianki.feature.home.generated.resources.browse_select_all
import dev.bee.kanjianki.feature.home.generated.resources.browse_selection_all
import dev.bee.kanjianki.feature.home.generated.resources.browse_selection_none
import dev.bee.kanjianki.feature.home.generated.resources.browse_selection_partial
import dev.bee.kanjianki.feature.home.generated.resources.browse_show_suspended
import dev.bee.kanjianki.feature.home.generated.resources.browse_similar_filter
import dev.bee.kanjianki.feature.home.generated.resources.browse_studied_toggle
import dev.bee.kanjianki.feature.home.generated.resources.browse_suspended_chip
import dev.bee.kanjianki.feature.home.generated.resources.browse_title
import dev.bee.kanjianki.feature.home.generated.resources.focus_queue_card_description
import dev.bee.kanjianki.feature.home.generated.resources.focus_queue_nothing_active_body
import dev.bee.kanjianki.feature.home.generated.resources.focus_queue_nothing_active_title
import dev.bee.kanjianki.feature.home.generated.resources.focus_queue_nothing_imported_body
import dev.bee.kanjianki.feature.home.generated.resources.focus_queue_nothing_imported_title
import dev.bee.kanjianki.feature.home.generated.resources.focus_queue_title
import dev.bee.kanjianki.feature.home.generated.resources.focus_queue_view_all
import dev.bee.kanjianki.feature.home.generated.resources.home_deck_overview_title
import dev.bee.kanjianki.feature.home.generated.resources.home_metric_card_description
import dev.bee.kanjianki.feature.home.generated.resources.home_metric_focus
import dev.bee.kanjianki.feature.home.generated.resources.home_metric_streak
import dev.bee.kanjianki.feature.home.generated.resources.home_metric_sync
import dev.bee.kanjianki.feature.home.generated.resources.home_study_action
import dev.bee.kanjianki.feature.home.generated.resources.home_study_remaining
import dev.bee.kanjianki.feature.home.generated.resources.home_sync_action
import dev.bee.kanjianki.feature.home.generated.resources.home_title
import dev.bee.kanjianki.feature.home.generated.resources.home_today_title
import dev.bee.kanjianki.feature.home.generated.resources.notice_reduced_fsrs_body
import dev.bee.kanjianki.feature.home.generated.resources.notice_reduced_fsrs_title
import dev.bee.kanjianki.presentation.BrowseResults
import dev.bee.kanjianki.presentation.BrowseRow
import dev.bee.kanjianki.presentation.FocusEmptyReason
import dev.bee.kanjianki.presentation.HomeMetricKind
import dev.bee.kanjianki.presentation.HomeNotice
import dev.bee.kanjianki.presentation.UiTextResolver
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Home dashboard and focus queue wording.
 *
 * Separate from [HomeCopy] rather than added to it, because [HomeCopy] is already the
 * size at which a data class stops being readable and because the two are needed in
 * different places: onboarding and note-type configuration are a first-run surface,
 * and this is what Home shows once that is done. A screen needing both takes both,
 * which is honest about the fact that it is rendering two things.
 */
@Suppress("LongParameterList")
data class DashboardCopy(
    val title: String,
    val syncAction: String,
    val studyAction: String,
    val todayTitle: String,
    val deckOverviewTitle: String,
    val metricCardDescription: String,
    val focusQueueTitle: String,
    val focusQueueViewAll: String,
    private val metricLabels: Map<HomeMetricKind, String>,
    private val emptyTitles: Map<FocusEmptyReason, String>,
    private val emptyBodies: Map<FocusEmptyReason, String>,
    private val noticeTitles: Map<HomeNotice, String>,
    private val noticeBodies: Map<HomeNotice, String>,
    private val focusCardDescriptionTemplate: String,
) {
    fun metricLabel(kind: HomeMetricKind): String = metricLabels.getValue(kind)

    fun emptyTitle(reason: FocusEmptyReason): String = emptyTitles.getValue(reason)

    fun emptyBody(reason: FocusEmptyReason): String = emptyBodies.getValue(reason)

    fun noticeTitle(notice: HomeNotice): String = noticeTitles.getValue(notice)

    fun noticeBody(notice: HomeNotice): String = noticeBodies.getValue(notice)

    /**
     * What a screen reader says for a focus card.
     *
     * Names the kanji and its meaning rather than reading the whole card, because the
     * badges and the reason line repeat what the meaning already established and a
     * card announced in six fragments is slower to skip past than to read.
     */
    fun focusCardDescription(kanji: String, meaning: String): String =
        focusCardDescriptionTemplate
            .replace(FIRST_ARGUMENT, kanji)
            .replace(SECOND_ARGUMENT, meaning)

    companion object {
        private const val FIRST_ARGUMENT = "%1\$s"
        private const val SECOND_ARGUMENT = "%2\$s"
    }
}

/**
 * The Browse wording.
 *
 * [rowDescription] assembles the row announcement here rather than in the renderer
 * for the reason Android's `browseKanjiRowDescription` gave by existing: the row is
 * four separate `Text`s plus a chip plus a checkbox, and left unmerged a screen reader
 * reads six fragments where the user wanted one sentence. Assembling it in a pure
 * function also means a test can assert the sentence without a window.
 */
@Suppress("LongParameterList")
data class BrowseCopy(
    val title: String,
    val searchHint: String,
    val searchAction: String,
    val similarFilter: String,
    val showSuspended: String,
    val resultNone: String,
    val selectionNone: String,
    val selectionAll: String,
    val selectAll: String,
    val deselectAll: String,
    val suspendedChip: String,
    val emptyTitle: String,
    val emptyBody: String,
    private val studiedToggleTemplate: String,
    private val rowDescriptionTemplate: String,
    private val selectionPartialTemplate: String,
    private val resultTruncatedTemplate: String,
    private val rowSelected: String,
    private val rowNotSelected: String,
) {
    /** The checkbox label, naming the kanji it is about. */
    fun studiedToggle(kanji: String): String =
        studiedToggleTemplate.replace(FIRST_ARGUMENT, kanji)

    /**
     * How many results, or that the list is capped.
     *
     * The capped case is its own sentence because a user searching a broad term needs
     * to know the list is not the whole answer — a bare count at exactly the limit
     * looks like a complete result set that happens to be a round number.
     */
    fun resultHeading(results: BrowseResults, countLine: String): String = when {
        results.rows.isEmpty() -> resultNone
        results.truncated ->
            resultTruncatedTemplate.replace(FIRST_ARGUMENT, results.rows.size.toString())
        else -> countLine
    }

    /** The selection summary above the list. */
    fun selectionSummary(results: BrowseResults): String = when {
        results.noneStudied -> selectionNone
        results.allStudied -> selectionAll
        else -> selectionPartialTemplate
            .replace(FIRST_ARGUMENT, results.studiedCount.toString())
            .replace(SECOND_ARGUMENT, results.rows.size.toString())
    }

    /** One merged sentence per row, in the order the row is laid out. */
    fun rowDescription(row: BrowseRow, resolver: UiTextResolver): String = buildList {
        add(rowDescriptionTemplate.replace(FIRST_ARGUMENT, row.kanji))
        add(resolver.resolve(row.meaning))
        add(resolver.resolve(row.readings))
        add(resolver.resolve(row.summary))
        if (row.suspended) add(suspendedChip)
        add(if (row.studied) rowSelected else rowNotSelected)
    }.filter { it.isNotBlank() }.joinToString(DESCRIPTION_SEPARATOR)

    companion object {
        private const val FIRST_ARGUMENT = "%1\$s"
        private const val SECOND_ARGUMENT = "%2\$s"

        /** Matches `browseKanjiRowDescription`, which joined on this. */
        private const val DESCRIPTION_SEPARATOR = ", "
    }
}

/**
 * Resolves [DashboardCopy] from this module's resources.
 *
 * Maps built exhaustively from the enum entries, so a new [HomeMetricKind],
 * [FocusEmptyReason], or [HomeNotice] is a compile error here rather than a
 * `NoSuchElementException` the first time a user reaches that state.
 */
@Composable
fun rememberDashboardCopy(): DashboardCopy {
    val metricLabels = HomeMetricKind.entries.associateWith { stringResource(it.resource()) }
    val emptyTitles = FocusEmptyReason.entries.associateWith {
        stringResource(it.titleResource())
    }
    val emptyBodies = FocusEmptyReason.entries.associateWith {
        stringResource(it.bodyResource())
    }
    val noticeTitles = HomeNotice.entries.associateWith { stringResource(it.titleResource()) }
    val noticeBodies = HomeNotice.entries.associateWith { stringResource(it.bodyResource()) }
    val fixed = FixedDashboardStrings(
        title = stringResource(Res.string.home_title),
        syncAction = stringResource(Res.string.home_sync_action),
        studyAction = stringResource(Res.string.home_study_action),
        todayTitle = stringResource(Res.string.home_today_title),
        deckOverviewTitle = stringResource(Res.string.home_deck_overview_title),
        metricCardDescription = stringResource(Res.string.home_metric_card_description),
        focusQueueTitle = stringResource(Res.string.focus_queue_title),
        focusQueueViewAll = stringResource(Res.string.focus_queue_view_all),
        focusCardDescriptionTemplate = stringResource(Res.string.focus_queue_card_description),
    )
    return remember(fixed, metricLabels, emptyTitles, emptyBodies, noticeTitles, noticeBodies) {
        fixed.toCopy(metricLabels, emptyTitles, emptyBodies, noticeTitles, noticeBodies)
    }
}

/** Resolves [BrowseCopy] from this module's resources. */
@Composable
fun rememberBrowseCopy(): BrowseCopy {
    val fixed = FixedBrowseStrings(
        title = stringResource(Res.string.browse_title),
        searchHint = stringResource(Res.string.browse_search_hint),
        searchAction = stringResource(Res.string.browse_search_action),
        similarFilter = stringResource(Res.string.browse_similar_filter),
        showSuspended = stringResource(Res.string.browse_show_suspended),
        resultNone = stringResource(Res.string.browse_result_none),
        selectionNone = stringResource(Res.string.browse_selection_none),
        selectionAll = stringResource(Res.string.browse_selection_all),
        selectAll = stringResource(Res.string.browse_select_all),
        deselectAll = stringResource(Res.string.browse_deselect_all),
        suspendedChip = stringResource(Res.string.browse_suspended_chip),
        emptyTitle = stringResource(Res.string.browse_empty_title),
        emptyBody = stringResource(Res.string.browse_empty_body),
        studiedToggleTemplate = stringResource(Res.string.browse_studied_toggle),
        rowDescriptionTemplate = stringResource(Res.string.browse_row_description),
        selectionPartialTemplate = stringResource(Res.string.browse_selection_partial),
        resultTruncatedTemplate = stringResource(Res.string.browse_result_truncated),
        rowSelected = stringResource(Res.string.browse_row_selected),
        rowNotSelected = stringResource(Res.string.browse_row_not_selected),
    )
    return remember(fixed) { fixed.toCopy() }
}

/** The plural result count for [size] rows, before truncation is considered. */
@Composable
fun rememberBrowseCountLine(size: Int): String =
    pluralStringResource(Res.plurals.browse_result_count, size, size)

/** The count on the study button, or blank when there is nothing waiting. */
@Composable
fun rememberStudyRemainingLine(count: Int): String = if (count > 0) {
    pluralStringResource(Res.plurals.home_study_remaining, count, count)
} else {
    ""
}

@Suppress("LongParameterList")
private data class FixedDashboardStrings(
    val title: String,
    val syncAction: String,
    val studyAction: String,
    val todayTitle: String,
    val deckOverviewTitle: String,
    val metricCardDescription: String,
    val focusQueueTitle: String,
    val focusQueueViewAll: String,
    val focusCardDescriptionTemplate: String,
) {
    fun toCopy(
        metricLabels: Map<HomeMetricKind, String>,
        emptyTitles: Map<FocusEmptyReason, String>,
        emptyBodies: Map<FocusEmptyReason, String>,
        noticeTitles: Map<HomeNotice, String>,
        noticeBodies: Map<HomeNotice, String>,
    ): DashboardCopy = DashboardCopy(
        title = title,
        syncAction = syncAction,
        studyAction = studyAction,
        todayTitle = todayTitle,
        deckOverviewTitle = deckOverviewTitle,
        metricCardDescription = metricCardDescription,
        focusQueueTitle = focusQueueTitle,
        focusQueueViewAll = focusQueueViewAll,
        metricLabels = metricLabels,
        emptyTitles = emptyTitles,
        emptyBodies = emptyBodies,
        noticeTitles = noticeTitles,
        noticeBodies = noticeBodies,
        focusCardDescriptionTemplate = focusCardDescriptionTemplate,
    )
}

@Suppress("LongParameterList")
private data class FixedBrowseStrings(
    val title: String,
    val searchHint: String,
    val searchAction: String,
    val similarFilter: String,
    val showSuspended: String,
    val resultNone: String,
    val selectionNone: String,
    val selectionAll: String,
    val selectAll: String,
    val deselectAll: String,
    val suspendedChip: String,
    val emptyTitle: String,
    val emptyBody: String,
    val studiedToggleTemplate: String,
    val rowDescriptionTemplate: String,
    val selectionPartialTemplate: String,
    val resultTruncatedTemplate: String,
    val rowSelected: String,
    val rowNotSelected: String,
) {
    fun toCopy(): BrowseCopy = BrowseCopy(
        title = title,
        searchHint = searchHint,
        searchAction = searchAction,
        similarFilter = similarFilter,
        showSuspended = showSuspended,
        resultNone = resultNone,
        selectionNone = selectionNone,
        selectionAll = selectionAll,
        selectAll = selectAll,
        deselectAll = deselectAll,
        suspendedChip = suspendedChip,
        emptyTitle = emptyTitle,
        emptyBody = emptyBody,
        studiedToggleTemplate = studiedToggleTemplate,
        rowDescriptionTemplate = rowDescriptionTemplate,
        selectionPartialTemplate = selectionPartialTemplate,
        resultTruncatedTemplate = resultTruncatedTemplate,
        rowSelected = rowSelected,
        rowNotSelected = rowNotSelected,
    )
}

private fun HomeMetricKind.resource(): StringResource = when (this) {
    HomeMetricKind.SYNC -> Res.string.home_metric_sync
    HomeMetricKind.STREAK -> Res.string.home_metric_streak
    HomeMetricKind.FOCUS -> Res.string.home_metric_focus
}

private fun FocusEmptyReason.titleResource(): StringResource = when (this) {
    FocusEmptyReason.NOTHING_IMPORTED -> Res.string.focus_queue_nothing_imported_title
    FocusEmptyReason.NOTHING_ACTIVE -> Res.string.focus_queue_nothing_active_title
}

private fun FocusEmptyReason.bodyResource(): StringResource = when (this) {
    FocusEmptyReason.NOTHING_IMPORTED -> Res.string.focus_queue_nothing_imported_body
    FocusEmptyReason.NOTHING_ACTIVE -> Res.string.focus_queue_nothing_active_body
}

private fun HomeNotice.titleResource(): StringResource = when (this) {
    HomeNotice.REDUCED_FSRS_PRECISION -> Res.string.notice_reduced_fsrs_title
}

private fun HomeNotice.bodyResource(): StringResource = when (this) {
    HomeNotice.REDUCED_FSRS_PRECISION -> Res.string.notice_reduced_fsrs_body
}
