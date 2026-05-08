package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SimilarKanjiChoicePlannerTest {
    @Test
    public void emptyOrTinyInventoryProducesNoChoices() {
        SimilarKanjiChoicePlanner planner = new SimilarKanjiChoicePlanner();

        assertTrue(planner.buildCandidates(null, null).isEmpty());
        assertTrue(planner.buildCandidates(Arrays.asList(null, item("", "blank"), item("拉", "pull")), null).isEmpty());
    }

    @Test
    public void buildsDirectLocalChoicesAndSkipsMissingMeaningTargets() {
        SimilarKanjiChoicePlanner planner = new SimilarKanjiChoicePlanner();
        List<Records.KanjiInventoryItem> inventory = Arrays.asList(
                item("拉", "pull"),
                item("提", "carry"),
                item("謎", "riddle"),
                item("麺", "")
        );
        List<Records.SimilarKanjiPair> pairs = Arrays.asList(
                        pair("拉", "提"),
                        pair("拉", "謎"),
                        pair("提", "外"),
                        pair("麺", "提"),
                        null,
                        pair("拉", "拉"),
                        pair("", "提")
        );

        List<Records.SimilarKanjiChoiceCard> cards = planner.buildCandidates(inventory, pairs);

        Records.SimilarKanjiChoiceCard pull = find(cards, "拉");
        assertEquals("pull", pull.primaryMeaning);
        assertEquals(Arrays.asList("拉", "提", "謎"), pull.choices);
        assertEquals("拉\t提\t謎", pull.choiceSignature);
        assertEquals(Arrays.asList("拉", "提", "麺"), find(cards, "提").choices);
        assertEquals(Arrays.asList("拉", "謎"), find(cards, "謎").choices);
        assertFalse(hasTarget(cards, "麺"));
        assertFalse(hasTarget(cards, "外"));
    }

    @Test
    public void wrongSelectionQueuesOnlyTargetAndSelectedNeighbor() {
        SimilarKanjiChoicePlanner planner = new SimilarKanjiChoicePlanner();
        Records.SimilarKanjiChoiceCard card = new Records.SimilarKanjiChoiceCard(
                "拉",
                "pull",
                Arrays.asList("拉", "提", "謎", "麺"),
                "拉\t提\t謎\t麺"
        );

        Records.SimilarKanjiChoiceResult wrong = planner.evaluateSelection(card, "謎");
        Records.SimilarKanjiChoiceResult correct = planner.evaluateSelection(card, "拉");
        Records.SimilarKanjiChoiceResult outsideChoice = planner.evaluateSelection(card, "外");

        assertFalse(wrong.correct);
        assertEquals(Arrays.asList("拉", "謎"), wrong.repairKanji);
        assertTrue(correct.correct);
        assertTrue(correct.repairKanji.isEmpty());
        assertEquals(Collections.singletonList("拉"), outsideChoice.repairKanji);
    }

    @Test
    public void nullCardAndChoiceSignatureHandleSparseValues() {
        SimilarKanjiChoicePlanner planner = new SimilarKanjiChoicePlanner();

        Records.SimilarKanjiChoiceResult nullCard = planner.evaluateSelection(null, " 拉 ");

        assertFalse(nullCard.correct);
        assertEquals(" 拉 ", nullCard.selectedKanji);
        assertTrue(nullCard.repairKanji.isEmpty());
        assertEquals("拉\t謎", SimilarKanjiChoicePlanner.choiceSignature(Arrays.asList(" 謎 ", null, "", "拉", "謎")));
        assertEquals("", SimilarKanjiChoicePlanner.choiceSignature(null));
    }

    private static Records.KanjiInventoryItem item(String kanji, String meaning) {
        return new Records.KanjiInventoryItem(kanji, meaning, "", "", 1, 1, false, 0L);
    }

    private static Records.SimilarKanjiPair pair(String first, String second) {
        return new Records.SimilarKanjiPair(first, second, "fixture", 0L, 0L);
    }

    private static Records.SimilarKanjiChoiceCard find(List<Records.SimilarKanjiChoiceCard> cards, String target) {
        for (Records.SimilarKanjiChoiceCard card : cards) {
            if (target.equals(card.targetKanji)) {
                return card;
            }
        }
        throw new AssertionError("No card for " + target + " in " + Collections.singletonList(cards.size()));
    }

    private static boolean hasTarget(List<Records.SimilarKanjiChoiceCard> cards, String target) {
        for (Records.SimilarKanjiChoiceCard card : cards) {
            if (target.equals(card.targetKanji)) {
                return true;
            }
        }
        return false;
    }
}
