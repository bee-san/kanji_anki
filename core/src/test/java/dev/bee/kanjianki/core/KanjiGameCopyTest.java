package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public final class KanjiGameCopyTest {
    @Test
    public void modeBodyPreservesGameModeCardCopy() {
        assertEquals("Needs more local kanji data.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.MEANING_POP, false));
        assertEquals("Pick meanings for kanji from your focus list.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.MEANING_POP, true));
        assertEquals("Pick readings from your source words.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.READING_RUSH, true));
        assertEquals("Choose between visually similar kanji.", KanjiGameCopy.modeBody(KanjiGameEngine.GameMode.CONFUSABLE_CLASH, true));
        assertEquals("Pick meanings for kanji from your focus list.", KanjiGameCopy.modeBody(null, true));
    }

    @Test
    public void choiceLabelKeepsConfusableKanjiLargeAndCompactsOtherModes() {
        KanjiGameEngine.GameQuestion confusable = question(KanjiGameEngine.GameMode.CONFUSABLE_CLASH, "拉");
        KanjiGameEngine.GameQuestion meaning = question(KanjiGameEngine.GameMode.MEANING_POP, "a very long answer choice that needs to be shortened for the button layout");

        assertEquals("拉", KanjiGameCopy.choiceLabel(confusable, "拉"));
        assertEquals("a very long answer choice that needs to be shortened...", KanjiGameCopy.choiceLabel(meaning, meaning.correctAnswer));
        assertEquals("", KanjiGameCopy.choiceLabel(meaning, null));
    }

    @Test
    public void resultAndSummaryCopyMatchGameRoundBehavior() {
        assertEquals("Round complete", KanjiGameCopy.resultTitle(true, false));
        assertEquals("Correct", KanjiGameCopy.resultTitle(false, true));
        assertEquals("Not quite", KanjiGameCopy.resultTitle(false, false));
        assertEquals("Final score: 7/10", KanjiGameCopy.finalScoreText(7, 10));
        assertEquals("Accuracy: 70%", KanjiGameCopy.accuracyText(7, 10));
        assertEquals("Accuracy: 0%", KanjiGameCopy.accuracyText(7, 0));
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
