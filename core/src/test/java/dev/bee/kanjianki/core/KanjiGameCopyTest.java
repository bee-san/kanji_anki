package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class KanjiGameCopyTest {
    @Test
    public void modeBodyPreservesGameModeCardCopy() {
        assertEquals("Needs more local kanji data.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.MEANING_POP, false));
        assertEquals("Pick meanings for kanji from your focus list.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.MEANING_POP, true));
        assertEquals("Pick readings from your source words.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.READING_RUSH, true));
        assertEquals("Choose between visually similar kanji.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.CONFUSABLE_CLASH, true));
        assertEquals("Needs more local kanji data.", KanjiGameCopy.modeBody(null, false));
        assertThrows(NullPointerException.class, () -> KanjiGameCopy.modeBody(null, true));
    }

    @Test
    public void choiceLabelKeepsConfusableKanjiLargeAndCompactsOtherModes() {
        KanjiGameEngine.GameQuestion confusable = question(KanjiGameEngine.GameMode.CONFUSABLE_CLASH, "拉");
        KanjiGameEngine.GameQuestion meaning = question(KanjiGameEngine.GameMode.MEANING_POP, "a very long answer choice that needs to be shortened for the button layout");

        assertEquals("拉", KanjiGameCopy.choiceLabel(confusable, "拉"));
        assertNull(KanjiGameCopy.choiceLabel(confusable, null));
        assertEquals("a very long answer choice that needs to be shortened...", KanjiGameCopy.choiceLabel(meaning, meaning.correctAnswer));
        assertEquals("", KanjiGameCopy.choiceLabel(meaning, null));
        assertThrows(NullPointerException.class, () -> KanjiGameCopy.choiceLabel(null, "拉"));
    }

    @Test
    public void presentationSizingMatchesGameModeLayoutRules() {
        KanjiGameEngine.GameQuestion meaning = question(KanjiGameEngine.GameMode.MEANING_POP, "pull");
        KanjiGameEngine.GameQuestion shortReading = question(KanjiGameEngine.GameMode.READING_RUSH, "ひく");
        KanjiGameEngine.GameQuestion longReading = new KanjiGameEngine.GameQuestion(
                KanjiGameEngine.GameMode.READING_RUSH,
                "引",
                "長いプロンプト",
                "Pick the reading",
                "ひく",
                Arrays.asList("ひく", "other"),
                "引 = pull"
        );
        KanjiGameEngine.GameQuestion confusable = question(KanjiGameEngine.GameMode.CONFUSABLE_CLASH, "拉");

        assertEquals(52, KanjiGameCopy.promptTextSizeSp(meaning));
        assertEquals(38, KanjiGameCopy.promptTextSizeSp(shortReading));
        assertEquals(25, KanjiGameCopy.promptTextSizeSp(longReading));
        assertEquals(32, KanjiGameCopy.choiceTextSizeSp(confusable));
        assertEquals(15, KanjiGameCopy.choiceTextSizeSp(meaning));
        assertTrue(KanjiGameCopy.choiceUsesKanjiTypography(confusable));
        assertFalse(KanjiGameCopy.choiceUsesKanjiTypography(meaning));
        assertThrows(NullPointerException.class, () -> KanjiGameCopy.promptTextSizeSp(null));
        assertThrows(NullPointerException.class, () -> KanjiGameCopy.choiceTextSizeSp(null));
        assertThrows(NullPointerException.class, () -> KanjiGameCopy.choiceUsesKanjiTypography(null));
    }

    @Test
    public void resultAndSummaryCopyMatchGameRoundBehavior() {
        assertEquals("Round complete", KanjiGameCopy.resultTitle(true, false));
        assertEquals("Correct", KanjiGameCopy.resultTitle(false, true));
        assertEquals("Not quite", KanjiGameCopy.resultTitle(false, false));
        assertEquals("Correct answer: pull", KanjiGameCopy.answerText("pull"));
        assertEquals("Your answer: push", KanjiGameCopy.selectedAnswerText("push"));
        assertEquals("Score: 7/10", KanjiGameCopy.finalScoreText(7, 10));
        assertEquals("Accuracy: 70%", KanjiGameCopy.accuracyText(7, 10));
        assertEquals("Accuracy: 0%", KanjiGameCopy.accuracyText(7, 0));
    }

    @Test
    public void screenConstantsPreserveMainActivityGamesCopy() {
        assertEquals("Games", KanjiGameCopy.LABEL_GAMES);
        assertEquals("Next", KanjiGameCopy.LABEL_NEXT);
        assertEquals("Round complete", KanjiGameCopy.LABEL_ROUND_COMPLETE);
        assertEquals("New round", KanjiGameCopy.LABEL_NEW_ROUND);
        assertEquals("Sync AnkiDroid", KanjiGameCopy.LABEL_SYNC_ANKIDROID);
        assertEquals("Start", KanjiGameCopy.LABEL_PLAY);
        assertEquals("Needs data", KanjiGameCopy.LABEL_LOCKED);
        assertEquals("Round", KanjiGameCopy.LABEL_ROUND);
        assertEquals("Score", KanjiGameCopy.LABEL_SCORE);
        assertEquals("Streak", KanjiGameCopy.LABEL_STREAK);
        assertEquals("Practice kanji without changing SRS.", KanjiGameCopy.GAMES_SUBTITLE);
        assertEquals("No kanji games yet", KanjiGameCopy.EMPTY_NO_KANJI_TITLE);
        assertEquals("Sync AnkiDroid first so Kani can build practice games from your own cards.", KanjiGameCopy.EMPTY_NO_KANJI_BODY);
        assertEquals("Game not ready", KanjiGameCopy.GAME_NOT_READY_TITLE);
        assertEquals("This game needs at least two usable choices from your local kanji data.", KanjiGameCopy.GAME_NOT_READY_BODY);
    }

    private static KanjiGameEngine.GameQuestion question(KanjiGameEngine.GameMode mode, String correctAnswer) {
        return new KanjiGameEngine.GameQuestion(
                mode,
                "拉",
                "拉",
                "Pick the meaning",
                correctAnswer,
                Arrays.asList(correctAnswer, "other"),
                "拉 = pull"
        );
    }
}
