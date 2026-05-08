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
        Map<String, Records.KanjiInventoryItem> inventoryByKanji = new TreeMap<>();
        if (inventory != null) {
            for (Records.KanjiInventoryItem item : inventory) {
                if (item != null && !item.kanji.isEmpty()) {
                    inventoryByKanji.put(item.kanji, item);
                }
            }
        }
        if (inventoryByKanji.size() < 2) {
            return Collections.emptyList();
        }

        Map<String, Set<String>> directNeighbors = new TreeMap<>();
        if (pairs != null) {
            for (Records.SimilarKanjiPair pair : pairs) {
                if (pair == null
                        || pair.kanjiA.isEmpty()
                        || pair.kanjiB.isEmpty()
                        || pair.kanjiA.equals(pair.kanjiB)
                        || !inventoryByKanji.containsKey(pair.kanjiA)
                        || !inventoryByKanji.containsKey(pair.kanjiB)) {
                    continue;
                }
                directNeighbors.computeIfAbsent(pair.kanjiA, ignored -> new TreeSet<>()).add(pair.kanjiB);
                directNeighbors.computeIfAbsent(pair.kanjiB, ignored -> new TreeSet<>()).add(pair.kanjiA);
            }
        }

        List<Records.SimilarKanjiChoiceCard> out = new ArrayList<>();
        for (Records.KanjiInventoryItem target : inventoryByKanji.values()) {
            if (target.primaryMeaning.trim().isEmpty()) {
                continue;
            }
            Set<String> neighbors = directNeighbors.get(target.kanji);
            if (neighbors == null || neighbors.isEmpty()) {
                continue;
            }
            TreeSet<String> choices = new TreeSet<>();
            choices.add(target.kanji);
            choices.addAll(neighbors);
            if (choices.size() < 2) {
                continue;
            }
            List<String> choiceList = new ArrayList<>(choices);
            out.add(new Records.SimilarKanjiChoiceCard(
                    target.kanji,
                    target.primaryMeaning.trim(),
                    choiceList,
                    choiceSignature(choiceList)
            ));
        }
        return out;
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
