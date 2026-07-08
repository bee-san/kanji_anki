package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class HomeTextCopyTest {
    @Test
    fun sentenceCasePreservesNullEmptyAndFirstCharacterOnlyBehavior() {
        assertEquals("", HomeTextCopy.sentenceCase(null))
        assertEquals("", HomeTextCopy.sentenceCase(""))
        assertEquals("Synced today", HomeTextCopy.sentenceCase("synced today"))
        assertEquals("Already synced", HomeTextCopy.sentenceCase("Already synced"))
    }

    @Test
    fun focusHeadlinePreservesHomeMetricCopy() {
        val waiting = RecordsSchedulerModels.AdaptiveLoadPlan(20, 0, 0, emptyList(), 0, false, "")
        val all = RecordsSchedulerModels.AdaptiveLoadPlan(100, 2, 2, listOf("裂", "語"), 0, true, "all")
        val focused = RecordsSchedulerModels.AdaptiveLoadPlan(20, 4, 1, listOf("裂", "語"), 0, false, "focus")

        assertEquals("Waiting", HomeTextCopy.focusHeadline(null))
        assertEquals("Waiting", HomeTextCopy.focusHeadline(waiting))
        assertEquals("All current", HomeTextCopy.focusHeadline(all))
        assertEquals("1/4 left", HomeTextCopy.focusHeadline(focused))
    }

    @Test
    fun homeSyncAndRecentMistakeCopyPreserveFallbacks() {
        assertEquals("Never synced", HomeTextCopy.homeSyncValue(null))
        assertEquals("Date unknown", HomeTextCopy.homeSyncValue(0L))
        assertEquals("Mistake", HomeTextCopy.recentMistakeTitle(null))
        assertEquals("Mistake", HomeTextCopy.recentMistakeTitle(""))
        assertEquals("split", HomeTextCopy.recentMistakeTitle("split"))
        assertEquals("Again · Unknown time", HomeTextCopy.recentMistakeSubtitle("again", "Unknown time"))
        assertEquals("Missed", HomeTextCopy.recentMistakeSubtitle(null, null))
    }

    @Test
    fun studyRemainingCountLabelDescribesSessionRemainingCards() {
        assertEquals("1 to study", HomeTextCopy.studyRemainingCountLabel(1))
        assertEquals("5 to study", HomeTextCopy.studyRemainingCountLabel(5))
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            assertEquals("残り5件", HomeTextCopy.studyRemainingCountLabel(5))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun streakCopyPreservesHomeMetricCopy() {
        assertEquals("No streak yet", HomeTextCopy.streakHeadline(0))
        assertEquals("No streak yet", HomeTextCopy.streakHeadline(-1))
        assertEquals("2-day streak", HomeTextCopy.streakHeadline(2))
        assertEquals("Not done today", HomeTextCopy.streakMetricBody(false, 0))
        assertEquals("Best: 5 days", HomeTextCopy.streakMetricBody(true, 5))
        assertEquals("Done today", HomeTextCopy.streakMetricBody(true, 0))
        assertEquals("1 day", HomeTextCopy.streakDayCount(1))
        assertEquals("3 days", HomeTextCopy.streakDayCount(3))
    }

    @Test
    fun reviewToastPreservesSavedCopyAndStreakSuffix() {
        assertEquals("Already saved.", HomeTextCopy.reviewToast(true, StudyRatings.GOOD, 2))
        assertEquals("Saved.", HomeTextCopy.reviewToast(false, null, 0))
        assertEquals("Saved. 2-day streak.", HomeTextCopy.reviewToast(false, "unknown", 2))
        assertEquals(
            "Saved. This kanji moved forward.",
            HomeTextCopy.reviewToast(false, StudyRatings.GOOD, 0)
        )
        assertEquals(
            "Saved. This kanji moved forward. 2-day streak.",
            HomeTextCopy.reviewToast(false, StudyRatings.EASY, 2)
        )
        assertEquals(
            "Saved. This kanji stays in practice.",
            HomeTextCopy.reviewToast(false, StudyRatings.HARD, 0)
        )
        assertEquals(
            "Saved. This kanji stays in practice. 2-day streak.",
            HomeTextCopy.reviewToast(false, StudyRatings.HARD, 2)
        )
        assertEquals(
            "Saved. This kanji will come back soon.",
            HomeTextCopy.reviewToast(false, StudyRatings.AGAIN, 0)
        )
        assertEquals(
            "Saved. This kanji will come back soon. 2-day streak.",
            HomeTextCopy.reviewToast(false, StudyRatings.AGAIN, 2)
        )
    }

    @Test
    fun homeShellCopyPreservesHeaderMetricsAndEmptyStates() {
        assertEquals("Kani", HomeTextCopy.appTitle())
        assertEquals("", HomeTextCopy.appSubtitle())
        assertEquals("Sync AnkiDroid", HomeTextCopy.syncAnkiDroidLabel())
        assertEquals("Focus queue", HomeTextCopy.focusQueueTitle())
        assertEquals("View all", HomeTextCopy.viewAllLabel())
        assertEquals("No kanji queued", HomeTextCopy.noKanjiQueuedTitle())
        assertEquals(
            "Sync AnkiDroid to load your kanji queue.",
            HomeTextCopy.homeNoKanjiQueuedBody()
        )
        assertEquals("Sync AnkiDroid to load your kanji queue.", HomeTextCopy.focusQueueNoKanjiQueuedBody())
        assertEquals("Sync", HomeTextCopy.syncMetricLabel())
        assertEquals("Up to date", HomeTextCopy.syncMetricStatus(true))
        assertEquals("Tap to sync", HomeTextCopy.syncMetricStatus(false))
        assertEquals("Streak", HomeTextCopy.streakMetricLabel())
        assertEquals("Focus", HomeTextCopy.focusMetricLabel())
        assertEquals("Browse Kanji", HomeTextCopy.browseActionLabel())
        assertEquals("Recent mistakes", HomeTextCopy.recentMistakesTitle())
        assertEquals("Stats", HomeTextCopy.statsActionLabel())
        assertEquals("Games", HomeTextCopy.gamesActionLabel())
        assertEquals("Home", HomeTextCopy.homeLabel())
        assertEquals("Home metric card", HomeTextCopy.homeMetricCardDescription())
        assertEquals("Study card for 語, language", HomeTextCopy.focusQueueCardContentDescription("語", "language"))
        assertEquals("Learning", HomeTextCopy.deckOverviewLearningLabel())
        assertEquals("Study now", HomeTextCopy.studyNowLabel())
        assertEquals("No active practice yet", HomeTextCopy.activePracticeEmptyTitle())
        assertEquals("Study now adds the next kanji.", HomeTextCopy.activePracticeEmptyBody())
        assertEquals("No mistakes yet", HomeTextCopy.noRecentMistakesTitle())
        assertEquals("Missed or hard reviews.", HomeTextCopy.noRecentMistakesBody())
        assertEquals("Loading…", HomeTextCopy.loadingLabel())
        assertEquals("Something went wrong", HomeTextCopy.routeLoadErrorTitle())
        assertEquals(
            "Kani hit an unexpected error while loading this screen. Your data is safe.",
            HomeTextCopy.routeLoadErrorBody()
        )
        assertEquals("Try again", HomeTextCopy.retryLabel())
    }

    @Test
    fun todayPlanCopyPreservesReasonStringsAndReminderLabel() {
        val plan = DailyStudyPlan(
            dateLocalDay = 0L,
            dueNow = 4,
            dueLater = 2,
            newProblemKanjiAvailable = 1,
            streakStatus = StreakStatus.SAFE,
            estimatedMinutes = 2,
            recommendedAction = RecommendedAction.STUDY_NOW,
            nextUsefulReminderAtMillis = 0L,
            dueLookahead = DueLookaheadWindow(4, 2, 0L, 0, 0L),
            syncStatus = SyncStatus.CURRENT,
            reasons = listOf("4 due now", "1 new problem kanji available"),
        )

        assertEquals("Today", HomeTextCopy.todayPlanTitle())
        assertEquals("4 due now · about 2 min", HomeTextCopy.todayPlanSummary(plan))
        assertEquals("Next useful time: unknown", HomeTextCopy.nextUsefulTimeLabel(0L))
    }

    @Test
    fun nextUsefulTimeLabelUsesLocalClockTime() {
        withTimeZone(TimeZone.getTimeZone("UTC")) {
            val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
            calendar.set(2026, Calendar.JUNE, 12, 20, 30, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            assertEquals("Next useful time: 20:30", HomeTextCopy.nextUsefulTimeLabel(calendar.timeInMillis))
        }
    }

    @Test
    fun todayPlanCopyHandlesStreakAndSyncStates() {
        val streakPlan = DailyStudyPlan(
            dateLocalDay = 0L,
            dueNow = 1,
            dueLater = 0,
            newProblemKanjiAvailable = 0,
            streakStatus = StreakStatus.NEEDS_ONE_REVIEW,
            estimatedMinutes = 1,
            recommendedAction = RecommendedAction.STUDY_ONCE_FOR_STREAK,
            nextUsefulReminderAtMillis = 0L,
            dueLookahead = DueLookaheadWindow(1, 0, 0L, 0, 0L),
            syncStatus = SyncStatus.CURRENT,
            reasons = listOf("1 review keeps the streak alive"),
        )
        val waitPlan = DailyStudyPlan(
            dateLocalDay = 0L,
            dueNow = 0,
            dueLater = 3,
            newProblemKanjiAvailable = 0,
            streakStatus = StreakStatus.NO_STREAK_ACTIVE,
            estimatedMinutes = 0,
            recommendedAction = RecommendedAction.WAIT_UNTIL_LATER,
            nextUsefulReminderAtMillis = 123L,
            dueLookahead = DueLookaheadWindow(0, 3, 123L, 1, 123L),
            syncStatus = SyncStatus.CURRENT,
            reasons = listOf("3 due later"),
        )
        val syncPlan = DailyStudyPlan(
            dateLocalDay = 0L,
            dueNow = 0,
            dueLater = 0,
            newProblemKanjiAvailable = 0,
            streakStatus = StreakStatus.NO_STREAK_ACTIVE,
            estimatedMinutes = 0,
            recommendedAction = RecommendedAction.SYNC_FIRST,
            nextUsefulReminderAtMillis = 0L,
            dueLookahead = DueLookaheadWindow(0, 0, 0L, 0, 0L),
            syncStatus = SyncStatus.SYNC_NEEDED_TO_JUDGE_PROGRESS,
            reasons = listOf("Sync needed before Kani can judge progress"),
        )

        assertEquals("Streak safe after 1 review", HomeTextCopy.todayPlanSummary(streakPlan))
        assertEquals("Nothing useful now", HomeTextCopy.todayPlanSummary(waitPlan))
        assertEquals("Sync needed before Kani can judge progress", HomeTextCopy.todayPlanSummary(syncPlan))
    }

    @Test
    fun homeShellAndSyncCopyTranslateToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            val waiting = RecordsSchedulerModels.AdaptiveLoadPlan(20, 0, 0, emptyList(), 0, false, "")
            val all = RecordsSchedulerModels.AdaptiveLoadPlan(100, 2, 2, listOf("裂", "語"), 0, true, "all")
            val focused = RecordsSchedulerModels.AdaptiveLoadPlan(20, 4, 1, listOf("裂", "語"), 0, false, "focus")
            val settings = RecordsSyncModels.Settings(
                "Basic",
                "kanji",
                "meaning",
                "reading",
                "deck",
                "",
                "",
                "",
                2,
                1,
                1000,
                20,
                5
            )

            assertEquals("カニ", HomeTextCopy.appTitle())
            assertEquals("待機中", HomeTextCopy.focusHeadline(waiting))
            assertEquals("全件最新", HomeTextCopy.focusHeadline(all))
            assertEquals("残り 1/4 件", HomeTextCopy.focusHeadline(focused))
            assertEquals("まだ同期していません", HomeTextCopy.homeSyncValue(null))
            assertEquals("ミス", HomeTextCopy.recentMistakeTitle(null))
            assertEquals("再挑戦 · Unknown time", HomeTextCopy.recentMistakeSubtitle("again", "Unknown time"))
            assertEquals("見逃し", HomeTextCopy.recentMistakeSubtitle(null, null))
            assertEquals("連続日数なし", HomeTextCopy.streakHeadline(0))
            assertEquals("2日連続", HomeTextCopy.streakHeadline(2))
            assertEquals("今日は未完了", HomeTextCopy.streakMetricBody(false, 0))
            assertEquals("最高: 5日", HomeTextCopy.streakMetricBody(true, 5))
            assertEquals("今日は完了", HomeTextCopy.streakMetricBody(true, 0))
            assertEquals("1日", HomeTextCopy.streakDayCount(1))
            assertEquals("3日", HomeTextCopy.streakDayCount(3))
            assertEquals("保存しました。この漢字はすぐ再登場します。2日連続。", HomeTextCopy.reviewToast(false, StudyRatings.AGAIN, 2))
            assertEquals("保存しました。この漢字は練習中のままです。", HomeTextCopy.reviewToast(false, StudyRatings.HARD, 0))
            assertEquals("保存しました。この漢字は次へ進みました。2日連続。", HomeTextCopy.reviewToast(false, StudyRatings.GOOD, 2))
            assertEquals("保存しました。", HomeTextCopy.reviewToast(false, null, 0))
            assertEquals("すでに保存済み。", HomeTextCopy.reviewToast(true, StudyRatings.GOOD, 2))
            assertEquals("AnkiDroidを同期", HomeTextCopy.syncAnkiDroidLabel())
            assertEquals("集中キュー", HomeTextCopy.focusQueueTitle())
            assertEquals("キューに漢字がありません", HomeTextCopy.noKanjiQueuedTitle())
            assertEquals("ホームの指標カード", HomeTextCopy.homeMetricCardDescription())
            assertEquals("語の学習カード、言語", HomeTextCopy.focusQueueCardContentDescription("語", "言語"))
            assertEquals("学習中", HomeTextCopy.deckOverviewLearningLabel())
            assertEquals("今すぐ学習", HomeTextCopy.studyNowLabel())
            val japanPlan = DailyStudyPlan(
                dateLocalDay = 0L,
                dueNow = 4,
                dueLater = 1,
                newProblemKanjiAvailable = 0,
                streakStatus = StreakStatus.SAFE,
                estimatedMinutes = 2,
                recommendedAction = RecommendedAction.STUDY_NOW,
                nextUsefulReminderAtMillis = 0L,
                dueLookahead = DueLookaheadWindow(4, 1, 0L, 0, 0L),
                syncStatus = SyncStatus.CURRENT,
                reasons = listOf("4 due now"),
            )
            assertEquals("今日", HomeTextCopy.todayPlanTitle())
            assertEquals("4件が今すぐ復習 · 約2分", HomeTextCopy.todayPlanSummary(japanPlan))
            assertEquals("次に有効な時刻: 不明", HomeTextCopy.nextUsefulTimeLabel(0L))
            assertEquals("学習中の漢字はまだありません", HomeTextCopy.activePracticeEmptyTitle())
            assertEquals("今すぐ学習すると次の漢字が追加されます。", HomeTextCopy.activePracticeEmptyBody())
            assertEquals("同期", HomeTextCopy.syncMetricLabel())
            assertEquals("最新です", HomeTextCopy.syncMetricStatus(true))
            assertEquals("タップして同期", HomeTextCopy.syncMetricStatus(false))
            assertEquals("AnkiDroidを同期しますか？", HomeTextCopy.syncDialogTitle())
            assertEquals(
                "Kaniは停止中のBasicカードを端末に保持します。アクティブカードも必要なら有効にしてください。",
                HomeTextCopy.syncDialogMessage(settings)
            )
            assertEquals("カードを同期", HomeTextCopy.syncDialogPositiveLabel())
            assertEquals("キャンセル", HomeTextCopy.cancelLabel())
            assertEquals("同期完了", HomeTextCopy.syncCompleteTitle())
            assertEquals("学習可能な漢字1件", HomeTextCopy.syncReadyCountText(1))
            assertEquals("学習可能な漢字3件", HomeTextCopy.syncReadyCountText(3))
            assertEquals("Ankiからの候補1件。Auto Pareto: 1 item today。", HomeTextCopy.syncCandidateSummary(1, "Auto Pareto: 1 item today"))
            assertEquals("停止中の漢字4件を取り込みました", HomeTextCopy.importedSuspendedKanjiText(4))
            assertEquals("AnkiDroidに対応が必要です", HomeTextCopy.syncNeedsAttentionTitle())
            assertEquals("AnkiDroidを読み取れませんでした", HomeTextCopy.syncReadErrorTitle())
            assertEquals("AnkiDroidの権限を確認してから再試行してください。", HomeTextCopy.syncFailureFallback())
            assertEquals("再同期", HomeTextCopy.trySyncAgainLabel())
        }
    }

    @Test
    fun syncCopyPreservesDialogResultAndFallbackText() {
        val settings = RecordsSyncModels.Settings(
            "Basic",
            "kanji",
            "meaning",
            "reading",
            "deck",
            "",
            "",
            "",
            2,
            1,
            1000,
            20,
            5
        )

        assertEquals("Sync AnkiDroid?", HomeTextCopy.syncDialogTitle())
        assertEquals(
            "Kani keeps suspended Basic cards on device. Turn on active cards if you want those too.",
            HomeTextCopy.syncDialogMessage(settings)
        )
        assertEquals("Sync cards", HomeTextCopy.syncDialogPositiveLabel())
        assertEquals("Cancel", HomeTextCopy.cancelLabel())
        assertEquals("Syncing AnkiDroid", HomeTextCopy.syncingTitle())
        assertEquals("Sync already running", HomeTextCopy.syncAlreadyRunningTitle())
        assertEquals("Already reading AnkiDroid.", HomeTextCopy.syncAlreadyRunningFallback())
        assertEquals("Sync complete", HomeTextCopy.syncCompleteTitle())
        assertEquals("1 kanji ready to study", HomeTextCopy.syncReadyCountText(1))
        assertEquals("3 kanji ready to study", HomeTextCopy.syncReadyCountText(3))
        assertEquals(
            "1 candidate from Anki. Auto Pareto: 1 item today.",
            HomeTextCopy.syncCandidateSummary(1, "Auto Pareto: 1 item today")
        )
        assertEquals(
            "2 candidates from Anki. Auto Pareto: 2 items today.",
            HomeTextCopy.syncCandidateSummary(2, "Auto Pareto: 2 items today")
        )
        assertEquals("1 suspended kanji imported", HomeTextCopy.importedSuspendedKanjiText(1))
        assertEquals("4 suspended kanji imported", HomeTextCopy.importedSuspendedKanjiText(4))
        assertEquals("AnkiDroid needs attention", HomeTextCopy.syncNeedsAttentionTitle())
        assertEquals("Could not read AnkiDroid", HomeTextCopy.syncReadErrorTitle())
        assertEquals("Check AnkiDroid permissions, then retry.", HomeTextCopy.syncFailureFallback())
        assertEquals("Try sync again", HomeTextCopy.trySyncAgainLabel())
        assertThrows(NullPointerException::class.java) { HomeTextCopy.syncDialogMessage(null) }
    }

    @Test
    fun browseResultHeadingPreservesBrowseCopy() {
        assertEquals("No matches", HomeTextCopy.browseResultHeading(0))
        assertEquals("No matches", HomeTextCopy.browseResultHeading(-1))
        assertEquals("2 kanji", HomeTextCopy.browseResultHeading(2))
        assertEquals("Showing first 300 matches", HomeTextCopy.browseResultHeading(300))
    }

    @Test
    fun browseStaticCopyAndFallbackMeaningStayCentralized() {
        assertEquals("Browse Kanji", HomeTextCopy.browseTitle())
        assertEquals("Search kanji, meaning, reading, or examples", HomeTextCopy.browseSearchHint())
        assertEquals("Search", HomeTextCopy.browseSearchButtonLabel())
        assertEquals("Similar kanji only", HomeTextCopy.browseSimilarFilterLabel())
        assertEquals("1 of 2 selected", HomeTextCopy.browseStudySelectionSummary(1, 2))
        assertEquals("All selected", HomeTextCopy.browseStudySelectionSummary(2, 2))
        assertEquals("None selected", HomeTextCopy.browseStudySelectionSummary(0, 0))
        assertEquals("Select all", HomeTextCopy.browseSelectAllStudiedLabel())
        assertEquals("Clear all", HomeTextCopy.browseDeselectAllStudiedLabel())
        assertEquals("Study this kanji", HomeTextCopy.browseStudiedToggleLabel("語"))
        assertEquals("No local kanji found", HomeTextCopy.browseEmptyTitle())
        assertEquals("Sync AnkiDroid first, or try a different search.", HomeTextCopy.browseEmptyBody())
        assertEquals("Kanji not found", HomeTextCopy.kanjiNotFoundTitle())
        assertEquals("No local record found.", HomeTextCopy.kanjiNotFoundBody())
        assertEquals("Meaning not stored yet", HomeTextCopy.browseItemMeaning(inventory("語", "", "")))
        assertEquals("language", HomeTextCopy.browseItemMeaning(inventory("語", "language", "")))
        assertEquals("1 local source · 2 examples", HomeTextCopy.browseInventorySummary(1, 2))
        assertEquals("3 local sources · 1 example", HomeTextCopy.browseInventorySummary(3, 1))
        assertEquals("SUSPENDED", HomeTextCopy.suspendedChipLabel())
        assertEquals("relearning", HomeTextCopy.relearningChipLabel())
        assertEquals("Back to Browse", HomeTextCopy.backToBrowseKanjiLabel())
        assertThrows(NullPointerException::class.java) { HomeTextCopy.browseItemMeaning(null) }
    }

    @Test
    fun browseAndDetailCopyTranslateToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            val inventory = inventory("語", "language", "inventory:語")
            val row = row("裂", "row:裂")
            val rowWithReason = row("裂", "row:裂", "manual reason")

            assertEquals("該当なし", HomeTextCopy.browseResultHeading(0))
            assertEquals("該当なし", HomeTextCopy.browseResultHeading(-1))
            assertEquals("2件の漢字", HomeTextCopy.browseResultHeading(2))
            assertEquals("最初の300件を表示", HomeTextCopy.browseResultHeading(300))
            assertEquals("漢字を閲覧", HomeTextCopy.browseTitle())
            assertEquals("漢字、意味、読み、例文を検索", HomeTextCopy.browseSearchHint())
            assertEquals("検索", HomeTextCopy.browseSearchButtonLabel())
            assertEquals("類似漢字のみ", HomeTextCopy.browseSimilarFilterLabel())
            assertEquals("1/2件を選択", HomeTextCopy.browseStudySelectionSummary(1, 2))
            assertEquals("すべて選択済み", HomeTextCopy.browseStudySelectionSummary(2, 2))
            assertEquals("未選択", HomeTextCopy.browseStudySelectionSummary(0, 0))
            assertEquals("すべて選択", HomeTextCopy.browseSelectAllStudiedLabel())
            assertEquals("すべてクリア", HomeTextCopy.browseDeselectAllStudiedLabel())
            assertEquals("この漢字を学習対象にする", HomeTextCopy.browseStudiedToggleLabel("語"))
            assertEquals("ローカル漢字が見つかりません", HomeTextCopy.browseEmptyTitle())
            assertEquals("先にAnkiDroidを同期するか、別の検索を試してください。", HomeTextCopy.browseEmptyBody())
            assertEquals("漢字が見つかりません", HomeTextCopy.kanjiNotFoundTitle())
            assertEquals("ローカル記録が見つかりません。", HomeTextCopy.kanjiNotFoundBody())
            assertEquals("まだ意味は保存されていません", HomeTextCopy.browseItemMeaning(inventory("語", "", "")))
            assertEquals("language", HomeTextCopy.browseItemMeaning(inventory))
            assertEquals("ローカルソース1件 · 例文2件", HomeTextCopy.browseInventorySummary(1, 2))
            assertEquals("ローカルソース3件 · 例文1件", HomeTextCopy.browseInventorySummary(3, 1))
            assertEquals("停止中", HomeTextCopy.suspendedChipLabel())
            assertEquals("再学習", HomeTextCopy.relearningChipLabel())
            assertEquals("閲覧に戻る", HomeTextCopy.backToBrowseKanjiLabel())
            assertEquals("", HomeTextCopy.detailReasonTitle())
            assertEquals("非アクティブ; 復元履歴として保持。", HomeTextCopy.historicalReasonText())
            assertEquals("アクティブな練習エビデンス。", HomeTextCopy.activeReasonText(row))
            assertEquals("manual reason", HomeTextCopy.activeReasonText(rowWithReason))
            assertEquals("Anki検索: row:裂", HomeTextCopy.ankiBrowserLine("row:裂"))
            assertEquals("今すぐ復習", HomeTextCopy.reviewNowLabel())
            assertEquals("検索をコピー", HomeTextCopy.copyAnkiSearchLabel())
            assertEquals("Anki検索", HomeTextCopy.ankiSearchClipLabel())
            assertEquals("検索をコピーしました", HomeTextCopy.ankiSearchCopiedToast())
            assertEquals("ローカルで停止", HomeTextCopy.localSuspendButtonLabel(false))
            assertEquals("ローカル停止を解除", HomeTextCopy.localSuspendButtonLabel(true))
            assertEquals("ローカルで停止しました。", HomeTextCopy.localSuspendToast(false))
            assertEquals("停止を解除しました。", HomeTextCopy.localSuspendToast(true))
            assertEquals("例文", HomeTextCopy.examplesTitle())
            assertEquals("ローカル記録", HomeTextCopy.localInventoryTitle())
            assertEquals("ソース1件 · 例文2件", HomeTextCopy.localInventorySummary(1, 2))
            assertEquals("ソース3件 · 例文1件", HomeTextCopy.localInventorySummary(3, 1))
            assertEquals("Anki検索: row:裂", HomeTextCopy.localInventorySearchLine("row:裂"))
            assertEquals(
                "最終確認 ${DateTextPolicy.shortDateTime(123456789L)}",
                HomeTextCopy.localInventoryLastSeenLine(123456789L)
            )
            assertEquals("復元履歴", HomeTextCopy.inventoryTitle(null))
            assertEquals("language", HomeTextCopy.inventoryTitle(inventory))
            assertEquals("裂", HomeTextCopy.detailDisplayKanji("fallback", row, inventory))
            assertEquals("語", HomeTextCopy.detailDisplayKanji("fallback", null, inventory))
            assertEquals("fallback", HomeTextCopy.detailDisplayKanji("fallback", null, null))
            assertEquals("inventory:語", HomeTextCopy.detailBrowserSearch(row, inventory))
            assertEquals("row:裂", HomeTextCopy.detailBrowserSearch(row, inventory("語", "language", "")))
            assertEquals("", HomeTextCopy.detailBrowserSearch(row("裂", ""), null))
            assertEquals("成熟サポート 0/2", HomeTextCopy.matureSupportTargetText(0, 2))
            assertEquals("成熟サポート 3/4", HomeTextCopy.matureSupportTargetText(3, 4))
            assertEquals("同期または復習後にタイムラインが表示されます。", HomeTextCopy.timelineEmptyText())
            assertEquals("復元タイムライン", HomeTextCopy.recoveryTimelineTitle())
            assertEquals("アクティブな Anki エビデンスはありません。", HomeTextCopy.noActiveEvidenceText())
        }
    }

    @Test
    fun detailIdentityHelpersPreserveFallbackPriority() {
        val inventory = inventory("語", "language", "inventory:語")
        val row = row("裂", "row:裂")
        val rowWithReason = row("裂", "row:裂", "manual reason")

        assertEquals(
            listOf(
                "裂",
                "語",
                "fallback",
                "Historical recovery",
                "Historical recovery",
                "language",
                "Inactive; kept in recovery history.",
                "Active practice evidence.",
                "manual reason"
            ),
            listOf(
                HomeTextCopy.detailDisplayKanji("fallback", row, inventory),
                HomeTextCopy.detailDisplayKanji("fallback", null, inventory),
                HomeTextCopy.detailDisplayKanji("fallback", null, null),
                HomeTextCopy.inventoryTitle(null),
                HomeTextCopy.inventoryTitle(inventory("語", "", "")),
                HomeTextCopy.inventoryTitle(inventory),
                HomeTextCopy.historicalReasonText(),
                HomeTextCopy.activeReasonText(row),
                HomeTextCopy.activeReasonText(rowWithReason)
            )
        )
        assertEquals(
            listOf(
                "Anki search: row:裂",
                "Review now",
                "Copy search",
                "Anki search",
                "Search copied",
                "Suspend locally",
                "Unsuspend locally",
                "Suspended locally.",
                "Unsuspended.",
                "Examples",
                "Local records",
                "1 source · 2 examples",
                "3 sources · 1 example",
                "Anki search: row:裂",
                "Last seen ${DateTextPolicy.shortDateTime(123456789L)}"
            ),
            listOf(
                HomeTextCopy.ankiBrowserLine("row:裂"),
                HomeTextCopy.reviewNowLabel(),
                HomeTextCopy.copyAnkiSearchLabel(),
                HomeTextCopy.ankiSearchClipLabel(),
                HomeTextCopy.ankiSearchCopiedToast(),
                HomeTextCopy.localSuspendButtonLabel(false),
                HomeTextCopy.localSuspendButtonLabel(true),
                HomeTextCopy.localSuspendToast(false),
                HomeTextCopy.localSuspendToast(true),
                HomeTextCopy.examplesTitle(),
                HomeTextCopy.localInventoryTitle(),
                HomeTextCopy.localInventorySummary(1, 2),
                HomeTextCopy.localInventorySummary(3, 1),
                HomeTextCopy.localInventorySearchLine("row:裂"),
                HomeTextCopy.localInventoryLastSeenLine(123456789L)
            )
        )
        assertEquals(
            listOf(
                "inventory:語",
                "row:裂",
                "",
                "Mature support 0/2",
                "Mature support 3/4",
                "Timeline appears after sync or review.",
                "Recovery timeline",
                "No active Anki evidence."
            ),
            listOf(
                HomeTextCopy.detailBrowserSearch(row, inventory),
                HomeTextCopy.detailBrowserSearch(row, inventory("語", "language", "")),
                HomeTextCopy.detailBrowserSearch(row("裂", ""), null),
                HomeTextCopy.matureSupportTargetText(0, 2),
                HomeTextCopy.matureSupportTargetText(3, 4),
                HomeTextCopy.timelineEmptyText(),
                HomeTextCopy.recoveryTimelineTitle(),
                HomeTextCopy.noActiveEvidenceText()
            )
        )
    }

    @Test
    fun exampleCopyPreservesSourceExpressionAndMeaningCleanup() {
        val active = example("active", "活動語", "カツドウゴ", "(suru verb) action")
        val noReading = example("suspended", "停止語", "", "")

        assertEquals("ACTIVE", HomeTextCopy.exampleSourceLabel(active))
        assertEquals("SUSPENDED", HomeTextCopy.exampleSourceLabel(noReading))
        assertEquals("活動語  カツドウゴ", HomeTextCopy.exampleExpressionLine(active))
        assertEquals("停止語", HomeTextCopy.exampleExpressionLine(noReading))
        assertEquals("Action", HomeTextCopy.exampleMeaningLine(active))
        assertEquals("", HomeTextCopy.exampleMeaningLine(noReading))
        assertThrows(NullPointerException::class.java) { HomeTextCopy.exampleSourceLabel(null) }
        assertThrows(NullPointerException::class.java) { HomeTextCopy.exampleExpressionLine(null) }
        assertThrows(NullPointerException::class.java) { HomeTextCopy.exampleMeaningLine(null) }
    }

    private fun inventory(kanji: String, meaning: String, browserSearch: String): RecordsImportModels.KanjiInventoryItem {
        return RecordsImportModels.KanjiInventoryItem(kanji, meaning, "reading", browserSearch, 2, 3, false, 1000L)
    }

    private fun row(kanji: String, browserSearch: String): RecordsImportModels.DashboardRow {
        return row(kanji, browserSearch, "")
    }

    private fun row(kanji: String, browserSearch: String, reasonText: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            900,
            "meaning",
            "reading",
            browserSearch,
            1,
            "reason",
            reasonText,
            1,
            0,
            1,
            emptyList<RecordsImportModels.Example>()
        )
    }

    private fun example(
        sourceType: String,
        expression: String,
        reading: String,
        meaning: String
    ): RecordsImportModels.Example {
        return RecordsImportModels.Example(
            sourceType,
            1L,
            2L,
            expression,
            reading,
            meaning,
            "sentence",
            false,
            0,
            0,
            0,
            null,
            null,
            null
        )
    }

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val original = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(original)
        }
    }

    private inline fun <T> withTimeZone(timeZone: TimeZone, block: () -> T): T {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(timeZone)
        return try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
