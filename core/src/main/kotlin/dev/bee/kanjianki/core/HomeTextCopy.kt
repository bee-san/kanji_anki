package dev.bee.kanjianki.core

import java.util.Locale

object HomeTextCopy {
    @JvmStatic
    fun sentenceCase(value: String?): String {
        if (value.isNullOrEmpty()) {
            return ""
        }
        return value.substring(0, 1).uppercase(Locale.ROOT) + value.substring(1)
    }

    @JvmStatic
    fun focusHeadline(plan: RecordsSchedulerModels.AdaptiveLoadPlan?): String {
        if (plan == null || plan.target <= 0) {
            return "Waiting"
        }
        if (plan.allKanjiMode) {
            return "All current"
        }
        return "${plan.remaining}/${plan.target} left"
    }

    @JvmStatic
    fun homeSyncValue(finishedAtMillis: Long?): String {
        if (finishedAtMillis == null) {
            return "Never synced"
        }
        return sentenceCase(DateTextPolicy.humanSyncTime(finishedAtMillis))
    }

    @JvmStatic
    fun recentMistakeTitle(rowMeaning: String?): String {
        if (rowMeaning.isNullOrEmpty()) {
            return "Recent mistake"
        }
        return rowMeaning
    }

    @JvmStatic
    fun recentMistakeSubtitle(rating: String?, dateText: String?): String {
        val safeRating = rating?.takeIf { it.isNotEmpty() }?.let(::sentenceCase)
        val safeDate = dateText?.takeIf { it.isNotEmpty() }
        return listOfNotNull(safeRating, safeDate).joinToString(" · ").ifEmpty { "Recent miss" }
    }

    @JvmStatic
    fun streakHeadline(currentDays: Int): String {
        if (currentDays <= 0) {
            return "No streak yet"
        }
        return "$currentDays-day streak"
    }

    @JvmStatic
    fun streakMetricBody(studiedToday: Boolean, bestDays: Int): String {
        if (studiedToday) {
            return if (bestDays > 0) "Best: ${streakDayCount(bestDays)}" else "Done today"
        }
        return "Not done today"
    }

    @JvmStatic
    fun streakDayCount(days: Int): String {
        return "$days ${if (days == 1) "day" else "days"}"
    }

    @JvmStatic
    fun reviewToast(duplicate: Boolean, appliedRating: String?, currentStreakDays: Int): String {
        if (duplicate) {
            return "Already saved."
        }
        val streakText = if (currentStreakDays <= 0) "" else " ${streakHeadline(currentStreakDays)}."
        return when (appliedRating) {
            StudyRatings.AGAIN -> "Saved. This kanji will come back soon.$streakText"
            StudyRatings.HARD -> "Saved. This kanji stays in practice.$streakText"
            StudyRatings.GOOD, StudyRatings.EASY -> "Saved. This kanji moved forward.$streakText"
            else -> "Saved.$streakText"
        }
    }

    @JvmStatic
    fun appTitle(): String = "Kani"

    @JvmStatic
    fun appSubtitle(): String = ""

    @JvmStatic
    fun syncAnkiDroidLabel(): String = "Sync AnkiDroid"

    @JvmStatic
    fun focusQueueTitle(): String = "Focus queue"

    @JvmStatic
    fun viewAllLabel(): String = "View all"

    @JvmStatic
    fun noKanjiQueuedTitle(): String = "No kanji queued"

    @JvmStatic
    fun homeNoKanjiQueuedBody(): String =
        "Sync AnkiDroid to build the queue."

    @JvmStatic
    fun focusQueueNoKanjiQueuedBody(): String = "Sync AnkiDroid to build the queue."

    @JvmStatic
    fun syncMetricLabel(): String = "Sync"

    @JvmStatic
    fun syncMetricStatus(upToDate: Boolean): String = if (upToDate) "Up to date" else "Tap to sync"

    @JvmStatic
    fun streakMetricLabel(): String = "Streak"

    @JvmStatic
    fun focusMetricLabel(): String = "Focus"

    @JvmStatic
    fun deckOverviewTitle(): String = "Deck overview"

    @JvmStatic
    fun deckOverviewDueLabel(): String = "Due"

    @JvmStatic
    fun deckOverviewNewLabel(): String = "New"

    @JvmStatic
    fun deckOverviewLearningLabel(): String = "Learning"

    @JvmStatic
    fun deckOverviewRelearningLabel(): String = "Relearning"

    @JvmStatic
    fun deckOverviewSuspendedLabel(): String = "Suspended"

    @JvmStatic
    fun deckOverviewBuriedLabel(): String = "Buried"

    @JvmStatic
    fun browseActionLabel(): String = "Browse Kanji"

    @JvmStatic
    fun recentMistakesTitle(): String = "Recent mistakes"

    @JvmStatic
    fun statsActionLabel(): String = "Stats"

    @JvmStatic
    fun gamesActionLabel(): String = "Games"

    @JvmStatic
    fun homeLabel(): String = "Home"

    @JvmStatic
    fun loadingLabel(): String = "Loading…"

    @JvmStatic
    fun noRecentMistakesTitle(): String = "No recent mistakes yet"

    @JvmStatic
    fun noRecentMistakesBody(): String = "Missed and hard reviews appear here."

    @JvmStatic
    fun syncDialogTitle(): String = "Sync AnkiDroid?"

    @JvmStatic
    fun syncDialogMessage(settings: RecordsSyncModels.Settings?): String {
        val safeSettings = settings ?: throw NullPointerException("settings")
        return "Kani imports suspended ${safeSettings.modelName} cards by default, archives them locally, " +
            "and only uses active cards when that filter is enabled."
    }

    @JvmStatic
    fun syncDialogPositiveLabel(): String = "Sync cards"

    @JvmStatic
    fun cancelLabel(): String = "Cancel"

    @JvmStatic
    fun syncingTitle(): String = "Syncing AnkiDroid"

    @JvmStatic
    fun syncAlreadyRunningTitle(): String = "Sync already running"

    @JvmStatic
    fun syncAlreadyRunningFallback(): String = "Kani is already reading AnkiDroid."

    @JvmStatic
    fun syncCompleteTitle(): String = "Sync complete"

    @JvmStatic
    fun syncReadyCountText(count: Int): String =
        StudyTextCopy.countText(count, "kanji ready to study", "kanji ready to study")

    @JvmStatic
    fun syncCandidateSummary(dashboardRows: Int, adaptiveFocusText: String?): String {
        return StudyTextCopy.countText(dashboardRows, "candidate found from Anki", "candidates found from Anki") +
            ". " +
            adaptiveFocusText.toString() +
            "."
    }

    @JvmStatic
    fun importedSuspendedKanjiText(count: Int): String =
        StudyTextCopy.countText(count, "new archived suspended kanji added", "new archived suspended kanji added")

    @JvmStatic
    fun syncNeedsAttentionTitle(): String = "AnkiDroid needs attention"

    @JvmStatic
    fun syncReadErrorTitle(): String = "Could not read AnkiDroid"

    @JvmStatic
    fun syncFailureFallback(): String = "Try again after checking AnkiDroid permissions."

    @JvmStatic
    fun trySyncAgainLabel(): String = "Try sync again"

    @JvmStatic
    fun browseResultHeading(size: Int): String {
        if (size <= 0) {
            return "No matches"
        }
        if (size >= 300) {
            return "Showing first 300 matches"
        }
        return StudyTextCopy.countText(size, "kanji", "kanji")
    }

    @JvmStatic
    fun browseTitle(): String = "Browse Kanji"

    @JvmStatic
    fun browseSearchHint(): String = "Search kanji, meaning, reading, or examples"

    @JvmStatic
    fun browseSearchButtonLabel(): String = "Search"

    @JvmStatic
    fun browseEmptyTitle(): String = "No local kanji found"

    @JvmStatic
    fun browseEmptyBody(): String = "Sync AnkiDroid first, or try a different search."

    @JvmStatic
    fun kanjiNotFoundTitle(): String = "Kanji not found"

    @JvmStatic
    fun kanjiNotFoundBody(): String = "No local record found."

    @JvmStatic
    fun browseItemMeaning(item: RecordsImportModels.KanjiInventoryItem?): String {
        val safeItem = item ?: throw NullPointerException("item")
        return if (safeItem.primaryMeaning.isEmpty()) "Meaning not stored yet" else safeItem.primaryMeaning
    }

    @JvmStatic
    fun browseInventorySummary(sourceCount: Int, exampleCount: Int): String {
        return StudyTextCopy.countText(sourceCount, "local source", "local sources") +
            " · " +
            StudyTextCopy.countText(exampleCount, "example", "examples")
    }

    @JvmStatic
    fun suspendedChipLabel(): String = "SUSPENDED"

    @JvmStatic
    fun relearningChipLabel(): String = "relearning"

    @JvmStatic
    fun backToBrowseKanjiLabel(): String = "Back to Browse"

    @JvmStatic
    fun detailReasonTitle(): String = ""

    @JvmStatic
    fun historicalReasonText(): String =
        "Inactive; kept in recovery history."

    @JvmStatic
    fun activeReasonText(row: RecordsImportModels.DashboardRow?): String {
        val safeRow = row ?: throw NullPointerException("row")
        return if (safeRow.reasonText.isEmpty()) "Active practice evidence." else safeRow.reasonText
    }

    @JvmStatic
    fun ankiBrowserLine(browserSearch: String?): String = "Anki search: ${browserSearch.toString()}"

    @JvmStatic
    fun reviewNowLabel(): String = "Review now"

    @JvmStatic
    fun copyAnkiSearchLabel(): String = "Copy search"

    @JvmStatic
    fun ankiSearchClipLabel(): String = "Anki search"

    @JvmStatic
    fun ankiSearchCopiedToast(): String = "Search copied"

    @JvmStatic
    fun localSuspendButtonLabel(currentlySuspended: Boolean): String =
        if (currentlySuspended) "Unsuspend locally" else "Suspend locally"

    @JvmStatic
    fun localSuspendToast(wasSuspended: Boolean): String =
        if (wasSuspended) "Unsuspended." else "Suspended locally."

    @JvmStatic
    fun examplesTitle(): String = "Examples"

    @JvmStatic
    fun localInventoryTitle(): String = "Local records"

    @JvmStatic
    fun localInventorySummary(sourceCount: Int, exampleCount: Int): String {
        return StudyTextCopy.countText(sourceCount, "source", "sources") +
            " · " +
            StudyTextCopy.countText(exampleCount, "example", "examples")
    }

    @JvmStatic
    fun localInventorySearchLine(browserSearch: String?): String = "Anki search: ${browserSearch.toString()}"

    @JvmStatic
    fun localInventoryLastSeenLine(lastSeenAtMillis: Long): String =
        "Last seen ${DateTextPolicy.shortDateTime(lastSeenAtMillis)}"

    @JvmStatic
    fun detailDisplayKanji(
        fallback: String,
        row: RecordsImportModels.DashboardRow?,
        inventory: RecordsImportModels.KanjiInventoryItem?,
    ): String {
        if (row != null) {
            return row.kanji
        }
        return inventory?.kanji ?: fallback
    }

    @JvmStatic
    fun inventoryTitle(inventory: RecordsImportModels.KanjiInventoryItem?): String {
        if (inventory == null || inventory.primaryMeaning.isEmpty()) {
            return "Historical recovery"
        }
        return inventory.primaryMeaning
    }

    @JvmStatic
    fun detailBrowserSearch(
        row: RecordsImportModels.DashboardRow?,
        inventory: RecordsImportModels.KanjiInventoryItem?,
    ): String {
        if (inventory != null && inventory.browserSearch.isNotEmpty()) {
            return inventory.browserSearch
        }
        if (row != null && row.browserSearch.isNotEmpty()) {
            return row.browserSearch
        }
        return ""
    }

    @JvmStatic
    fun matureSupportTargetText(matureSupportCount: Int, target: Int): String =
        "Mature support $matureSupportCount/$target"

    @JvmStatic
    fun timelineEmptyText(): String = "Timeline appears after sync or review."

    @JvmStatic
    fun recoveryTimelineTitle(): String = "Recovery timeline"

    @JvmStatic
    fun noActiveEvidenceText(): String = "No active Anki evidence."

    @JvmStatic
    fun exampleSourceLabel(example: RecordsImportModels.Example?): String =
        (example ?: throw NullPointerException("example")).sourceType.uppercase(Locale.ROOT)

    @JvmStatic
    fun exampleExpressionLine(example: RecordsImportModels.Example?): String {
        val safeExample = example ?: throw NullPointerException("example")
        if (safeExample.reading.isEmpty()) {
            return safeExample.expression
        }
        return "${safeExample.expression}  ${safeExample.reading}"
    }

    @JvmStatic
    fun exampleMeaningLine(example: RecordsImportModels.Example?): String {
        val safeExample = example ?: throw NullPointerException("example")
        if (safeExample.meaning.isEmpty()) {
            return ""
        }
        return StudyTextCopy.cleanLearnerText(safeExample.meaning, safeExample.meaning, 120)
    }
}
