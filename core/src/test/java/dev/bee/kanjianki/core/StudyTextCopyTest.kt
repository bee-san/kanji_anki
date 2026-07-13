package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyTextCopyTest {
    @Test
    fun countAndCompactTextPreserveAppCopyHelpers() {
        assertEquals("1 item", StudyTextCopy.countText(1, "item", "items"))
        assertEquals("2 items", StudyTextCopy.countText(2, "item", "items"))
        assertEquals("1 null", StudyTextCopy.countText(1, null, "items"))
        assertEquals("2 null", StudyTextCopy.countText(2, "item", null))
        assertEquals("", StudyTextCopy.compact(null, 12))
        assertEquals("short", StudyTextCopy.compact("short", 12))
        assertEquals("a very long s...", StudyTextCopy.compact("a very long sentence that should be shortened", 16))
    }

    @Test
    fun rowMeaningAndCleanLearnerTextUseDictionaryMeaningCleanup() {
        assertEquals("Split", StudyTextCopy.rowMeaning(row("裂", "meaning: split", "fallback", emptyList())))
        assertEquals("Fallback", StudyTextCopy.rowMeaning(row("裂", "", "fallback", emptyList())))
        assertEquals("Collection clue", StudyTextCopy.rowMeaning(null))
        assertEquals("Quiet", StudyTextCopy.cleanLearnerText("(suru verb) quiet", "", 72))
    }

    @Test
    fun sessionCluePrefersDictionaryThenRowThenPrompt() {
        val lookup = DictionaryLookup.fromKanjiEntries(listOf(kanjiEntry("裂", "split", "tear")))
        val item = studyItem("裂")
        val row = row("裂", "row meaning", "reason", emptyList())

        assertEquals("Split, tear", StudyTextCopy.sessionClue(lookup, session(item, row, "fallback prompt")))
        assertEquals("Row meaning", StudyTextCopy.sessionClue(DictionaryLookup.empty(), session(item, row, "fallback prompt")))
        assertEquals("Fallback prompt", StudyTextCopy.sessionClue(DictionaryLookup.empty(), session(studyItem("?"), null, "fallback prompt")))
        assertEquals("Collection clue", StudyTextCopy.sessionClue(DictionaryLookup.empty(), session(studyItem("?"), null, "")))
        assertEquals("Collection clue", StudyTextCopy.sessionClue(DictionaryLookup.empty(), null))
    }

    @Test
    fun canonicalKanjiMeaningFallsBackWhenDictionaryHasNoGloss() {
        val lookup = DictionaryLookup.fromKanjiEntries(listOf(kanjiEntry("裂")))

        assertEquals("Fallback", StudyTextCopy.canonicalKanjiMeaning(DictionaryLookup.empty(), "?", "fallback", 40))
        assertEquals("Fallback", StudyTextCopy.canonicalKanjiMeaning(lookup, "裂", "fallback", 40))
        assertEquals("Fallback", StudyTextCopy.canonicalKanjiMeaning(null, "裂", "fallback", 40))
        assertEquals(
            "Very long meaning...",
            StudyTextCopy.canonicalKanjiMeaning(
                DictionaryLookup.fromKanjiEntries(listOf(kanjiEntry("長", "very long meaning that compacts"))),
                "長",
                "fallback",
                21,
            ),
        )
    }

    @Test
    fun wordPromptPrefersWordReadingExampleExpression() {
        val active = example("active", "活動語")
        val suspended = example("suspended", "休止語")
        val item = studyItem("語")

        assertEquals("休止語", StudyTextCopy.wordPrompt(session(item, row("語", "language", "reason", listOf(active, suspended)), "prompt")))
        assertEquals("活動語", StudyTextCopy.wordPrompt(session(item, row("語", "language", "reason", listOf(active)), "prompt")))
        assertEquals("語", StudyTextCopy.wordPrompt(session(item, row("語", "language", "reason", emptyList()), "prompt")))
        assertEquals("", StudyTextCopy.wordPrompt(null))
    }

    @Test
    fun heroQuestionUsesWordReadingTaskOnly() {
        assertEquals("What is the reading?", StudyTextCopy.heroQuestion(session(studyItem("語"), row("語", "language", "reason", emptyList()), "prompt", StudyTaskTypes.WORD_READING)))
        assertEquals("What does this kanji mean?", StudyTextCopy.heroQuestion(session(studyItem("語"), row("語", "language", "reason", emptyList()), "prompt", StudyTaskTypes.KANJI_MEANING)))
        assertEquals("What does this kanji mean?", StudyTextCopy.heroQuestion(null))
    }

    @Test
    fun heroQuestionUsesSentenceReadingTaskToo() {
        assertEquals(
            "How is the word read here?",
            StudyTextCopy.heroQuestion(session(studyItem("語"), row("語", "language", "reason", emptyList()), "prompt", StudyTaskTypes.SENTENCE_READING)),
        )
    }

    @Test
    fun heroQuestionTranslatesToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals(
                "読み方は？",
                StudyTextCopy.heroQuestion(
                    session(studyItem("語"), row("語", "language", "reason", emptyList()), "prompt", StudyTaskTypes.WORD_READING),
                ),
            )
            assertEquals(
                "この漢字の意味は？",
                StudyTextCopy.heroQuestion(
                    session(studyItem("語"), row("語", "language", "reason", emptyList()), "prompt", StudyTaskTypes.KANJI_MEANING),
                ),
            )
            assertEquals(
                "この語はどう読む？",
                StudyTextCopy.heroQuestion(
                    session(studyItem("語"), row("語", "language", "reason", emptyList()), "prompt", StudyTaskTypes.SENTENCE_READING),
                ),
            )
            assertEquals("この漢字の意味は？", StudyTextCopy.heroQuestion(null))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun sentencePromptPrefersSentenceReadingExampleThenFallsBackToWordPrompt() {
        val withSentence = exampleWithSentence("active", "活動語", "有効な文。")
        val withoutSentence = exampleWithSentence("suspended", "休止語", "")
        val item = studyItem("語")

        assertEquals(
            "有効な文。",
            StudyTextCopy.sentencePrompt(
                session(item, row("語", "language", "reason", listOf(withoutSentence, withSentence)), "prompt", StudyTaskTypes.SENTENCE_READING),
            ),
        )
        assertEquals(
            "休止語",
            StudyTextCopy.sentencePrompt(
                session(item, row("語", "language", "reason", listOf(withoutSentence)), "prompt", StudyTaskTypes.SENTENCE_READING),
            ),
        )
        assertEquals(
            "語",
            StudyTextCopy.sentencePrompt(
                session(item, row("語", "language", "reason", emptyList()), "prompt", StudyTaskTypes.SENTENCE_READING),
            ),
        )
        assertEquals("", StudyTextCopy.sentencePrompt(null))
    }

    @Test
    fun sentenceReadingWordShowsExpressionThenFallsBackToKanji() {
        val withSentence = exampleWithSentence("active", "活動語", "有効な文。")
        val item = studyItem("語")

        assertEquals(
            "活動語",
            StudyTextCopy.sentenceReadingWord(
                session(item, row("語", "language", "reason", listOf(withSentence)), "prompt", StudyTaskTypes.SENTENCE_READING),
            ),
        )
        assertEquals(
            "語",
            StudyTextCopy.sentenceReadingWord(
                session(item, row("語", "language", "reason", emptyList()), "prompt", StudyTaskTypes.SENTENCE_READING),
            ),
        )
        assertEquals("", StudyTextCopy.sentenceReadingWord(null))
    }

    @Test
    fun kanjiReadingChoiceCopyPreservesEnglishLabelsAndResultBranches() {
        val card = RecordsImportModels.KanjiReadingChoiceCard("脱", "脱出", "escape", "だつ", listOf("だつ", "しゅつ"))

        assertEquals("How is 脱 read in 脱出?", StudyTextCopy.kanjiReadingChoiceQuestion(card))
        assertEquals("Correct. 脱 is read だつ in 脱出.", StudyTextCopy.kanjiReadingChoiceResult(card, true))
        assertEquals("Answer: 脱 is read だつ in 脱出", StudyTextCopy.kanjiReadingChoiceResult(card, false))
        assertEquals("How is  read in ?", StudyTextCopy.kanjiReadingChoiceQuestion(null))
    }

    @Test
    fun kanjiReadingChoiceCopyTranslatesToJapaneseLocale() {
        val card = RecordsImportModels.KanjiReadingChoiceCard("脱", "脱出", "escape", "だつ", listOf("だつ", "しゅつ"))
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("「脱出」の 脱 の読みは？", StudyTextCopy.kanjiReadingChoiceQuestion(card))
            assertEquals("正解。脱出 の 脱 は「だつ」と読みます。", StudyTextCopy.kanjiReadingChoiceResult(card, true))
            assertEquals("答え：脱出 の 脱 は「だつ」", StudyTextCopy.kanjiReadingChoiceResult(card, false))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun readingKanjiChoiceCopyPreservesEnglishLabelsAndResultBranches() {
        val card = RecordsImportModels.ReadingKanjiChoiceCard("脱", "だつ", "〇出", "escape", listOf("脱", "出"))
        val noMeaning = RecordsImportModels.ReadingKanjiChoiceCard("脱", "だつ", "〇出", "", listOf("脱", "出"))

        assertEquals("だつ — which kanji is 〇出? (escape)", StudyTextCopy.readingKanjiChoiceQuestion(card))
        assertEquals("だつ — which kanji is 〇出?", StudyTextCopy.readingKanjiChoiceQuestion(noMeaning))
        assertEquals("Correct. 脱 is read だつ.", StudyTextCopy.readingKanjiChoiceResult(card, true))
        assertEquals("Answer: 脱 · だつ", StudyTextCopy.readingKanjiChoiceResult(card, false))
    }

    @Test
    fun readingKanjiChoiceCopyTranslatesToJapaneseLocale() {
        val card = RecordsImportModels.ReadingKanjiChoiceCard("脱", "だつ", "〇出", "escape", listOf("脱", "出"))
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("「だつ」— 〇出 はどの漢字？（escape）", StudyTextCopy.readingKanjiChoiceQuestion(card))
            assertEquals("正解。脱 は「だつ」です。", StudyTextCopy.readingKanjiChoiceResult(card, true))
            assertEquals("答え：脱 ・ だつ", StudyTextCopy.readingKanjiChoiceResult(card, false))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun collectionMeaningForSessionUsesSelectedExampleThenRowMeaning() {
        val active = example("active", "活動語", "active meaning")
        val suspended = example("suspended", "休止語", "suspended meaning")
        val item = studyItem("語")
        val row = row("語", "language", "reason", listOf(active, suspended))

        assertEquals("suspended meaning", StudyTextCopy.collectionMeaningForSession(session(item, row, "prompt", StudyTaskTypes.WORD_READING)))
        assertEquals("active meaning", StudyTextCopy.collectionMeaningForSession(session(item, row, "prompt", StudyTaskTypes.KANJI_MEANING)))
        assertEquals("language", StudyTextCopy.collectionMeaningForSession(session(item, row("語", "language", "reason", emptyList()), "prompt")))
        assertEquals("", StudyTextCopy.collectionMeaningForSession(null))
        assertEquals("", StudyTextCopy.collectionMeaningForSession(session(item, null, "prompt")))
    }

    @Test
    fun meaningKanjiChoiceCopyCleansLearnerMeaningAndPreservesResultBranches() {
        val card = RecordsImportModels.MeaningKanjiChoiceCard(
            "静",
            "(suru verb) quiet",
            "しず",
            listOf("静", "青", "清", "晴"),
        )

        assertEquals("Which kanji means Quiet?", StudyTextCopy.studyChoiceQuestion("Quiet"))
        assertEquals("Which kanji means Quiet?", StudyTextCopy.meaningKanjiChoiceQuestion(card, "fallback"))
        assertEquals("Correct. 静 means Quiet.", StudyTextCopy.meaningKanjiChoiceResult(card, "fallback", true))
        assertEquals("Answer: 静 · Quiet", StudyTextCopy.meaningKanjiChoiceResult(card, "fallback", false))
        assertEquals("Which kanji means Fallback clue?", StudyTextCopy.meaningKanjiChoiceQuestion(null, "fallback clue"))
        assertEquals("Typing answer accepted.", StudyTextCopy.typingAnswerAcceptedToast())
    }

    @Test
    fun meaningKanjiChoiceQuestionRemovesTrailingJapaneseExampleFromEnglishMeaning() {
        val card = RecordsImportModels.MeaningKanjiChoiceCard(
            "脱",
            "Escape getting away (from) getting out (of) 爺ちゃんはやっとのことで 脱出 した",
            "だつ",
            listOf("号", "脱", "別", "協"),
        )

        assertEquals(
            "Which kanji means Escape getting away (from) getting out (of)?",
            StudyTextCopy.meaningKanjiChoiceQuestion(card, "fallback"),
        )
    }

    @Test
    fun meaningKanjiChoiceCopyTranslatesToJapaneseLocale() {
        val card = RecordsImportModels.MeaningKanjiChoiceCard(
            "静",
            "(suru verb) quiet",
            "しず",
            listOf("静", "青", "清", "晴"),
        )
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("「Quiet」はどの漢字ですか？", StudyTextCopy.studyChoiceQuestion("Quiet"))
            assertEquals("「Quiet」はどの漢字ですか？", StudyTextCopy.meaningKanjiChoiceQuestion(card, "fallback"))
            assertEquals("正解。静 は「Quiet」です。", StudyTextCopy.meaningKanjiChoiceResult(card, "fallback", true))
            assertEquals("答え：静 ・ Quiet", StudyTextCopy.meaningKanjiChoiceResult(card, "fallback", false))
            assertEquals("入力した答えを保存しました。", StudyTextCopy.typingAnswerAcceptedToast())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun kanjiExplorationCopyPreservesEnglishLabels() {
        assertEquals("Open in Browse", StudyTextCopy.openInBrowseLabel())
        assertEquals("Explore the differences", StudyTextCopy.exploreDifferencesLabel())
        assertEquals("Explore the differences", StudyTextCopy.similarKanjiDifferencesTitle())
        assertEquals(
            "Compare the target kanji with the similar choices. Exact stroke or component claims only appear when Kani has reliable local data; otherwise use the shape hint as a safe visual fallback.",
            StudyTextCopy.similarKanjiDifferencesBody(),
        )
        assertEquals("Correct kanji", StudyTextCopy.similarKanjiCorrectLabel())
        assertEquals("Similar choices", StudyTextCopy.similarKanjiChoicesLabel())
        assertEquals("Kanji 拉", StudyTextCopy.similarKanjiChoiceLabel("拉"))
        assertEquals("Back to study", StudyTextCopy.backToStudyLabel())
    }

    @Test
    fun kanjiExplorationCopyTranslatesToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("Browseで開く", StudyTextCopy.openInBrowseLabel())
            assertEquals("違いを見比べる", StudyTextCopy.exploreDifferencesLabel())
            assertEquals("違いを見比べる", StudyTextCopy.similarKanjiDifferencesTitle())
            assertEquals(
                "正解の漢字と似ている選択肢を見比べます。Kaniが信頼できるローカルデータを持つ場合だけ画数や部品の違いを表示し、それ以外は形のヒントで安全に確認します。",
                StudyTextCopy.similarKanjiDifferencesBody(),
            )
            assertEquals("正解の漢字", StudyTextCopy.similarKanjiCorrectLabel())
            assertEquals("似ている選択肢", StudyTextCopy.similarKanjiChoicesLabel())
            assertEquals("拉", StudyTextCopy.similarKanjiChoiceLabel("拉"))
            assertEquals("学習に戻る", StudyTextCopy.backToStudyLabel())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun studyAnswerDetailsCopyPreservesEnglishLabelsAndSummaries() {
        withLocale(Locale.ENGLISH) {
            assertEquals("Details", StudyTextCopy.studyAnswerDetailsLabel())
            assertEquals("Breakdown", StudyTextCopy.studyAnswerBreakdownLabel())
            assertEquals("Stroke order", StudyTextCopy.studyAnswerStrokeOrderLabel())
            assertEquals("Used in Anki", StudyTextCopy.studyAnswerUsedInAnkiLabel())
            assertEquals("Why this card?", StudyTextCopy.studyAnswerWhyThisCardLabel())
            assertEquals(
                "Kani couldn't find local details for this kanji yet.",
                StudyTextCopy.studyAnswerDetailsEmptyTitle(),
            )
            assertEquals(
                "Review still works; this drawer can fill in after dictionary data syncs.",
                StudyTextCopy.studyAnswerDetailsEmptyBody(),
            )
            assertEquals("No radical or component data yet.", StudyTextCopy.studyAnswerBreakdownEmptyTitle())
            assertEquals(
                "Component breakdown is still molting. Radical data is shown for now.",
                StudyTextCopy.studyAnswerBreakdownEmptyBody(),
            )
            assertEquals("Radical only", StudyTextCopy.studyAnswerRadicalOnlySummary())
            assertEquals(
                "Stroke data is not available for this kanji yet.",
                StudyTextCopy.studyAnswerStrokeOrderEmptyTitle(),
            )
            assertEquals(
                "Stroke-order animation needs a licensed offline asset before Kani can draw it here.",
                StudyTextCopy.studyAnswerStrokeOrderEmptyBody(),
            )
            assertEquals("No other synced Anki words yet.", StudyTextCopy.studyAnswerUsedInAnkiEmptyTitle())
            assertEquals(
                "Sync more cards and Kani will connect them here.",
                StudyTextCopy.studyAnswerUsedInAnkiEmptyBody(),
            )
            assertEquals(
                "This card came from your synced study queue.",
                StudyTextCopy.studyAnswerWhyThisCardEmptyBody(),
            )
            assertEquals(
                "Anki link unavailable — copied note ID.",
                StudyTextCopy.studyAnswerAnkiNoteIdCopiedMessage(),
            )
            assertEquals(
                "Anki link unavailable — copied card ID.",
                StudyTextCopy.studyAnswerAnkiCardIdCopiedMessage(),
            )
            assertEquals("Local dictionary", StudyTextCopy.studyAnswerLocalDictionarySummary())
            assertEquals("On", StudyTextCopy.studyAnswerOnReadingLabel())
            assertEquals("Kun", StudyTextCopy.studyAnswerKunReadingLabel())
            assertEquals("Nanori", StudyTextCopy.studyAnswerNanoriReadingLabel())
            assertEquals("Radical + components", StudyTextCopy.studyAnswerRadicalAndComponentsSummary())
            assertEquals("8 strokes", StudyTextCopy.studyAnswerStrokeCountSummary(8))
            assertEquals("Animated guide ready", StudyTextCopy.studyAnswerAnimatedGuideReadySummary())
            assertEquals("No synced words", StudyTextCopy.studyAnswerNoSyncedWordsSummary())
            assertEquals("From: 抗議", StudyTextCopy.studyAnswerFromSummary("抗議"))
            assertEquals("radical 64", StudyTextCopy.studyAnswerRadicalSummary(64))
            assertEquals("1 synced word", StudyTextCopy.studyAnswerSyncedWordsSummary(1))
            assertEquals("4 synced words", StudyTextCopy.studyAnswerSyncedWordsSummary(4))
            assertEquals("Meanings", StudyTextCopy.studyAnswerMeaningsHeading())
            assertEquals("Strokes", StudyTextCopy.studyAnswerStrokesLabel())
            assertEquals("Not available", StudyTextCopy.studyAnswerNotAvailableValue())
            assertEquals("Grade", StudyTextCopy.studyAnswerGradeLabel())
            assertEquals("Not graded", StudyTextCopy.studyAnswerNotGradedValue())
            assertEquals("Radical", StudyTextCopy.studyAnswerRadicalLabel())
            assertEquals("Frequency", StudyTextCopy.studyAnswerFrequencyLabel())
            assertEquals("Jiten rank", StudyTextCopy.studyAnswerJitenRankLabel())
            assertEquals("Components", StudyTextCopy.studyAnswerComponentsHeading())
            assertEquals("Stroke count", StudyTextCopy.studyAnswerStrokeCountLabel())
            assertEquals("Planned: kanji.svg", StudyTextCopy.studyAnswerPlannedAssetNote("kanji.svg"))
            assertEquals("Reading: こう", StudyTextCopy.studyAnswerReadingNote("こう"))
            assertEquals("Also appears in...", StudyTextCopy.studyAnswerAlsoAppearsInHeading())
        }
    }

    @Test
    fun studyAnswerDetailsCopyTranslatesLabelsAndSummariesToJapanese() {
        withLocale(Locale.JAPANESE) {
            assertEquals("詳細", StudyTextCopy.studyAnswerDetailsLabel())
            assertEquals("構成", StudyTextCopy.studyAnswerBreakdownLabel())
            assertEquals("筆順", StudyTextCopy.studyAnswerStrokeOrderLabel())
            assertEquals("Ankiでの使用例", StudyTextCopy.studyAnswerUsedInAnkiLabel())
            assertEquals("このカードが出た理由", StudyTextCopy.studyAnswerWhyThisCardLabel())
            assertEquals("この漢字のローカル詳細はまだ見つかりません。", StudyTextCopy.studyAnswerDetailsEmptyTitle())
            assertEquals(
                "レビューは続けられます。辞書データを同期すると、ここに詳細が表示されます。",
                StudyTextCopy.studyAnswerDetailsEmptyBody(),
            )
            assertEquals("部首や構成要素のデータはまだありません。", StudyTextCopy.studyAnswerBreakdownEmptyTitle())
            assertEquals(
                "構成要素の内訳は準備中です。今は部首データのみ表示します。",
                StudyTextCopy.studyAnswerBreakdownEmptyBody(),
            )
            assertEquals("部首のみ", StudyTextCopy.studyAnswerRadicalOnlySummary())
            assertEquals("この漢字の筆順データはまだ利用できません。", StudyTextCopy.studyAnswerStrokeOrderEmptyTitle())
            assertEquals(
                "筆順アニメーションを表示するには、ライセンス済みのオフライン素材が必要です。",
                StudyTextCopy.studyAnswerStrokeOrderEmptyBody(),
            )
            assertEquals("同期済みのAnki単語はほかにありません。", StudyTextCopy.studyAnswerUsedInAnkiEmptyTitle())
            assertEquals(
                "さらにカードを同期すると、ここに関連する単語が表示されます。",
                StudyTextCopy.studyAnswerUsedInAnkiEmptyBody(),
            )
            assertEquals(
                "このカードは同期済みの学習キューから選ばれました。",
                StudyTextCopy.studyAnswerWhyThisCardEmptyBody(),
            )
            assertEquals(
                "Ankiリンクを利用できないため、ノートIDをコピーしました。",
                StudyTextCopy.studyAnswerAnkiNoteIdCopiedMessage(),
            )
            assertEquals(
                "Ankiリンクを利用できないため、カードIDをコピーしました。",
                StudyTextCopy.studyAnswerAnkiCardIdCopiedMessage(),
            )
            assertEquals("ローカル辞書", StudyTextCopy.studyAnswerLocalDictionarySummary())
            assertEquals("音読み", StudyTextCopy.studyAnswerOnReadingLabel())
            assertEquals("訓読み", StudyTextCopy.studyAnswerKunReadingLabel())
            assertEquals("名乗り", StudyTextCopy.studyAnswerNanoriReadingLabel())
            assertEquals("部首＋構成要素", StudyTextCopy.studyAnswerRadicalAndComponentsSummary())
            assertEquals("8画", StudyTextCopy.studyAnswerStrokeCountSummary(8))
            assertEquals("アニメーションガイドを利用できます", StudyTextCopy.studyAnswerAnimatedGuideReadySummary())
            assertEquals("同期済みの単語なし", StudyTextCopy.studyAnswerNoSyncedWordsSummary())
            assertEquals("出典：抗議", StudyTextCopy.studyAnswerFromSummary("抗議"))
            assertEquals("部首 64", StudyTextCopy.studyAnswerRadicalSummary(64))
            assertEquals("1件の同期済み単語", StudyTextCopy.studyAnswerSyncedWordsSummary(1))
            assertEquals("4件の同期済み単語", StudyTextCopy.studyAnswerSyncedWordsSummary(4))
            assertEquals("意味", StudyTextCopy.studyAnswerMeaningsHeading())
            assertEquals("画数", StudyTextCopy.studyAnswerStrokesLabel())
            assertEquals("利用不可", StudyTextCopy.studyAnswerNotAvailableValue())
            assertEquals("学年", StudyTextCopy.studyAnswerGradeLabel())
            assertEquals("学年指定なし", StudyTextCopy.studyAnswerNotGradedValue())
            assertEquals("部首", StudyTextCopy.studyAnswerRadicalLabel())
            assertEquals("頻度", StudyTextCopy.studyAnswerFrequencyLabel())
            assertEquals("Jiten順位", StudyTextCopy.studyAnswerJitenRankLabel())
            assertEquals("構成要素", StudyTextCopy.studyAnswerComponentsHeading())
            assertEquals("画数", StudyTextCopy.studyAnswerStrokeCountLabel())
            assertEquals("予定：kanji.svg", StudyTextCopy.studyAnswerPlannedAssetNote("kanji.svg"))
            assertEquals("読み：こう", StudyTextCopy.studyAnswerReadingNote("こう"))
            assertEquals("ほかの用例", StudyTextCopy.studyAnswerAlsoAppearsInHeading())
        }
    }

    @Test
    fun meaningKanjiChoiceCopyUsesTestedCompoundMeaningOverIndividualKanjiGloss() {
        val lookup = DictionaryLookup.fromKanjiEntries(listOf(kanjiEntry("脱", "undress", "removing")))
        val card = RecordsImportModels.MeaningKanjiChoiceCard(
            "脱",
            "Loss of strength exhaustion weakness",
            "だつりょく",
            listOf("脱", "弱", "欠", "疲"),
        )

        assertEquals("Which kanji means Loss of strength exhaustion weakness?", StudyTextCopy.meaningKanjiChoiceQuestion(lookup, card, "fallback"))
        assertEquals("Correct. 脱 means Loss of strength exhaustion weakness.", StudyTextCopy.meaningKanjiChoiceResult(lookup, card, "fallback", true))
        assertEquals("Answer: 脱 · Loss of strength exhaustion weakness", StudyTextCopy.meaningKanjiChoiceResult(lookup, card, "fallback", false))
    }

    @Test
    fun studyDoneCopyPreservesFocusAndRunSummaryText() {
        assertEquals("Today's focus done", StudyTextCopy.studyDoneTitle())
        assertEquals("Practice", StudyTextCopy.practiceLabel())
        assertEquals("Back home", StudyTextCopy.backHomeLabel())
        assertEquals("Continue all kanji", StudyTextCopy.continueAllKanjiLabel())
        assertEquals("New cards", StudyTextCopy.newCardsLabel())
        assertEquals("Study", StudyTextCopy.studyLabel())
        assertEquals("Cancel", StudyTextCopy.cancelLabel())
        assertEquals("Nothing due now", StudyTextCopy.nothingDueTitle())
        assertEquals("All caught up", StudyTextCopy.allCaughtUpHeadline())
        assertEquals(
            "Your active kanji are resting. Sync for new cards, or come back when reviews are due.",
            StudyTextCopy.allCaughtUpBody(),
        )
        assertEquals("Study practice", StudyTextCopy.studyPracticeTitle())
        assertEquals("Nothing to study yet", StudyTextCopy.nothingToStudyHeadline())
        assertEquals("Sync AnkiDroid first.", StudyTextCopy.syncAnkiDroidFirstBody())
        assertEquals("Kanji not available", StudyTextCopy.kanjiNotAvailableHeadline())
        assertEquals("This kanji changed after sync.", StudyTextCopy.kanjiChangedAfterSyncBody())
        assertEquals("Study more new cards", StudyTextCopy.studyMoreNewCardsLabel())
        assertEquals("How many extra new cards?", StudyTextCopy.studyMoreNewCardsDialogMessage())
        assertEquals(
            "Keep going or stop here.",
            StudyTextCopy.adaptiveFocusDoneBody(),
        )
        assertEquals(
            "Keep going or stop here.",
            StudyTextCopy.studyRunDoneBody(),
        )
        assertEquals("Today's focus: 0 of 7 left", StudyTextCopy.adaptiveFocusDoneSummary(7))
        assertEquals("1 kanji moved forward this session", StudyTextCopy.movedForwardSummary(1))
        assertEquals("3 kanji moved forward this session", StudyTextCopy.movedForwardSummary(3))
        assertEquals("1 kanji was missed and will come back soon", StudyTextCopy.missedSummary(1))
        assertEquals("2 kanji were missed and will come back soon", StudyTextCopy.missedSummary(2))
        assertEquals("1 task completed", StudyTextCopy.completedTaskSummary(1))
        assertEquals("4 tasks completed", StudyTextCopy.completedTaskSummary(4))
    }

    @Test
    fun studyDoneCopyTranslatesToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("今日のフォーカス完了", StudyTextCopy.studyDoneTitle())
            assertEquals("練習", StudyTextCopy.practiceLabel())
            assertEquals("ホームに戻る", StudyTextCopy.backHomeLabel())
            assertEquals("すべての漢字を続ける", StudyTextCopy.continueAllKanjiLabel())
            assertEquals("新規カード", StudyTextCopy.newCardsLabel())
            assertEquals("学習", StudyTextCopy.studyLabel())
            assertEquals("キャンセル", StudyTextCopy.cancelLabel())
            assertEquals("今は期限のカードがありません", StudyTextCopy.nothingDueTitle())
            assertEquals("すべて完了", StudyTextCopy.allCaughtUpHeadline())
            assertEquals(
                "アクティブな漢字は休憩中です。新しいカードを同期するか、レビュー期限になったら戻ってきてください。",
                StudyTextCopy.allCaughtUpBody(),
            )
            assertEquals("学習練習", StudyTextCopy.studyPracticeTitle())
            assertEquals("まだ学習するカードがありません", StudyTextCopy.nothingToStudyHeadline())
            assertEquals("先にAnkiDroidを同期してください。", StudyTextCopy.syncAnkiDroidFirstBody())
            assertEquals("漢字を利用できません", StudyTextCopy.kanjiNotAvailableHeadline())
            assertEquals("この漢字は同期後に変更されました。", StudyTextCopy.kanjiChangedAfterSyncBody())
            assertEquals("新規カードを追加で学習", StudyTextCopy.studyMoreNewCardsLabel())
            assertEquals("追加する新規カードは何枚ですか？", StudyTextCopy.studyMoreNewCardsDialogMessage())
            assertEquals("続けても、ここで終えてもOKです。", StudyTextCopy.adaptiveFocusDoneBody())
            assertEquals("続けても、ここで終えてもOKです。", StudyTextCopy.studyRunDoneBody())
            assertEquals("今日のフォーカス：残り0 / 7", StudyTextCopy.adaptiveFocusDoneSummary(7))
            assertEquals("このセッションで1件の漢字が進みました", StudyTextCopy.movedForwardSummary(1))
            assertEquals("このセッションで3件の漢字が進みました", StudyTextCopy.movedForwardSummary(3))
            assertEquals("1件の漢字をミスしました。まもなく再出題されます", StudyTextCopy.missedSummary(1))
            assertEquals("2件の漢字をミスしました。まもなく再出題されます", StudyTextCopy.missedSummary(2))
            assertEquals("1件のタスクが完了しました", StudyTextCopy.completedTaskSummary(1))
            assertEquals("4件のタスクが完了しました", StudyTextCopy.completedTaskSummary(4))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun similarRepairPromptPreservesRepairCopyBranches() {
        assertEquals("You picked 提 — write 拉.", StudyTextCopy.similarRepairPrompt(repair("拉", "提", "pull")))
        assertEquals("Write 拉.", StudyTextCopy.similarRepairPrompt(repair("拉", "", "")))
        assertEquals("Repair saved.", StudyTextCopy.similarWritingRepairSavedToast(true))
        assertEquals("Saved. Try that repair again.", StudyTextCopy.similarWritingRepairSavedToast(false))
    }

    @Test
    fun similarRepairPromptTranslatesToJapaneseLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)

            assertEquals("提を選びました。拉を書いてください。", StudyTextCopy.similarRepairPrompt(repair("拉", "提", "pull")))
            assertEquals("拉を書いてください。", StudyTextCopy.similarRepairPrompt(repair("拉", "", "")))
            assertEquals("修復を保存しました。", StudyTextCopy.similarWritingRepairSavedToast(true))
            assertEquals("保存しました。もう一度練習しましょう。", StudyTextCopy.similarWritingRepairSavedToast(false))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun copyHelpersTolerateLegacyNullItemSessionSentinel() {
        val row = row("裂", "split", "reason", emptyList())
        val session = session(null, row, "fallback prompt")

        assertEquals("Split", StudyTextCopy.sessionClue(DictionaryLookup.empty(), session))
        assertEquals("", StudyTextCopy.wordPrompt(session))
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    private fun session(
        item: RecordsStudyModels.StudyItem?,
        row: RecordsImportModels.DashboardRow?,
        prompt: String,
    ): RecordsSchedulerModels.StudySession = session(item, row, prompt, StudyTaskTypes.KANJI_MEANING)

    private fun session(
        item: RecordsStudyModels.StudyItem?,
        row: RecordsImportModels.DashboardRow?,
        prompt: String,
        taskType: String,
    ): RecordsSchedulerModels.StudySession = RecordsSchedulerModels.StudySession(item, row, "token", taskType, false, prompt)

    private fun studyItem(kanji: String): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(kanji, "review", 0L, 1.0, 5.0, 1, 0, 0, 1, null, 0L)

    private fun row(
        kanji: String,
        meaning: String,
        reason: String,
        examples: List<RecordsImportModels.Example>,
    ): RecordsImportModels.DashboardRow =
        RecordsImportModels.DashboardRow(
            kanji,
            900,
            meaning,
            "reading",
            "search",
            1,
            reason,
            "reason text",
            1,
            0,
            1,
            examples,
        )

    private fun example(sourceType: String, expression: String): RecordsImportModels.Example =
        example(sourceType, expression, "meaning")

    private fun example(sourceType: String, expression: String, meaning: String): RecordsImportModels.Example =
        RecordsImportModels.Example(sourceType, 1L, 2L, expression, "reading", meaning, "sentence", false, 0)

    private fun exampleWithSentence(sourceType: String, expression: String, sentence: String): RecordsImportModels.Example =
        RecordsImportModels.Example(sourceType, 1L, 2L, expression, "reading", "meaning", sentence, false, 0)

    private fun repair(repairKanji: String, wrongSelection: String, promptMeaning: String): RecordsImportModels.SimilarKanjiWritingRepair =
        RecordsImportModels.SimilarKanjiWritingRepair(
            1L,
            repairKanji,
            repairKanji,
            "$repairKanji|$wrongSelection",
            wrongSelection,
            promptMeaning,
            "pending",
            0L,
            "",
            0,
            0L,
            0L,
            0L,
        )

    private fun kanjiEntry(literal: String, vararg meanings: String): DictionaryLookup.KanjiEntry =
        DictionaryLookup.KanjiEntry(
            DictionaryLookup.KanjiEntryFields(
                literal,
                meanings.asList(),
                emptyList(),
                emptyList(),
                emptyList(),
                0,
                0,
                0,
                0,
                null,
            ),
        )
}
