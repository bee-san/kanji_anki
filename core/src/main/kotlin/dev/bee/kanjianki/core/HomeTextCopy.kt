package dev.bee.kanjianki.core

import java.util.Calendar
import java.util.Locale

object HomeTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun sentenceCase(value: String?): String {
        if (value.isNullOrEmpty()) {
            return ""
        }
        return value.substring(0, 1).uppercase(Locale.ROOT) + value.substring(1)
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun localizedRatingLabel(value: String): String {
        return when (StudyRatings.normalize(value)) {
            StudyRatings.AGAIN -> localizedText("Again", "再挑戦")
            StudyRatings.HARD -> localizedText("Hard", "難しい")
            StudyRatings.GOOD -> localizedText("Good", "良い")
            StudyRatings.EASY -> localizedText("Easy", "簡単")
            else -> sentenceCase(value)
        }
    }

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

    @JvmStatic
    fun focusHeadline(plan: RecordsSchedulerModels.AdaptiveLoadPlan?): String {
        if (plan == null || plan.target <= 0) {
            return localizedText("Waiting", "待機中")
        }
        if (plan.allKanjiMode) {
            return localizedText("All current", "全件最新")
        }
        return localizedText(
            "${plan.remaining}/${plan.target} left",
            "残り ${plan.remaining}/${plan.target} 件",
        )
    }

    @JvmStatic
    fun homeSyncValue(finishedAtMillis: Long?): String {
        if (finishedAtMillis == null) {
            return localizedText("Never synced", "まだ同期していません")
        }
        return sentenceCase(DateTextPolicy.humanSyncTime(finishedAtMillis))
    }

    @JvmStatic
    fun recentMistakeTitle(rowMeaning: String?): String {
        if (rowMeaning.isNullOrEmpty()) {
            return localizedText("Mistake", "ミス")
        }
        return rowMeaning
    }

    @JvmStatic
    fun recentMistakeSubtitle(rating: String?, dateText: String?): String {
        val safeRating = rating?.takeIf { it.isNotEmpty() }?.let(::localizedRatingLabel)
        val safeDate = dateText?.takeIf { it.isNotEmpty() }
        return listOfNotNull(safeRating, safeDate).joinToString(" · ").ifEmpty {
            localizedText("Missed", "見逃し")
        }
    }

    @JvmStatic
    fun streakHeadline(currentDays: Int): String {
        if (currentDays <= 0) {
            return localizedText("No streak yet", "連続日数なし")
        }
        return localizedText("$currentDays-day streak", "${currentDays}日連続")
    }

    @JvmStatic
    fun streakMetricBody(studiedToday: Boolean, bestDays: Int): String {
        if (studiedToday) {
            return if (bestDays > 0) {
                localizedText("Best: ${streakDayCount(bestDays)}", "最高: ${streakDayCount(bestDays)}")
            } else {
                localizedText("Done today", "今日は完了")
            }
        }
        return localizedText("Not done today", "今日は未完了")
    }

    @JvmStatic
    fun streakDayCount(days: Int): String {
        return localizedText(
            "$days ${if (days == 1) "day" else "days"}",
            "${days}日",
        )
    }

    @JvmStatic
    fun reviewToast(duplicate: Boolean, appliedRating: String?, currentStreakDays: Int): String {
        if (duplicate) {
            return localizedText("Already saved.", "すでに保存済み。")
        }
        val streakText = if (currentStreakDays <= 0) {
            ""
        } else {
            localizedText(
                " ${streakHeadline(currentStreakDays)}.",
                "${streakHeadline(currentStreakDays)}。",
            )
        }
        return when (appliedRating) {
            StudyRatings.AGAIN -> localizedText(
                "Saved. This kanji will come back soon.$streakText",
                "保存しました。この漢字はすぐ再登場します。$streakText",
            )
            StudyRatings.HARD -> localizedText(
                "Saved. This kanji stays in practice.$streakText",
                "保存しました。この漢字は練習中のままです。$streakText",
            )
            StudyRatings.GOOD, StudyRatings.EASY -> localizedText(
                "Saved. This kanji moved forward.$streakText",
                "保存しました。この漢字は次へ進みました。$streakText",
            )
            else -> localizedText("Saved.$streakText", "保存しました。$streakText")
        }
    }

    @JvmStatic
    fun appTitle(): String = localizedText("Kani", "カニ")

    @JvmStatic
    fun appSubtitle(): String = ""

    @JvmStatic
    fun syncAnkiDroidLabel(): String = localizedText("Sync AnkiDroid", "AnkiDroidを同期")

    @JvmStatic
    fun focusQueueTitle(): String = localizedText("Focus queue", "集中キュー")

    @JvmStatic
    fun viewAllLabel(): String = localizedText("View all", "すべて見る")

    @JvmStatic
    fun noKanjiQueuedTitle(): String = localizedText("No kanji queued", "キューに漢字がありません")

    @JvmStatic
    fun homeNoKanjiQueuedBody(): String =
        localizedText(
            "Sync AnkiDroid to load your kanji queue.",
            "AnkiDroidを同期して漢字キューを読み込みます。",
        )

    @JvmStatic
    fun focusQueueNoKanjiQueuedBody(): String =
        localizedText(
            "Sync AnkiDroid to load your kanji queue.",
            "AnkiDroidを同期して漢字キューを読み込みます。",
        )

    @JvmStatic
    fun syncMetricLabel(): String = localizedText("Sync", "同期")

    @JvmStatic
    fun syncMetricStatus(upToDate: Boolean): String =
        if (upToDate) localizedText("Up to date", "最新です") else localizedText("Tap to sync", "タップして同期")

    @JvmStatic
    fun streakMetricLabel(): String = localizedText("Streak", "連続日数")

    @JvmStatic
    fun focusMetricLabel(): String = localizedText("Focus", "集中")

    @JvmStatic
    fun homeMetricCardDescription(): String = localizedText("Home metric card", "ホームの指標カード")

    @JvmStatic
    fun focusQueueCardContentDescription(kanji: String, meaning: String): String =
        localizedText("Study card for $kanji, $meaning", "${kanji}の学習カード、$meaning")

    @JvmStatic
    fun deckOverviewTitle(): String = localizedText("Deck overview", "デッキ概要")

    @JvmStatic
    fun deckOverviewDueLabel(): String = localizedText("Due", "要復習")

    @JvmStatic
    fun deckOverviewNewLabel(): String = localizedText("New", "新規")

    @JvmStatic
    fun deckOverviewLearningLabel(): String = localizedText("Learning", "学習中")

    @JvmStatic
    fun deckOverviewRelearningLabel(): String = localizedText("Relearning", "再学習")

    @JvmStatic
    fun deckOverviewSuspendedLabel(): String = localizedText("Suspended", "停止中")

    @JvmStatic
    fun studyNowLabel(): String = localizedText("Study now", "今すぐ学習")

    /**
     * Count pill on the home Study-now card: cards remaining in the current/next
     * focus session (not a raw due count — the session is sized by the adaptive
     * plan, so this matches what tapping Study actually serves).
     */
    @JvmStatic
    fun studyRemainingCountLabel(count: Int): String =
        localizedText(
            StudyTextCopy.countText(count, "to study", "to study"),
            "残り${count}件",
        )

    @JvmStatic
    fun todayPlanTitle(): String = localizedText("Today", "今日")

    @JvmStatic
    fun todayPlanSummary(plan: DailyStudyPlan): String {
        return when (plan.recommendedAction) {
            RecommendedAction.STUDY_NOW -> {
                val loadText = when {
                    plan.dueNow > 0 -> localizedText("${plan.dueNow} due now", "${plan.dueNow}件が今すぐ復習")
                    plan.newProblemKanjiAvailable > 0 -> localizedText(
                        "${plan.newProblemKanjiAvailable} new problem kanji available",
                        "新しい問題漢字${plan.newProblemKanjiAvailable}件",
                    )
                    else -> localizedText("Study now", "今すぐ学習")
                }
                val timeText = if (plan.estimatedMinutes > 0) {
                    localizedText(" · about ${plan.estimatedMinutes} min", " · 約${plan.estimatedMinutes}分")
                } else {
                    ""
                }
                loadText + timeText
            }
            RecommendedAction.STUDY_ONCE_FOR_STREAK ->
                localizedText("Streak safe after 1 review", "1回の復習で連続を守れます")
            RecommendedAction.WAIT_UNTIL_LATER ->
                localizedText("Nothing useful now", "今は学ぶものなし")
            RecommendedAction.SYNC_FIRST ->
                localizedText("Sync needed before Kani can judge progress", "進捗を判断するには同期が必要")
            RecommendedAction.NOTHING_USEFUL_NOW ->
                localizedText("Nothing useful now", "今は学ぶものなし")
        }
    }

    @JvmStatic
    fun nextUsefulTimeLabel(nextUsefulReminderAtMillis: Long): String {
        if (nextUsefulReminderAtMillis <= 0L) {
            return localizedText("Next useful time: unknown", "次に有効な時刻: 不明")
        }
        val calendar = Calendar.getInstance().apply { timeInMillis = nextUsefulReminderAtMillis }
        return localizedText("Next useful time: ", "次に有効な時刻: ") +
            TimeOfDaySettingsPolicy.displayTime(
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
            )
    }

    @JvmStatic
    fun activePracticeEmptyTitle(): String = localizedText("No active practice yet", "学習中の漢字はまだありません")

    @JvmStatic
    fun activePracticeEmptyBody(): String = localizedText("Study now adds the next kanji.", "今すぐ学習すると次の漢字が追加されます。")

    @JvmStatic
    fun browseActionLabel(): String = localizedText("Browse Kanji", "漢字を閲覧")

    @JvmStatic
    fun recentMistakesTitle(): String = localizedText("Recent mistakes", "最近のミス")

    @JvmStatic
    fun statsActionLabel(): String = localizedText("Stats", "統計")

    @JvmStatic
    fun gamesActionLabel(): String = localizedText("Games", "ゲーム")

    @JvmStatic
    fun homeLabel(): String = localizedText("Home", "ホーム")

    @JvmStatic
    fun loadingLabel(): String = localizedText("Loading…", "読み込み中…")

    @JvmStatic
    fun routeLoadErrorTitle(): String = localizedText("Something went wrong", "問題が発生しました")

    @JvmStatic
    fun routeLoadErrorBody(): String = localizedText(
        "Kani hit an unexpected error while loading this screen. Your data is safe.",
        "この画面の読み込み中に予期しないエラーが発生しました。データは安全です。",
    )

    @JvmStatic
    fun retryLabel(): String = localizedText("Try again", "再試行")

    @JvmStatic
    fun noRecentMistakesTitle(): String = localizedText("No mistakes yet", "まだミスはありません")

    @JvmStatic
    fun noRecentMistakesBody(): String = localizedText("Missed or hard reviews.", "見逃しや難しかった復習。")

    @JvmStatic
    fun syncDialogTitle(): String = localizedText("Sync AnkiDroid?", "AnkiDroidを同期しますか？")

    @JvmStatic
    fun syncDialogMessage(settings: RecordsSyncModels.Settings?): String {
        val safeSettings = settings ?: throw NullPointerException("settings")
        return localizedText(
            "Kani keeps suspended ${safeSettings.modelName} cards on device. Turn on active cards if you want those too.",
            "Kaniは停止中の${safeSettings.modelName}カードを端末に保持します。アクティブカードも必要なら有効にしてください。",
        )
    }

    @JvmStatic
    fun syncDialogPositiveLabel(): String = localizedText("Sync cards", "カードを同期")

    @JvmStatic
    fun cancelLabel(): String = localizedText("Cancel", "キャンセル")

    @JvmStatic
    fun syncingTitle(): String = localizedText("Syncing AnkiDroid", "AnkiDroidを同期中")

    @JvmStatic
    fun syncAlreadyRunningTitle(): String = localizedText("Sync already running", "同期はすでに実行中")

    @JvmStatic
    fun syncAlreadyRunningFallback(): String = localizedText("Already reading AnkiDroid.", "すでにAnkiDroidを読み込んでいます。")

    @JvmStatic
    fun syncCompleteTitle(): String = localizedText("Sync complete", "同期完了")

    @JvmStatic
    fun syncReadyCountText(count: Int): String =
        localizedText(
            StudyTextCopy.countText(count, "kanji ready to study", "kanji ready to study"),
            "学習可能な漢字${count}件",
        )

    @JvmStatic
    fun syncCandidateSummary(dashboardRows: Int, adaptiveFocusText: String?): String {
        return localizedText(
            StudyTextCopy.countText(dashboardRows, "candidate from Anki", "candidates from Anki") +
                ". " +
                adaptiveFocusText.toString() +
                ".",
            "Ankiからの候補${dashboardRows}件。${adaptiveFocusText.toString()}。",
        )
    }

    @JvmStatic
    fun importedSuspendedKanjiText(count: Int): String =
        localizedText(
            StudyTextCopy.countText(count, "suspended kanji imported", "suspended kanji imported"),
            "停止中の漢字${count}件を取り込みました",
        )

    @JvmStatic
    fun syncNeedsAttentionTitle(): String = localizedText("AnkiDroid needs attention", "AnkiDroidに対応が必要です")

    @JvmStatic
    fun syncReadErrorTitle(): String = localizedText("Could not read AnkiDroid", "AnkiDroidを読み取れませんでした")

    @JvmStatic
    fun syncFailureFallback(): String = localizedText("Check AnkiDroid permissions, then retry.", "AnkiDroidの権限を確認してから再試行してください。")

    @JvmStatic
    fun trySyncAgainLabel(): String = localizedText("Try sync again", "再同期")

    @JvmStatic
    fun browseResultHeading(size: Int): String {
        if (size <= 0) {
            return localizedText("No matches", "該当なし")
        }
        if (size >= 300) {
            return localizedText("Showing first 300 matches", "最初の300件を表示")
        }
        return localizedText(StudyTextCopy.countText(size, "kanji", "kanji"), "${size}件の漢字")
    }

    @JvmStatic
    fun browseTitle(): String = localizedText("Browse Kanji", "漢字を閲覧")

    @JvmStatic
    fun browseSearchHint(): String = localizedText("Search kanji, meaning, reading, or examples", "漢字、意味、読み、例文を検索")

    @JvmStatic
    fun browseSearchButtonLabel(): String = localizedText("Search", "検索")

    @JvmStatic
    fun browseSimilarFilterLabel(): String = localizedText("Similar kanji only", "類似漢字のみ")

    @JvmStatic
    fun browseStudySelectionSummary(selected: Int, total: Int): String {
        val safeTotal = total.coerceAtLeast(0)
        val safeSelected = selected.coerceIn(0, safeTotal)
        if (safeTotal == 0 || safeSelected == 0) {
            return localizedText("None selected", "未選択")
        }
        if (safeSelected == safeTotal) {
            return localizedText("All selected", "すべて選択済み")
        }
        return localizedText("$safeSelected of $safeTotal selected", "${safeSelected}/${safeTotal}件を選択")
    }

    @JvmStatic
    fun browseSelectAllStudiedLabel(): String = localizedText("Select all", "すべて選択")

    @JvmStatic
    fun browseDeselectAllStudiedLabel(): String = localizedText("Clear all", "すべてクリア")

    @JvmStatic
    fun browseStudiedToggleLabel(kanji: String?): String = localizedText("Study this kanji", "この漢字を学習対象にする")

    @JvmStatic
    fun browseEmptyTitle(): String = localizedText("No local kanji found", "ローカル漢字が見つかりません")

    @JvmStatic
    fun browseEmptyBody(): String = localizedText("Sync AnkiDroid first, or try a different search.", "先にAnkiDroidを同期するか、別の検索を試してください。")

    @JvmStatic
    fun kanjiNotFoundTitle(): String = localizedText("Kanji not found", "漢字が見つかりません")

    @JvmStatic
    fun kanjiNotFoundBody(): String = localizedText("No local record found.", "ローカル記録が見つかりません。")

    @JvmStatic
    fun browseItemMeaning(item: RecordsImportModels.KanjiInventoryItem?): String {
        val safeItem = item ?: throw NullPointerException("item")
        return if (safeItem.primaryMeaning.isEmpty()) localizedText("Meaning not stored yet", "まだ意味は保存されていません") else safeItem.primaryMeaning
    }

    @JvmStatic
    fun browseInventorySummary(sourceCount: Int, exampleCount: Int): String {
        return localizedText(
            StudyTextCopy.countText(sourceCount, "local source", "local sources") +
                " · " +
                StudyTextCopy.countText(exampleCount, "example", "examples"),
            "ローカルソース${sourceCount}件 · 例文${exampleCount}件",
        )
    }

    @JvmStatic
    fun suspendedChipLabel(): String = localizedText("SUSPENDED", "停止中")

    @JvmStatic
    fun stuckChipLabel(): String = localizedText("STUCK", "停滞")

    @JvmStatic
    fun stuckChipHint(): String = localizedText(
        "This kanji keeps failing at the most-supported rung. Try a mnemonic or a mental story to make it stick.",
        "この漢字は最も支援の多いラングでも失敗し続けています。語呂合わせやイメージで覚えてみましょう。",
    )

    @JvmStatic
    fun mnemonicNoteTitle(): String = localizedText("My mnemonic", "自分の覚え方")

    @JvmStatic
    fun mnemonicNoteFieldLabel(): String = localizedText("Mnemonic note", "覚え方メモ")

    @JvmStatic
    fun mnemonicNoteHelper(stuck: Boolean): String {
        return if (stuck) {
            stuckChipHint()
        } else {
            localizedText(
                "Write a story, image, or association that helps this kanji stick.",
                "この漢字を思い出すための物語、イメージ、関連付けを書きましょう。",
            )
        }
    }

    @JvmStatic
    fun saveMnemonicNoteLabel(): String = localizedText("Save mnemonic", "覚え方を保存")

    @JvmStatic
    fun mnemonicNoteSavedToast(): String = localizedText("Mnemonic saved.", "覚え方メモを保存しました。")

    @JvmStatic
    fun mnemonicNoteClearedToast(): String = localizedText("Mnemonic cleared.", "覚え方メモを削除しました。")

    @JvmStatic
    fun relearningChipLabel(): String = localizedText("relearning", "再学習")

    @JvmStatic
    fun backToBrowseKanjiLabel(): String = localizedText("Back to Browse", "閲覧に戻る")

    @JvmStatic
    fun detailReasonTitle(): String = ""

    @JvmStatic
    fun historicalReasonText(): String =
        localizedText("Inactive; kept in recovery history.", "非アクティブ; 復元履歴として保持。")

    @JvmStatic
    fun activeReasonText(row: RecordsImportModels.DashboardRow?): String {
        val safeRow = row ?: throw NullPointerException("row")
        return if (safeRow.reasonText.isEmpty()) localizedText("Active practice evidence.", "アクティブな練習エビデンス。") else safeRow.reasonText
    }

    @JvmStatic
    fun ankiBrowserLine(browserSearch: String?): String = localizedText("Anki search: ${browserSearch.toString()}", "Anki検索: ${browserSearch.toString()}")

    @JvmStatic
    fun reviewNowLabel(): String = localizedText("Review now", "今すぐ復習")

    @JvmStatic
    fun copyAnkiSearchLabel(): String = localizedText("Copy search", "検索をコピー")

    @JvmStatic
    fun ankiSearchClipLabel(): String = localizedText("Anki search", "Anki検索")

    @JvmStatic
    fun ankiSearchCopiedToast(): String = localizedText("Search copied", "検索をコピーしました")

    @JvmStatic
    fun localSuspendButtonLabel(currentlySuspended: Boolean): String =
        if (currentlySuspended) localizedText("Unsuspend locally", "ローカル停止を解除") else localizedText("Suspend locally", "ローカルで停止")

    @JvmStatic
    fun localSuspendToast(wasSuspended: Boolean): String =
        if (wasSuspended) localizedText("Unsuspended.", "停止を解除しました。") else localizedText("Suspended locally.", "ローカルで停止しました。")

    @JvmStatic
    fun browseAllKanjiScopeLabel(): String = localizedText("All kanji", "全漢字")

    @JvmStatic
    fun browseInYourDeckMarker(): String = localizedText("In your deck", "デッキに登録済み")

    @JvmStatic
    fun browseNotInDeckLine(): String = localizedText("This kanji is not in your deck.", "この漢字はデッキに登録されていません。")

    @JvmStatic
    fun browseDictionaryPanelTitle(): String = localizedText("Dictionary", "辞書")

    @JvmStatic
    fun localizedStrokeCount(count: Int): String = localizedText("Strokes: $count", "画数: $count")

    @JvmStatic
    fun localizedGrade(grade: Int): String = localizedText("Grade: $grade", "学年: $grade")

    @JvmStatic
    fun localizedJitenRank(rank: Int?): String {
        if (rank == null) return ""
        return localizedText("Jiten rank: $rank", "字典ランク: $rank")
    }

    @JvmStatic
    fun confusedWithTitle(): String = localizedText("Confused with", "混同しやすい漢字")

    @JvmStatic
    fun confusedWithEvidence(youPicked: Int, itStole: Int): String? {
        if (youPicked <= 0 && itStole <= 0) return null
        val parts = mutableListOf<String>()
        if (youPicked > 0) parts.add(localizedText("you picked it ×$youPicked", "あなたが選んだ回数: ${youPicked}回"))
        if (itStole > 0) parts.add(localizedText("it stole ×$itStole", "奪われた回数: ${itStole}回"))
        return parts.joinToString(" · ")
    }

    @JvmStatic
    fun strokeOrderTitle(): String = localizedText("Stroke order", "書き順")

    @JvmStatic
    fun strokeOrderOverflow(omittedCount: Int): String =
        localizedText("+$omittedCount more strokes", "+${omittedCount}画省略")

    @JvmStatic
    fun examplesTitle(): String = localizedText("Examples", "例文")

    @JvmStatic
    fun localInventoryTitle(): String = localizedText("Local records", "ローカル記録")

    @JvmStatic
    fun localInventorySummary(sourceCount: Int, exampleCount: Int): String {
        return localizedText(
            StudyTextCopy.countText(sourceCount, "source", "sources") +
                " · " +
                StudyTextCopy.countText(exampleCount, "example", "examples"),
            "ソース${sourceCount}件 · 例文${exampleCount}件",
        )
    }

    @JvmStatic
    fun localInventorySearchLine(browserSearch: String?): String = localizedText("Anki search: ${browserSearch.toString()}", "Anki検索: ${browserSearch.toString()}")

    @JvmStatic
    fun localInventoryLastSeenLine(lastSeenAtMillis: Long): String =
        localizedText("Last seen ${DateTextPolicy.shortDateTime(lastSeenAtMillis)}", "最終確認 ${DateTextPolicy.shortDateTime(lastSeenAtMillis)}")

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
            return localizedText("Historical recovery", "復元履歴")
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
        localizedText("Mature support $matureSupportCount/$target", "成熟サポート $matureSupportCount/$target")

    @JvmStatic
    fun timelineEmptyText(): String = localizedText("Timeline appears after sync or review.", "同期または復習後にタイムラインが表示されます。")

    @JvmStatic
    fun recoveryTimelineTitle(): String = localizedText("Recovery timeline", "復元タイムライン")

    @JvmStatic
    fun noActiveEvidenceText(): String = localizedText("No active Anki evidence.", "アクティブな Anki エビデンスはありません。")

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
