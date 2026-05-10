package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class SimilarKanjiChoicePlanner {
    public List<Records.SimilarKanjiChoiceCard> buildCandidates(
            List<Records.KanjiInventoryItem> inventory,
            List<Records.SimilarKanjiPair> pairs
    ) {
        Map<String, Records.KanjiInventoryItem> inventoryByKanji = inventoryByKanji(inventory);
        if (inventoryByKanji.size() < 2) {
            return Collections.emptyList();
        }

        Map<String, Set<String>> directNeighbors = directNeighbors(pairs, inventoryByKanji);
        List<Records.SimilarKanjiChoiceCard> out = new ArrayList<>();
        for (Records.KanjiInventoryItem target : inventoryByKanji.values()) {
            Records.SimilarKanjiChoiceCard card = choiceCard(target, directNeighbors);
            if (card != null) {
                out.add(card);
            }
        }
        return out;
    }

    private static Map<String, Records.KanjiInventoryItem> inventoryByKanji(List<Records.KanjiInventoryItem> inventory) {
        Map<String, Records.KanjiInventoryItem> inventoryByKanji = new TreeMap<>();
        if (inventory != null) {
            for (Records.KanjiInventoryItem item : inventory) {
                if (item != null && !item.kanji.isEmpty()) {
                    inventoryByKanji.put(item.kanji, item);
                }
            }
        }
        return inventoryByKanji;
    }

    private static Map<String, Set<String>> directNeighbors(
            List<Records.SimilarKanjiPair> pairs,
            Map<String, Records.KanjiInventoryItem> inventoryByKanji
    ) {
        Map<String, Set<String>> directNeighbors = new TreeMap<>();
        if (pairs != null) {
            for (Records.SimilarKanjiPair pair : pairs) {
                if (validPair(pair, inventoryByKanji)) {
                    directNeighbors.computeIfAbsent(pair.kanjiA, ignored -> new TreeSet<>()).add(pair.kanjiB);
                    directNeighbors.computeIfAbsent(pair.kanjiB, ignored -> new TreeSet<>()).add(pair.kanjiA);
                }
            }
        }
        return directNeighbors;
    }

    private static boolean validPair(Records.SimilarKanjiPair pair, Map<String, Records.KanjiInventoryItem> inventoryByKanji) {
        return pair != null
                && !pair.kanjiA.isEmpty()
                && !pair.kanjiB.isEmpty()
                && !pair.kanjiA.equals(pair.kanjiB)
                && inventoryByKanji.containsKey(pair.kanjiA)
                && inventoryByKanji.containsKey(pair.kanjiB);
    }

    private static Records.SimilarKanjiChoiceCard choiceCard(
            Records.KanjiInventoryItem target,
            Map<String, Set<String>> directNeighbors
    ) {
        String meaning = target.primaryMeaning.trim();
        Set<String> neighbors = directNeighbors.get(target.kanji);
        if (meaning.isEmpty() || neighbors == null || neighbors.isEmpty()) {
            return null;
        }
        TreeSet<String> choices = new TreeSet<>();
        choices.add(target.kanji);
        choices.addAll(neighbors);
        if (choices.size() < 2) {
            return null;
        }
        List<String> choiceList = new ArrayList<>(choices);
        return new Records.SimilarKanjiChoiceCard(
                target.kanji,
                meaning,
                choiceList,
                choiceSignature(choiceList)
        );
    }

    public Records.SimilarKanjiChoiceResult evaluateSelection(
            Records.SimilarKanjiChoiceCard card,
            String selectedKanji
    ) {
        if (card == null) {
            return new Records.SimilarKanjiChoiceResult(null, selectedKanji, false, Collections.emptyList());
        }
        String selected = selectedKanji == null ? "" : selectedKanji.trim();
        boolean correct = card.targetKanji.equals(selected);
        if (correct) {
            return new Records.SimilarKanjiChoiceResult(card, selected, true, Collections.emptyList());
        }
        LinkedHashSet<String> repairs = new LinkedHashSet<>();
        repairs.add(card.targetKanji);
        if (card.choices.contains(selected) && !selected.equals(card.targetKanji)) {
            repairs.add(selected);
        }
        return new Records.SimilarKanjiChoiceResult(card, selected, false, new ArrayList<>(repairs));
    }

    public static String choiceSignature(List<String> choices) {
        TreeSet<String> sorted = new TreeSet<>();
        if (choices != null) {
            for (String choice : choices) {
                if (choice != null && !choice.trim().isEmpty()) {
                    sorted.add(choice.trim());
                }
            }
        }
        return String.join("\t", sorted);
    }
}
