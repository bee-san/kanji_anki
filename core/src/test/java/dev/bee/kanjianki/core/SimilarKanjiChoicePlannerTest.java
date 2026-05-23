package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
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
        List<RecordsImportModels.KanjiInventoryItem> inventory = Arrays.asList(
                item("拉", "pull"),
                item("提", "carry"),
                item("謎", "riddle"),
                item("麺", "")
        );
        List<RecordsImportModels.SimilarKanjiPair> pairs = Arrays.asList(
                        pair("拉", "提"),
                        pair("拉", "謎"),
                        pair("提", "外"),
                        pair("麺", "提"),
                        null,
                        pair("拉", "拉"),
                        pair("", "提")
        );

        List<RecordsImportModels.SimilarKanjiChoiceCard> cards = planner.buildCandidates(inventory, pairs);

        RecordsImportModels.SimilarKanjiChoiceCard pull = find(cards, "拉");
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
        RecordsImportModels.SimilarKanjiChoiceCard card = new RecordsImportModels.SimilarKanjiChoiceCard(
                "拉",
                "pull",
                Arrays.asList("拉", "提", "謎", "麺"),
                "拉\t提\t謎\t麺"
        );

        RecordsImportModels.SimilarKanjiChoiceResult wrong = planner.evaluateSelection(card, "謎");
        RecordsImportModels.SimilarKanjiChoiceResult correct = planner.evaluateSelection(card, "拉");
        RecordsImportModels.SimilarKanjiChoiceResult outsideChoice = planner.evaluateSelection(card, "外");

        assertFalse(wrong.correct);
        assertEquals(Arrays.asList("拉", "謎"), wrong.repairKanji);
        assertTrue(correct.correct);
        assertTrue(correct.repairKanji.isEmpty());
        assertEquals(Collections.singletonList("拉"), outsideChoice.repairKanji);
    }

    @Test
    public void nullCardAndChoiceSignatureHandleSparseValues() {
        SimilarKanjiChoicePlanner planner = new SimilarKanjiChoicePlanner();

        RecordsImportModels.SimilarKanjiChoiceResult nullCard = planner.evaluateSelection(null, " 拉 ");
        RecordsImportModels.SimilarKanjiChoiceResult nullSelection = planner.evaluateSelection(
                new RecordsImportModels.SimilarKanjiChoiceCard("拉", "pull", Arrays.asList("拉", "提"), "拉\t提"),
                null
        );

        assertFalse(nullCard.correct);
        assertEquals(" 拉 ", nullCard.selectedKanji);
        assertTrue(nullCard.repairKanji.isEmpty());
        assertEquals("", nullSelection.selectedKanji);
        assertEquals(Collections.singletonList("拉"), nullSelection.repairKanji);
        assertEquals("拉\t謎", SimilarKanjiChoicePlanner.choiceSignature(Arrays.asList(" 謎 ", null, "", "拉", "謎")));
        assertEquals("", SimilarKanjiChoicePlanner.choiceSignature(null));
    }

    @Test
    public void fallbackChoicesKeepTargetAndFirstThreeNeighborsInStoreOrder() {
        List<String> choices = SimilarKanjiChoicePlanner.fallbackChoices(
                "裂",
                Arrays.asList(
                        pair("裂", "列"),
                        pair("裂", "烈"),
                        pair("劣", "裂"),
                        pair("裂", "例"),
                        pair("裂", "列")
                )
        );

        assertEquals(Arrays.asList("裂", "列", "烈", "劣"), choices);
    }

    @Test
    public void fallbackChoicesAllowMissingPairs() {
        assertEquals(Collections.singletonList("裂"), SimilarKanjiChoicePlanner.fallbackChoices("裂", null));
    }

    @Test
    public void choiceCardForSessionPrefersStoredDueCard() {
        RecordsImportModels.SimilarKanjiChoiceCard stored = new RecordsImportModels.SimilarKanjiChoiceCard(
                "裂",
                "stored meaning",
                Arrays.asList("裂", "列"),
                "stored-signature"
        );

        RecordsImportModels.SimilarKanjiChoiceCard card = SimilarKanjiChoicePlanner.choiceCardForSession(
                stored,
                "謎",
                "fallback meaning",
                Collections.singletonList(pair("謎", "迷"))
        );

        assertSame(stored, card);
    }

    @Test
    public void choiceCardForSessionBuildsFallbackCardFromPairsAndMeaning() {
        RecordsImportModels.SimilarKanjiChoiceCard card = SimilarKanjiChoicePlanner.choiceCardForSession(
                null,
                "裂",
                "split",
                Arrays.asList(
                        pair("裂", "列"),
                        pair("裂", "烈"),
                        pair("劣", "裂"),
                        pair("裂", "例"),
                        pair("裂", "戻")
                )
        );

        assertEquals("裂", card.targetKanji);
        assertEquals("split", card.primaryMeaning);
        assertEquals(Arrays.asList("裂", "列", "烈", "劣"), card.choices);
        assertEquals("列\t劣\t烈\t裂", card.choiceSignature);
    }

    @Test
    public void sparsePairsAndMissingNeighborsAreSkipped() {
        SimilarKanjiChoicePlanner planner = new SimilarKanjiChoicePlanner();
        List<RecordsImportModels.KanjiInventoryItem> inventory = Arrays.asList(
                item("拉", "pull"),
                item("提", "carry"),
                item("謎", "riddle")
        );

        assertTrue(planner.buildCandidates(inventory, null).isEmpty());
        assertTrue(planner.buildCandidates(inventory, Arrays.asList(
                pair("拉", ""),
                pair("", "提"),
                pair("拉", "外"),
                pair("外", "提")
        )).isEmpty());
    }

    private static RecordsImportModels.KanjiInventoryItem item(String kanji, String meaning) {
        return new RecordsImportModels.KanjiInventoryItem(kanji, meaning, "", "", 1, 1, false, 0L);
    }

    private static RecordsImportModels.SimilarKanjiPair pair(String first, String second) {
        return new RecordsImportModels.SimilarKanjiPair(first, second, "fixture", 0L, 0L);
    }

    private static RecordsImportModels.SimilarKanjiChoiceCard find(List<RecordsImportModels.SimilarKanjiChoiceCard> cards, String target) {
        for (RecordsImportModels.SimilarKanjiChoiceCard card : cards) {
            if (target.equals(card.targetKanji)) {
                return card;
            }
        }
        throw new AssertionError("No card for " + target + " in " + Collections.singletonList(cards.size()));
    }

    private static boolean hasTarget(List<RecordsImportModels.SimilarKanjiChoiceCard> cards, String target) {
        for (RecordsImportModels.SimilarKanjiChoiceCard card : cards) {
            if (target.equals(card.targetKanji)) {
                return true;
            }
        }
        return false;
    }
}
