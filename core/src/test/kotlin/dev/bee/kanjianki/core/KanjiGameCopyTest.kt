package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays
import java.util.Locale

class KanjiGameCopyTest {
    @Test
    fun modeBodyPreservesGameModeCardCopy() {
        assertEquals("Needs more data.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.MEANING_POP, false))
        assertEquals("Pick meanings from your focus list.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.MEANING_POP, true))
        assertEquals("Pick readings from source words.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.READING_RUSH, true))
        assertEquals("Choose among similar kanji.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.CONFUSABLE_CLASH, true))
        assertEquals("Needs more data.", KanjiGameCopy.modeBody(null, false))
        assertThrows(NullPointerException::class.java) { KanjiGameCopy.modeBody(null, true) }
    }

    @Test
    fun choiceLabelKeepsConfusableKanjiLargeAndCompactsOtherModes() {
        val confusable = question(KanjiGameEngine.GameMode.CONFUSABLE_CLASH, "拉")
        val meaning = question(KanjiGameEngine.GameMode.MEANING_POP, "a very long answer choice that needs to be shortened for the button layout")

        assertEquals("拉", KanjiGameCopy.choiceLabel(confusable, "拉"))
        assertNull(KanjiGameCopy.choiceLabel(confusable, null))
        assertEquals("a very long answer choice that needs to be shortened...", KanjiGameCopy.choiceLabel(meaning, meaning.correctAnswer))
        assertEquals("", KanjiGameCopy.choiceLabel(meaning, null))
        assertThrows(NullPointerException::class.java) { KanjiGameCopy.choiceLabel(null, "拉") }
    }

    @Test
    fun presentationSizingMatchesGameModeLayoutRules() {
        val meaning = question(KanjiGameEngine.GameMode.MEANING_POP, "pull")
        val shortReading = question(KanjiGameEngine.GameMode.READING_RUSH, "ひく")
        val longReading = KanjiGameEngine.GameQuestion(
            KanjiGameEngine.GameMode.READING_RUSH,
            "引",
            "長いプロンプト",
            "Pick the reading",
            "ひく",
            Arrays.asList("ひく", "other"),
            "引 = pull"
        )
        val confusable = question(KanjiGameEngine.GameMode.CONFUSABLE_CLASH, "拉")

        assertEquals(52, KanjiGameCopy.promptTextSizeSp(meaning))
        assertEquals(38, KanjiGameCopy.promptTextSizeSp(shortReading))
        assertEquals(25, KanjiGameCopy.promptTextSizeSp(longReading))
        assertEquals(32, KanjiGameCopy.choiceTextSizeSp(confusable))
        assertEquals(15, KanjiGameCopy.choiceTextSizeSp(meaning))
        assertTrue(KanjiGameCopy.choiceUsesKanjiTypography(confusable))
        assertFalse(KanjiGameCopy.choiceUsesKanjiTypography(meaning))
        assertThrows(NullPointerException::class.java) { KanjiGameCopy.promptTextSizeSp(null) }
        assertThrows(NullPointerException::class.java) { KanjiGameCopy.choiceTextSizeSp(null) }
        assertThrows(NullPointerException::class.java) { KanjiGameCopy.choiceUsesKanjiTypography(null) }
    }

    @Test
    fun resultAndSummaryCopyMatchGameRoundBehavior() {
        assertEquals("Round complete", KanjiGameCopy.resultTitle(true, false))
        assertEquals("Correct", KanjiGameCopy.resultTitle(false, true))
        assertEquals("Not quite", KanjiGameCopy.resultTitle(false, false))
        assertEquals("Correct answer: pull", KanjiGameCopy.answerText("pull"))
        assertEquals("Your answer: push", KanjiGameCopy.selectedAnswerText("push"))
        assertEquals("Score: 7/10", KanjiGameCopy.finalScoreText(7, 10))
        assertEquals("Accuracy: 70%", KanjiGameCopy.accuracyText(7, 10))
        assertEquals("Accuracy: 0%", KanjiGameCopy.accuracyText(7, 0))
    }

    @Test
    fun screenConstantsPreserveMainActivityGamesCopy() {
        assertEquals("Games", KanjiGameCopy.LABEL_GAMES)
        assertEquals("Next", KanjiGameCopy.LABEL_NEXT)
        assertEquals("Round complete", KanjiGameCopy.LABEL_ROUND_COMPLETE)
        assertEquals("New round", KanjiGameCopy.LABEL_NEW_ROUND)
        assertEquals("Sync AnkiDroid", KanjiGameCopy.LABEL_SYNC_ANKIDROID)
        assertEquals("Start", KanjiGameCopy.LABEL_PLAY)
        assertEquals("Needs data", KanjiGameCopy.LABEL_LOCKED)
        assertEquals("Round", KanjiGameCopy.LABEL_ROUND)
        assertEquals("Score", KanjiGameCopy.LABEL_SCORE)
        assertEquals("Streak", KanjiGameCopy.LABEL_STREAK)
        assertEquals("Practice without changing reviews.", KanjiGameCopy.GAMES_SUBTITLE)
        assertEquals("No games yet", KanjiGameCopy.EMPTY_NO_KANJI_TITLE)
        assertEquals("Sync AnkiDroid to build games.", KanjiGameCopy.EMPTY_NO_KANJI_BODY)
        assertEquals("Needs more data", KanjiGameCopy.GAME_NOT_READY_TITLE)
        assertEquals("At least two choices needed.", KanjiGameCopy.GAME_NOT_READY_BODY)
    }

    @Test
    fun screenLabelFunctionsPreserveEnglishDefaults() {
        assertEquals("Games", KanjiGameCopy.gamesLabel())
        assertEquals("Next", KanjiGameCopy.nextLabel())
        assertEquals("Round complete", KanjiGameCopy.roundCompleteLabel())
        assertEquals("New round", KanjiGameCopy.newRoundLabel())
        assertEquals("Sync AnkiDroid", KanjiGameCopy.syncAnkiDroidLabel())
        assertEquals("Start", KanjiGameCopy.playLabel())
        assertEquals("Needs data", KanjiGameCopy.lockedLabel())
        assertEquals("Round", KanjiGameCopy.roundLabel())
        assertEquals("Score", KanjiGameCopy.scoreLabel())
        assertEquals("Streak", KanjiGameCopy.streakLabel())
        assertEquals("Practice without changing reviews.", KanjiGameCopy.gamesSubtitle())
        assertEquals("No games yet", KanjiGameCopy.emptyNoKanjiTitle())
        assertEquals("Sync AnkiDroid to build games.", KanjiGameCopy.emptyNoKanjiBody())
        assertEquals("Needs more data", KanjiGameCopy.gameNotReadyTitle())
        assertEquals("At least two choices needed.", KanjiGameCopy.gameNotReadyBody())
        assertEquals("Kanji -> meaning", KanjiGameCopy.modeLabel(KanjiGameEngine.GameMode.MEANING_POP))
        assertEquals("Word -> reading", KanjiGameCopy.modeLabel(KanjiGameEngine.GameMode.READING_RUSH))
        assertEquals("Meaning -> kanji", KanjiGameCopy.modeLabel(KanjiGameEngine.GameMode.CONFUSABLE_CLASH))
        assertEquals("Meaning Pop", KanjiGameCopy.modeTitle(KanjiGameEngine.GameMode.MEANING_POP))
        assertEquals("Reading Rush", KanjiGameCopy.modeTitle(KanjiGameEngine.GameMode.READING_RUSH))
        assertEquals("Confusable Clash", KanjiGameCopy.modeTitle(KanjiGameEngine.GameMode.CONFUSABLE_CLASH))
        assertEquals("Games mode card", KanjiGameCopy.modeCardAccessibilityPrefix())
        assertThrows(NullPointerException::class.java) { KanjiGameCopy.modeLabel(null) }
        assertThrows(NullPointerException::class.java) { KanjiGameCopy.modeTitle(null) }
    }

    @Test
    fun questionPromptCopyPreservesEnglishGameEngineOutput() {
        val meaning = KanjiGameEngine.GameQuestion(
            KanjiGameEngine.GameMode.MEANING_POP,
            "語",
            "語",
            "Pick the meaning",
            "language",
            listOf("language", "word"),
            "語 = language",
        )
        val reading = KanjiGameEngine.GameQuestion(
            KanjiGameEngine.GameMode.READING_RUSH,
            "語",
            "言語",
            "Pick the reading for 語",
            "げんご",
            listOf("げんご", "ことば"),
            "語 = げんご · language",
        )
        val confusable = KanjiGameEngine.GameQuestion(
            KanjiGameEngine.GameMode.CONFUSABLE_CLASH,
            "裂",
            "Which kanji means split?",
            "Watch the shape",
            "裂",
            listOf("裂", "提"),
            "裂 = split",
        )

        assertEquals("語", KanjiGameCopy.questionPrompt(meaning))
        assertEquals("Pick the meaning", KanjiGameCopy.questionPromptDetail(meaning))
        assertEquals("言語", KanjiGameCopy.questionPrompt(reading))
        assertEquals("Pick the reading for 語", KanjiGameCopy.questionPromptDetail(reading))
        assertEquals("Which kanji means split?", KanjiGameCopy.questionPrompt(confusable))
        assertEquals("Watch the shape", KanjiGameCopy.questionPromptDetail(confusable))
        assertThrows(NullPointerException::class.java) { KanjiGameCopy.questionPrompt(null) }
        assertThrows(NullPointerException::class.java) { KanjiGameCopy.questionPromptDetail(null) }
    }

    @Test
    fun gameScreenCopyTranslatesToJapaneseLocale() {
        withLocale(Locale.JAPAN) {
            assertEquals("ゲーム", KanjiGameCopy.gamesLabel())
            assertEquals("次へ", KanjiGameCopy.nextLabel())
            assertEquals("ラウンド完了", KanjiGameCopy.roundCompleteLabel())
            assertEquals("新しいラウンド", KanjiGameCopy.newRoundLabel())
            assertEquals("AnkiDroidを同期", KanjiGameCopy.syncAnkiDroidLabel())
            assertEquals("開始", KanjiGameCopy.playLabel())
            assertEquals("データ不足", KanjiGameCopy.lockedLabel())
            assertEquals("ラウンド", KanjiGameCopy.roundLabel())
            assertEquals("スコア", KanjiGameCopy.scoreLabel())
            assertEquals("連続", KanjiGameCopy.streakLabel())
            assertEquals("復習を変更せずに練習できます。", KanjiGameCopy.gamesSubtitle())
            assertEquals("まだゲームはありません", KanjiGameCopy.emptyNoKanjiTitle())
            assertEquals("AnkiDroidを同期してゲームを作成します。", KanjiGameCopy.emptyNoKanjiBody())
            assertEquals("もっとデータが必要です", KanjiGameCopy.gameNotReadyTitle())
            assertEquals("選択肢が2つ以上必要です。", KanjiGameCopy.gameNotReadyBody())
            assertEquals("漢字→意味", KanjiGameCopy.modeLabel(KanjiGameEngine.GameMode.MEANING_POP))
            assertEquals("単語→読み", KanjiGameCopy.modeLabel(KanjiGameEngine.GameMode.READING_RUSH))
            assertEquals("意味→漢字", KanjiGameCopy.modeLabel(KanjiGameEngine.GameMode.CONFUSABLE_CLASH))
            assertEquals("意味ポップ", KanjiGameCopy.modeTitle(KanjiGameEngine.GameMode.MEANING_POP))
            assertEquals("読みラッシュ", KanjiGameCopy.modeTitle(KanjiGameEngine.GameMode.READING_RUSH))
            assertEquals("似た漢字バトル", KanjiGameCopy.modeTitle(KanjiGameEngine.GameMode.CONFUSABLE_CLASH))
            assertEquals("ゲームモードカード", KanjiGameCopy.modeCardAccessibilityPrefix())
            assertEquals("もっとデータが必要です。", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.MEANING_POP, false))
            assertEquals("集中リストから意味を選びます。", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.MEANING_POP, true))
            assertEquals("出典の単語から読みを選びます。", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.READING_RUSH, true))
            assertEquals("似ている漢字から選びます。", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.CONFUSABLE_CLASH, true))
            assertEquals("拉", KanjiGameCopy.questionPrompt(question(KanjiGameEngine.GameMode.MEANING_POP, "language")))
            assertEquals("意味を選びます。", KanjiGameCopy.questionPromptDetail(question(KanjiGameEngine.GameMode.MEANING_POP, "language")))
            assertEquals(
                "語の読みを選びます。",
                KanjiGameCopy.questionPromptDetail(
                    KanjiGameEngine.GameQuestion(
                        KanjiGameEngine.GameMode.READING_RUSH,
                        "語",
                        "言語",
                        "Pick the reading for 語",
                        "げんご",
                        listOf("げんご", "ことば"),
                        "語 = げんご · language",
                    )
                )
            )
            assertEquals(
                "「split」を表す漢字は？",
                KanjiGameCopy.questionPrompt(
                    KanjiGameEngine.GameQuestion(
                        KanjiGameEngine.GameMode.CONFUSABLE_CLASH,
                        "裂",
                        "Which kanji means split?",
                        "Watch the shape",
                        "裂",
                        listOf("裂", "提"),
                        "裂 = split",
                    )
                )
            )
            assertEquals(
                "形を見比べます。",
                KanjiGameCopy.questionPromptDetail(
                    KanjiGameEngine.GameQuestion(
                        KanjiGameEngine.GameMode.CONFUSABLE_CLASH,
                        "裂",
                        "Which kanji means split?",
                        "Watch the shape",
                        "裂",
                        listOf("裂", "提"),
                        "裂 = split",
                    )
                )
            )
            assertEquals("ラウンド完了", KanjiGameCopy.resultTitle(true, false))
            assertEquals("正解", KanjiGameCopy.resultTitle(false, true))
            assertEquals("惜しい", KanjiGameCopy.resultTitle(false, false))
            assertEquals("正解: pull", KanjiGameCopy.answerText("pull"))
            assertEquals("あなたの答え: push", KanjiGameCopy.selectedAnswerText("push"))
            assertEquals("スコア: 7/10", KanjiGameCopy.finalScoreText(7, 10))
            assertEquals("正答率: 70%", KanjiGameCopy.accuracyText(7, 10))
            assertEquals("正答率: 0%", KanjiGameCopy.accuracyText(7, 0))
        }
    }

    @Test
    fun missSweepCopyAndScoreStripPreserveEnglishDefaults() {
        val missSweep = KanjiGameEngine.GameQuestion(
            KanjiGameEngine.GameMode.MISS_SWEEP,
            "裂",
            "裂",
            "Pick the meaning",
            "split",
            listOf("split", "present"),
            "裂 = split",
        )

        assertEquals("Recent misses", KanjiGameCopy.modeLabel(KanjiGameEngine.GameMode.MISS_SWEEP))
        assertEquals("Miss Sweep", KanjiGameCopy.modeTitle(KanjiGameEngine.GameMode.MISS_SWEEP))
        assertEquals("Drill kanji you missed recently.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.MISS_SWEEP, true))
        assertEquals("Needs more data.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.MISS_SWEEP, false))
        assertEquals("裂", KanjiGameCopy.questionPrompt(missSweep))
        assertEquals("Pick the meaning", KanjiGameCopy.questionPromptDetail(missSweep))
        assertEquals("3 correct of 5", KanjiGameCopy.scoreStripDescription(3, 5))
    }

    @Test
    fun missSweepCopyTranslatesToJapaneseLocale() {
        withLocale(Locale.JAPAN) {
            val missSweep = KanjiGameEngine.GameQuestion(
                KanjiGameEngine.GameMode.MISS_SWEEP,
                "裂",
                "裂",
                "Pick the meaning",
                "split",
                listOf("split", "present"),
                "裂 = split",
            )

            assertEquals("最近のミス", KanjiGameCopy.modeLabel(KanjiGameEngine.GameMode.MISS_SWEEP))
            assertEquals("ミス復習", KanjiGameCopy.modeTitle(KanjiGameEngine.GameMode.MISS_SWEEP))
            assertEquals("最近間違えた漢字を練習します。", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.MISS_SWEEP, true))
            assertEquals("裂", KanjiGameCopy.questionPrompt(missSweep))
            assertEquals("意味を選びます。", KanjiGameCopy.questionPromptDetail(missSweep))
            assertEquals("5問中3問正解", KanjiGameCopy.scoreStripDescription(3, 5))
        }
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

    private fun question(mode: KanjiGameEngine.GameMode, correctAnswer: String): KanjiGameEngine.GameQuestion {
        return KanjiGameEngine.GameQuestion(
            mode,
            "拉",
            "拉",
            "Pick the meaning",
            correctAnswer,
            listOf(correctAnswer, "other"),
            "拉 = pull"
        )
    }
}
