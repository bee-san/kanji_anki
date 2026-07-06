package dev.bee.kanjianki.core

import java.util.Locale

object StudyTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun countText(count: Int, singular: String?, plural: String?): String {
        return "$count " + if (count == 1) singular else plural
    }

    @JvmStatic
    fun rowMeaning(row: RecordsImportModels.DashboardRow?): String {
        return cleanLearnerText(row?.primaryMeaning, row?.reasonCode, 72)
    }

    @JvmStatic
    fun sessionClue(
        dictionaryLookup: DictionaryLookup?,
        session: RecordsSchedulerModels.StudySession?,
    ): String {
        val raw = sessionClueRawText(session)
        val kanji = session?.item?.kanji ?: ""
        return canonicalKanjiMeaning(dictionaryLookup, kanji, raw, 96)
    }

    @JvmStatic
    fun canonicalKanjiMeaning(
        dictionaryLookup: DictionaryLookup?,
        kanji: String?,
        fallback: String?,
        maxChars: Int,
    ): String {
        val lookup = dictionaryLookup ?: DictionaryLookup.empty()
        val entry = lookup.lookupKanji(kanji)
        if (entry != null) {
            val meaning = StudyCueFormatter.displayGlosses(entry.meanings, 2)
            if (meaning.isNotEmpty()) {
                return compact(meaning, maxChars)
            }
        }
        return cleanLearnerText(fallback, "Collection clue", maxChars)
    }

    @JvmStatic
    fun wordPrompt(session: RecordsSchedulerModels.StudySession?): String {
        val example = if (session == null) null else StudyExampleSelector.wordReadingExample(session.row)
        if (example != null && example.expression.isNotEmpty()) {
            return example.expression
        }
        return session?.item?.kanji ?: ""
    }

    @JvmStatic
    fun heroQuestion(session: RecordsSchedulerModels.StudySession?): String {
        if (session != null && StudyTaskTypes.WORD_READING == session.taskType) {
            return localizedText("What is the reading?", "読み方は？")
        }
        return localizedText("What does this kanji mean?", "この漢字の意味は？")
    }

    @JvmStatic
    fun collectionMeaningForSession(session: RecordsSchedulerModels.StudySession?): String {
        if (session?.row == null) {
            return ""
        }
        val example = StudyExampleSelector.exampleForSession(session)
        if (example != null && example.meaning.isNotEmpty()) {
            return example.meaning
        }
        return session.row.primaryMeaning
    }

    @JvmStatic
    fun studyChoiceTitle(): String = localizedText("Choose the kanji", "漢字を選ぶ")

    @JvmStatic
    fun studyChoiceBody(): String = localizedText("Pick the matching kanji.", "一致する漢字を選んでください。")

    @JvmStatic
    fun viewKanjiDetailsLabel(): String = localizedText("View kanji details", "漢字の詳細を見る")

    @JvmStatic
    fun openInBrowseLabel(): String = localizedText("Open in Browse", "Browseで開く")

    @JvmStatic
    fun exploreDifferencesLabel(): String = localizedText("Explore the differences", "違いを見比べる")

    @JvmStatic
    fun similarKanjiDifferencesTitle(): String = localizedText("Explore the differences", "違いを見比べる")

    @JvmStatic
    fun similarKanjiDifferencesBody(): String = localizedText(
        "Compare the target kanji with the similar choices. Exact stroke or component claims only appear when Kani has reliable local data; otherwise use the shape hint as a safe visual fallback.",
        "正解の漢字と似ている選択肢を見比べます。Kaniが信頼できるローカルデータを持つ場合だけ画数や部品の違いを表示し、それ以外は形のヒントで安全に確認します。"
    )

    @JvmStatic
    fun similarKanjiCorrectLabel(): String = localizedText("Correct kanji", "正解の漢字")

    @JvmStatic
    fun similarKanjiChoicesLabel(): String = localizedText("Similar choices", "似ている選択肢")

    @JvmStatic
    fun similarKanjiChoiceLabel(kanji: String): String = localizedText("Kanji $kanji", "$kanji")

    @JvmStatic
    fun backToStudyLabel(): String = localizedText("Back to study", "学習に戻る")

    @JvmStatic
    fun choiceCorrectStateDescription(): String = localizedText("Correct answer", "正解")

    @JvmStatic
    fun choiceIncorrectStateDescription(): String = localizedText("Incorrect answer", "不正解")

    @JvmStatic
    fun similarKanjiDetailsLabel(): String = localizedText("Details", "詳細")

    @JvmStatic
    fun similarKanjiHideDetailsLabel(): String = localizedText("Hide details", "詳細を隠す")

    @JvmStatic
    fun similarKanjiWrongChoiceResult(correctKanji: String): String {
        if (isJapaneseLocale()) {
            return "不正解。正解は $correctKanji です。"
        }
        return "Not quite — the correct kanji is $correctKanji."
    }

    @JvmStatic
    fun continueLabel(): String = localizedText("Continue", "次へ")

    @JvmStatic
    fun studyChoiceQuestion(meaning: String): String {
        return if (isJapaneseLocale()) {
            "「$meaning」はどの漢字ですか？"
        } else {
            "Which kanji means $meaning?"
        }
    }

    @JvmStatic
    fun meaningKanjiChoiceQuestion(card: RecordsImportModels.MeaningKanjiChoiceCard?, prompt: String?): String {
        return meaningKanjiChoiceQuestion(null, card, prompt)
    }

    @JvmStatic
    fun meaningKanjiChoiceQuestion(
        dictionaryLookup: DictionaryLookup?,
        card: RecordsImportModels.MeaningKanjiChoiceCard?,
        prompt: String?,
    ): String {
        val meaning = meaningKanjiChoiceMeaning(dictionaryLookup, card, prompt, 96)
        return studyChoiceQuestion(meaning)
    }

    @JvmStatic
    fun meaningKanjiChoiceResult(
        card: RecordsImportModels.MeaningKanjiChoiceCard?,
        prompt: String?,
        correct: Boolean,
    ): String {
        return meaningKanjiChoiceResult(null, card, prompt, correct)
    }

    @JvmStatic
    fun meaningKanjiChoiceResult(
        dictionaryLookup: DictionaryLookup?,
        card: RecordsImportModels.MeaningKanjiChoiceCard?,
        prompt: String?,
        correct: Boolean,
    ): String {
        val targetKanji = card?.targetKanji ?: ""
        val meaning = meaningKanjiChoiceMeaning(dictionaryLookup, card, prompt, 72)
        if (correct) {
            if (isJapaneseLocale()) {
                return "正解。$targetKanji は「$meaning」です。"
            }
            return "Correct. $targetKanji means $meaning."
        }
        if (isJapaneseLocale()) {
            return "答え：$targetKanji ・ $meaning"
        }
        return "Answer: $targetKanji \u00b7 $meaning"
    }

    @JvmStatic
    fun typingAnswerAcceptedToast(): String {
        return localizedText("Typing answer accepted.", "入力した答えを保存しました。")
    }

    @JvmStatic
    fun studyDoneTitle(): String {
        return localizedText("Today's focus done", "今日のフォーカス完了")
    }

    @JvmStatic
    fun practiceLabel(): String = localizedText("Practice", "練習")

    @JvmStatic
    fun backHomeLabel(): String = localizedText("Back home", "ホームに戻る")

    @JvmStatic
    fun closeStudyLabel(): String = localizedText("Close study", "学習を閉じる")

    @JvmStatic
    fun studyProgressDescription(): String = localizedText("Study progress", "学習進捗")

    @JvmStatic
    fun continueAllKanjiLabel(): String = localizedText("Continue all kanji", "すべての漢字を続ける")

    @JvmStatic
    fun newCardsLabel(): String = localizedText("New cards", "新規カード")

    @JvmStatic
    fun studyLabel(): String = localizedText("Study", "学習")

    @JvmStatic
    fun meaningLabel(): String = localizedText("Meaning", "意味")

    @JvmStatic
    fun passLabel(): String = localizedText("Pass", "合格")

    @JvmStatic
    fun failLabel(): String = localizedText("Fail", "不合格")

    @JvmStatic
    fun answerHiddenHint(): String = localizedText("Answer hidden until reveal", "答えを表示するまで非表示")

    @JvmStatic
    fun cancelLabel(): String = localizedText("Cancel", "キャンセル")

    @JvmStatic
    fun nothingDueTitle(): String = localizedText("Nothing due now", "今は期限のカードがありません")

    @JvmStatic
    fun allCaughtUpHeadline(): String = localizedText("All caught up", "すべて完了")

    @JvmStatic
    fun allCaughtUpBody(): String {
        return localizedText(
            "Your active kanji are resting. Sync for new cards, or come back when reviews are due.",
            "アクティブな漢字は休憩中です。新しいカードを同期するか、レビュー期限になったら戻ってきてください。",
        )
    }

    @JvmStatic
    fun studyPracticeTitle(): String = localizedText("Study practice", "学習練習")

    @JvmStatic
    fun nothingToStudyHeadline(): String = localizedText("Nothing to study yet", "まだ学習するカードがありません")

    @JvmStatic
    fun syncAnkiDroidFirstBody(): String = localizedText("Sync AnkiDroid first.", "先にAnkiDroidを同期してください。")

    @JvmStatic
    fun kanjiNotAvailableHeadline(): String = localizedText("Kanji not available", "漢字を利用できません")

    @JvmStatic
    fun kanjiChangedAfterSyncBody(): String = localizedText("This kanji changed after sync.", "この漢字は同期後に変更されました。")

    @JvmStatic
    fun studyMoreNewCardsLabel(): String = localizedText("Study more new cards", "新規カードを追加で学習")

    @JvmStatic
    fun studyMoreNewCardsDialogMessage(): String = localizedText("How many extra new cards?", "追加する新規カードは何枚ですか？")

    @JvmStatic
    fun adaptiveFocusDoneBody(): String {
        return localizedText("Keep going or stop here.", "続けても、ここで終えてもOKです。")
    }

    @JvmStatic
    fun studyRunDoneBody(): String {
        return localizedText("Keep going or stop here.", "続けても、ここで終えてもOKです。")
    }

    @JvmStatic
    fun adaptiveFocusDoneSummary(target: Int): String {
        if (isJapaneseLocale()) {
            return "今日のフォーカス：残り0 / $target"
        }
        return "Today's focus: 0 of $target left"
    }

    @JvmStatic
    fun movedForwardSummary(count: Int): String {
        if (isJapaneseLocale()) {
            return "このセッションで${count}件の漢字が進みました"
        }
        return countText(count, "kanji moved forward this session", "kanji moved forward this session")
    }

    @JvmStatic
    fun missedSummary(count: Int): String {
        if (isJapaneseLocale()) {
            return "${count}件の漢字をミスしました。まもなく再出題されます"
        }
        return countText(count, "kanji was missed and will come back soon", "kanji were missed and will come back soon")
    }

    @JvmStatic
    fun reviewUndoMessage(rating: String): String {
        val ratingLabel = localizedRatingLabel(rating)
        return localizedText("${ratingLabel} saved", "${ratingLabel}を保存しました")
    }

    private fun localizedRatingLabel(value: String): String {
        return when (StudyRatings.normalize(value)) {
            StudyRatings.AGAIN -> StudyReviewButtonCopy.againLabel()
            StudyRatings.HARD -> localizedText("Hard", "難しい")
            StudyRatings.GOOD -> StudyReviewButtonCopy.goodLabel()
            StudyRatings.EASY -> localizedText("Easy", "簡単")
            else -> StudyReviewButtonCopy.goodLabel()
        }
    }

    @JvmStatic
    fun taskCompletedSummary(count: Int): String {
        if (isJapaneseLocale()) {
            return "${count}件のタスクが完了しました"
        }
        return countText(count, "task completed", "tasks completed")
    }

    @JvmStatic
    fun completedTaskSummary(count: Int): String {
        return taskCompletedSummary(count)
    }

    @JvmStatic
    fun completedTaskBreakdownSummary(breakdown: StudySessionProgressTracker.CompletedTaskBreakdown): String {
        val parts = ArrayList<String>()
        addCompletedTaskBreakdownPart(
            parts,
            breakdown.writingChecks,
            "writing check",
            "writing checks",
            "書く練習",
        )
        addCompletedTaskBreakdownPart(
            parts,
            breakdown.similarKanjiChoices,
            "similar kanji choice",
            "similar kanji choices",
            "似た漢字",
        )
        addCompletedTaskBreakdownPart(
            parts,
            breakdown.similarKanjiRepairs,
            "similar kanji repair",
            "similar kanji repairs",
            "修復",
        )
        addCompletedTaskBreakdownPart(
            parts,
            breakdown.wordReadingReviews,
            "word reading review",
            "word reading reviews",
            "単語読み",
        )
        addCompletedTaskBreakdownPart(
            parts,
            breakdown.otherReviews,
            "other review",
            "other reviews",
            "その他",
        )
        if (parts.isEmpty()) {
            return completedTaskSummary(breakdown.total)
        }
        return completedTaskSummary(breakdown.total) + " — " + parts.joinToString(", ")
    }

    private fun addCompletedTaskBreakdownPart(
        parts: MutableList<String>,
        count: Int,
        singular: String,
        plural: String,
        japaneseLabel: String,
    ) {
        if (count <= 0) {
            return
        }
        if (isJapaneseLocale()) {
            parts.add("${count}件の$japaneseLabel")
        } else {
            parts.add(countText(count, singular, plural))
        }
    }

    @JvmStatic
    fun similarWritingRepairSavedToast(passed: Boolean): String {
        if (isJapaneseLocale()) {
            return if (passed) "修復を保存しました。" else "保存しました。もう一度練習しましょう。"
        }
        return if (passed) "Repair saved." else "Saved. Try that repair again."
    }

    @JvmStatic
    fun similarWritingRepairSkippedToast(): String = localizedText(
        "Repair skipped.",
        "修正をスキップしました。",
    )

    @JvmStatic
    fun similarRepairPrompt(repair: RecordsImportModels.SimilarKanjiWritingRepair): String {
        return buildString {
            if (repair.wrongSelection.isNotEmpty()) {
                if (isJapaneseLocale()) {
                    append(repair.wrongSelection).append("を選びました。")
                        .append(repair.repairKanji).append("を書いてください。")
                } else {
                    append("You picked ").append(repair.wrongSelection).append(" — write ")
                        .append(repair.repairKanji).append(".")
                }
            } else {
                if (isJapaneseLocale()) {
                    append(repair.repairKanji).append("を書いてください。")
                } else {
                    append("Write ").append(repair.repairKanji).append(".")
                }
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun studyReasonLine(
        similarRepairActive: Boolean,
        session: RecordsSchedulerModels.StudySession?,
        matureSupportThreshold: Int,
        nowMillis: Long,
    ): String {
        return ""
    }

    @JvmStatic
    fun cleanLearnerText(raw: String?, fallback: String?, maxChars: Int): String {
        return StudyCueFormatter.cleanFallbackMeaning(raw, fallback, maxChars)
    }

    @JvmStatic
    fun compact(value: String?, maxChars: Int): String {
        return StudyCueFormatter.compact(value, maxChars)
    }

    private fun meaningKanjiChoiceMeaning(
        dictionaryLookup: DictionaryLookup?,
        card: RecordsImportModels.MeaningKanjiChoiceCard?,
        prompt: String?,
        maxChars: Int,
    ): String {
        val testedMeaning = StudyCueFormatter.cleanMeaningText(card?.primaryMeaning ?: prompt)
        if (testedMeaning.isNotEmpty()) {
            return cleanLearnerText(testedMeaning, "", maxChars)
        }
        return canonicalKanjiMeaning(dictionaryLookup, card?.targetKanji, prompt, maxChars)
    }

    private fun sessionClueRawText(session: RecordsSchedulerModels.StudySession?): String? {
        if (session == null) {
            return ""
        }
        if (session.row == null || session.row.primaryMeaning.isEmpty()) {
            return session.prompt
        }
        return session.row.primaryMeaning
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
