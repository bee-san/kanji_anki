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
    public static final int FALLBACK_CHOICE_LIMIT = 4;

    public List<RecordsImportModels.SimilarKanjiChoiceCard> buildCandidates(
            List<RecordsImportModels.KanjiInventoryItem> inventory,
            List<RecordsImportModels.SimilarKanjiPair> pairs
    ) {
        Map<String, RecordsImportModels.KanjiInventoryItem> inventoryByKanji = inventoryByKanji(inventory);
        if (inventoryByKanji.size() < 2) {
            return Collections.emptyList();
        }

        Map<String, Set<String>> directNeighbors = directNeighbors(pairs, inventoryByKanji);
        List<RecordsImportModels.SimilarKanjiChoiceCard> out = new ArrayList<>();
        for (RecordsImportModels.KanjiInventoryItem target : inventoryByKanji.values()) {
            RecordsImportModels.SimilarKanjiChoiceCard card = choiceCard(target, directNeighbors);
            if (card != null) {
                out.add(card);
            }
        }
        return out;
    }

    private static Map<String, RecordsImportModels.KanjiInventoryItem> inventoryByKanji(List<RecordsImportModels.KanjiInventoryItem> inventory) {
        Map<String, RecordsImportModels.KanjiInventoryItem> inventoryByKanji = new TreeMap<>();
        if (inventory != null) {
            for (RecordsImportModels.KanjiInventoryItem item : inventory) {
                if (item != null && !item.kanji.isEmpty()) {
                    inventoryByKanji.put(item.kanji, item);
                }
            }
        }
        return inventoryByKanji;
    }

    private static Map<String, Set<String>> directNeighbors(
            List<RecordsImportModels.SimilarKanjiPair> pairs,
            Map<String, RecordsImportModels.KanjiInventoryItem> inventoryByKanji
    ) {
        Map<String, Set<String>> directNeighbors = new TreeMap<>();
        if (pairs != null) {
            for (RecordsImportModels.SimilarKanjiPair pair : pairs) {
                if (validPair(pair, inventoryByKanji)) {
                    directNeighbors.computeIfAbsent(pair.kanjiA, ignored -> new TreeSet<>()).add(pair.kanjiB);
                    directNeighbors.computeIfAbsent(pair.kanjiB, ignored -> new TreeSet<>()).add(pair.kanjiA);
                }
            }
        }
        return directNeighbors;
    }

    private static boolean validPair(RecordsImportModels.SimilarKanjiPair pair, Map<String, RecordsImportModels.KanjiInventoryItem> inventoryByKanji) {
        return pair != null
                && !pair.kanjiA.isEmpty()
                && !pair.kanjiB.isEmpty()
                && !pair.kanjiA.equals(pair.kanjiB)
                && inventoryByKanji.containsKey(pair.kanjiA)
                && inventoryByKanji.containsKey(pair.kanjiB);
    }

    private static RecordsImportModels.SimilarKanjiChoiceCard choiceCard(
            RecordsImportModels.KanjiInventoryItem target,
            Map<String, Set<String>> directNeighbors
    ) {
        String meaning = target.primaryMeaning.trim();
        Set<String> neighbors = directNeighbors.get(target.kanji);
        if (meaning.isEmpty() || neighbors == null) {
            return null;
        }
        TreeSet<String> choices = new TreeSet<>();
        choices.add(target.kanji);
        choices.addAll(neighbors);
        List<String> choiceList = new ArrayList<>(choices);
        return new RecordsImportModels.SimilarKanjiChoiceCard(
                target.kanji,
                meaning,
                choiceList,
                choiceSignature(choiceList)
        );
    }

    public RecordsImportModels.SimilarKanjiChoiceResult evaluateSelection(
            RecordsImportModels.SimilarKanjiChoiceCard card,
            String selectedKanji
    ) {
        if (card == null) {
            return new RecordsImportModels.SimilarKanjiChoiceResult(null, selectedKanji, false, Collections.emptyList());
        }
        String selected = selectedKanji == null ? "" : selectedKanji.trim();
        boolean correct = card.targetKanji.equals(selected);
        if (correct) {
            return new RecordsImportModels.SimilarKanjiChoiceResult(card, selected, true, Collections.emptyList());
        }
        LinkedHashSet<String> repairs = new LinkedHashSet<>();
        repairs.add(card.targetKanji);
        if (card.choices.contains(selected)) {
            repairs.add(selected);
        }
        return new RecordsImportModels.SimilarKanjiChoiceResult(card, selected, false, new ArrayList<>(repairs));
    }

    public static List<String> fallbackChoices(String targetKanji, List<RecordsImportModels.SimilarKanjiPair> pairs) {
        LinkedHashSet<String> choices = new LinkedHashSet<>();
        choices.add(targetKanji);
        if (pairs != null) {
            for (RecordsImportModels.SimilarKanjiPair pair : pairs) {
                if (pair == null) {
                    continue;
                }
                String other = pair.kanjiA.equals(targetKanji) ? pair.kanjiB : pair.kanjiA;
                choices.add(other);
                if (choices.size() >= FALLBACK_CHOICE_LIMIT) {
                    break;
                }
            }
        }
        return new ArrayList<>(choices);
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
