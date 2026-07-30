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
        val example = when {
            session == null -> null
            session.taskType == StudyTaskTypes.TYPE_READING -> StudyExampleSelector.exampleForSession(session)
            else -> StudyExampleSelector.wordReadingExample(session.row)
        }
        if (example != null && example.expression.isNotEmpty()) {
            return example.expression
        }
        return session?.item?.kanji ?: ""
    }

    /**
     * The sentence_reading front (Goal 80): the mined sentence for the card, or
     * the plain expression / kanji when no sentence example exists.
     */
    @JvmStatic
    fun sentencePrompt(session: RecordsSchedulerModels.StudySession?): String {
        val example = if (session == null) null else StudyExampleSelector.sentenceReadingExample(session.row)
        if (example != null && example.sentence.isNotEmpty()) {
            return example.sentence
        }
        return wordPrompt(session)
    }

    /** The target word shown beneath the sentence on the sentence_reading card. */
    @JvmStatic
    fun sentenceReadingWord(session: RecordsSchedulerModels.StudySession?): String {
        val example = if (session == null) null else StudyExampleSelector.sentenceReadingExample(session.row)
        if (example != null && example.expression.isNotEmpty()) {
            return example.expression
        }
        return session?.item?.kanji ?: ""
    }

    @JvmStatic
    fun heroQuestion(session: RecordsSchedulerModels.StudySession?): String {
        if (session != null && StudyTaskTypes.SENTENCE_READING == session.taskType) {
            return localizedText("How is the word read here?", "この語はどう読む？")
        }
        if (session != null &&
            (StudyTaskTypes.WORD_READING == session.taskType || StudyTaskTypes.TYPE_READING == session.taskType)
        ) {
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
    fun collectionReadingForSession(session: RecordsSchedulerModels.StudySession?): String {
        return StudyExampleSelector.exampleForSession(session)?.reading.orEmpty()
    }

    @JvmStatic
    fun readingLabel(): String = localizedText("Reading", "読み")

    @JvmStatic
    fun recognitionFailureTitle(): String = localizedText("What went wrong?", "どこで困りましたか？")

    @JvmStatic
    fun recognitionFailureBody(): String = localizedText(
        "Choose the closest cause so Kani can give one targeted repair.",
        "最も近い原因を選ぶと、Kaniが適切な修復練習を出します。",
    )

    @JvmStatic
    fun recognitionFailureMeaningChoice(): String = localizedText("I didn't know the meaning", "意味が分からなかった")

    @JvmStatic
    fun recognitionFailureVisualChoice(): String = localizedText("I mixed up the kanji", "漢字を見間違えた")

    @JvmStatic
    fun openInBrowseLabel(): String = localizedText("Open in Browse", "Browseで開く")

    @JvmStatic
    fun answerLabel(): String = localizedText("Answer", "答え")

    @JvmStatic
    fun referenceLabel(): String = localizedText("Reference", "お手本")

    @JvmStatic
    fun studyMnemonicLabel(): String = localizedText("My mnemonic", "自分の覚え方")

    @JvmStatic
    fun writingReferenceHelper(): String = localizedText(
        "Trace it below, then check.",
        "下になぞってから確認してください。",
    )

    @JvmStatic
    fun moreAboutKanjiLabel(kanji: String): String = localizedText(
        "More about $kanji",
        "${kanji}についてもっと見る",
    )

    @JvmStatic
    fun showAllLabel(count: Int): String = localizedText("Show all $count", "${count}件すべて表示")

    @JvmStatic
    fun showFewerLabel(): String = localizedText("Show fewer", "一部だけ表示")

    @JvmStatic
    fun currentLabel(): String = localizedText("Current", "現在")

    @JvmStatic
    fun expandedStateDescription(): String = localizedText("Expanded", "展開済み")

    @JvmStatic
    fun collapsedStateDescription(): String = localizedText("Collapsed", "折りたたみ済み")

    @JvmStatic
    fun shellContentDescription(route: String): String = localizedText("Kani shell $route", "Kani画面 $route")

    @JvmStatic
    fun routeContentDescription(route: String, scrollPositionLabel: String?): String {
        val scrollLabel = scrollPositionLabel?.takeIf { it.isNotBlank() }
        return if (isJapaneseLocale()) {
            buildString {
                append("Kaniルート ").append(route)
                scrollLabel?.let { append(" スクロール位置 ").append(it) }
            }
        } else {
            buildString {
                append("Kani route ").append(route)
                scrollLabel?.let { append(" scroll ").append(it) }
            }
        }
    }

    @JvmStatic
    fun studyAnswerDetailsLabel(): String = localizedText("Details", "詳細")

    @JvmStatic
    fun studyAnswerBreakdownLabel(): String = localizedText("Breakdown", "構成")

    @JvmStatic
    fun studyAnswerStrokeOrderLabel(): String = localizedText("Stroke order", "筆順")

    @JvmStatic
    fun studyAnswerUsedInAnkiLabel(): String = localizedText("Used in Anki", "Ankiでの使用例")

    @JvmStatic
    fun studyAnswerWhyThisCardLabel(): String = localizedText("Why this card?", "このカードが出た理由")

    @JvmStatic
    fun studyAnswerDetailsEmptyTitle(): String = localizedText(
        "Kani couldn't find local details for this kanji yet.",
        "この漢字のローカル詳細はまだ見つかりません。",
    )

    @JvmStatic
    fun studyAnswerDetailsEmptyBody(): String = localizedText(
        "Review still works; this drawer can fill in after dictionary data syncs.",
        "レビューは続けられます。辞書データを同期すると、ここに詳細が表示されます。",
    )

    @JvmStatic
    fun studyAnswerBreakdownEmptyTitle(): String = localizedText(
        "No radical or component data yet.",
        "部首や構成要素のデータはまだありません。",
    )

    @JvmStatic
    fun studyAnswerBreakdownEmptyBody(): String = localizedText(
        "Component breakdown is still molting. Radical data is shown for now.",
        "構成要素の内訳は準備中です。今は部首データのみ表示します。",
    )

    @JvmStatic
    fun studyAnswerRadicalOnlySummary(): String = localizedText("Radical only", "部首のみ")

    @JvmStatic
    fun studyAnswerStrokeOrderEmptyTitle(): String = localizedText(
        "Stroke data is not available for this kanji yet.",
        "この漢字の筆順データはまだ利用できません。",
    )

    @JvmStatic
    fun studyAnswerStrokeOrderEmptyBody(): String = localizedText(
        "Stroke-order animation needs a licensed offline asset before Kani can draw it here.",
        "筆順アニメーションを表示するには、ライセンス済みのオフライン素材が必要です。",
    )

    @JvmStatic
    fun studyAnswerUsedInAnkiEmptyTitle(): String = localizedText(
        "No other synced Anki words yet.",
        "同期済みのAnki単語はほかにありません。",
    )

    @JvmStatic
    fun studyAnswerUsedInAnkiEmptyBody(): String = localizedText(
        "Sync more cards and Kani will connect them here.",
        "さらにカードを同期すると、ここに関連する単語が表示されます。",
    )

    @JvmStatic
    fun studyAnswerWhyThisCardEmptyBody(): String = localizedText(
        "This card came from your synced study queue.",
        "このカードは同期済みの学習キューから選ばれました。",
    )

    @JvmStatic
    fun studyAnswerAnkiNoteIdCopiedMessage(): String = localizedText(
        "Anki link unavailable — copied note ID.",
        "Ankiリンクを利用できないため、ノートIDをコピーしました。",
    )

    @JvmStatic
    fun studyAnswerAnkiCardIdCopiedMessage(): String = localizedText(
        "Anki link unavailable — copied card ID.",
        "Ankiリンクを利用できないため、カードIDをコピーしました。",
    )

    @JvmStatic
    fun studyAnswerLocalDictionarySummary(): String = localizedText("Local dictionary", "ローカル辞書")

    @JvmStatic
    fun studyAnswerOnReadingLabel(): String = localizedText("On", "音読み")

    @JvmStatic
    fun studyAnswerKunReadingLabel(): String = localizedText("Kun", "訓読み")

    @JvmStatic
    fun studyAnswerNanoriReadingLabel(): String = localizedText("Nanori", "名乗り")

    @JvmStatic
    fun studyAnswerRadicalAndComponentsSummary(): String = localizedText("Radical + components", "部首＋構成要素")

    @JvmStatic
    fun studyAnswerStrokeCountSummary(count: Int): String = localizedText("$count strokes", "${count}画")

    @JvmStatic
    fun studyAnswerAnimatedGuideReadySummary(): String = localizedText(
        "Animated guide ready",
        "アニメーションガイドを利用できます",
    )

    @JvmStatic
    fun studyAnswerNoSyncedWordsSummary(): String = localizedText("No synced words", "同期済みの単語なし")

    @JvmStatic
    fun studyAnswerFromSummary(expression: String): String = localizedText("From: $expression", "出典：$expression")

    @JvmStatic
    fun studyAnswerRadicalSummary(radicalNumber: Int): String = localizedText(
        "radical $radicalNumber",
        "部首 $radicalNumber",
    )

    @JvmStatic
    fun studyAnswerSyncedWordsSummary(count: Int): String {
        if (isJapaneseLocale()) {
            return "${count}件の同期済み単語"
        }
        return countText(count, "synced word", "synced words")
    }

    @JvmStatic
    fun studyAnswerMeaningsHeading(): String = localizedText("Meanings", "意味")

    @JvmStatic
    fun studyAnswerStrokesLabel(): String = localizedText("Strokes", "画数")

    @JvmStatic
    fun studyAnswerNotAvailableValue(): String = localizedText("Not available", "利用不可")

    @JvmStatic
    fun studyAnswerGradeLabel(): String = localizedText("Grade", "学年")

    @JvmStatic
    fun studyAnswerNotGradedValue(): String = localizedText("Not graded", "学年指定なし")

    @JvmStatic
    fun studyAnswerRadicalLabel(): String = localizedText("Radical", "部首")

    @JvmStatic
    fun studyAnswerFrequencyLabel(): String = localizedText("Frequency", "頻度")

    @JvmStatic
    fun studyAnswerJitenRankLabel(): String = localizedText("Jiten rank", "Jiten順位")

    @JvmStatic
    fun studyAnswerComponentsHeading(): String = localizedText("Components", "構成要素")

    @JvmStatic
    fun studyAnswerStrokeCountLabel(): String = localizedText("Stroke count", "画数")

    @JvmStatic
    fun studyAnswerPlannedAssetNote(reference: String): String = localizedText("Planned: $reference", "予定：$reference")

    @JvmStatic
    fun studyAnswerReadingNote(reading: String): String = localizedText("Reading: $reading", "読み：$reading")

    @JvmStatic
    fun studyAnswerAlsoAppearsInHeading(): String = localizedText("Also appears in...", "ほかの用例")

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
    fun answerCorrectFeedback(): String = localizedText("Correct.", "正解です。")

    @JvmStatic
    fun answerIncorrectFeedback(): String = localizedText("Incorrect.", "不正解です。")

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
    fun kanjiReadingChoiceQuestion(card: RecordsImportModels.KanjiReadingChoiceCard?): String {
        val kanji = card?.targetKanji ?: ""
        val word = card?.word ?: ""
        return if (isJapaneseLocale()) {
            "「$word」の $kanji の読みは？"
        } else {
            "How is $kanji read in $word?"
        }
    }

    @JvmStatic
    fun kanjiReadingChoiceResult(card: RecordsImportModels.KanjiReadingChoiceCard?, correct: Boolean): String {
        val kanji = card?.targetKanji ?: ""
        val word = card?.word ?: ""
        val reading = card?.correctReading ?: ""
        if (correct) {
            return if (isJapaneseLocale()) {
                "正解。$word の $kanji は「$reading」と読みます。"
            } else {
                "Correct. $kanji is read $reading in $word."
            }
        }
        return if (isJapaneseLocale()) {
            "答え：$word の $kanji は「$reading」"
        } else {
            "Answer: $kanji is read $reading in $word"
        }
    }

    @JvmStatic
    fun readingKanjiChoiceQuestion(card: RecordsImportModels.ReadingKanjiChoiceCard?): String {
        val reading = card?.reading ?: ""
        val word = card?.blankedWord ?: ""
        val meaning = card?.meaning.orEmpty()
        val base = if (isJapaneseLocale()) {
            "「$reading」— $word はどの漢字？"
        } else {
            "$reading — which kanji is $word?"
        }
        if (meaning.isEmpty()) {
            return base
        }
        return if (isJapaneseLocale()) "$base（$meaning）" else "$base ($meaning)"
    }

    @JvmStatic
    fun readingKanjiChoiceResult(card: RecordsImportModels.ReadingKanjiChoiceCard?, correct: Boolean): String {
        val kanji = card?.targetKanji ?: ""
        val reading = card?.reading ?: ""
        if (correct) {
            return if (isJapaneseLocale()) "正解。$kanji は「$reading」です。" else "Correct. $kanji is read $reading."
        }
        return if (isJapaneseLocale()) "答え：$kanji ・ $reading" else "Answer: $kanji · $reading"
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
    fun typingReadingIncorrectToast(): String = localizedText(
        "That reading does not match.",
        "読みが一致しません。",
    )

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
        // Dictionary-first: meaning_kanji asks for the kanji's KANJIDIC gloss, not
        // the tested word's JMdict compound gloss (revert of PR #87's preference).
        // canonicalKanjiMeaning returns the KANJIDIC gloss when the kanji is in the
        // dictionary and falls back to the word gloss/prompt otherwise. The question
        // and both result branches share this helper, so they stay consistent.
        return canonicalKanjiMeaning(dictionaryLookup, card?.targetKanji, card?.primaryMeaning ?: prompt, maxChars)
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
