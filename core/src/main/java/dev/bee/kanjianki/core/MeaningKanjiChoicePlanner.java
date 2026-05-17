package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public final class MeaningKanjiChoicePlanner {
    private static final int CHOICE_COUNT = 4;

    public RecordsImportModels.MeaningKanjiChoiceCard buildChoiceCard(
            RecordsImportModels.DashboardRow target,
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsImportModels.KanjiInventoryItem> inventory,
            Random random
    ) {
        if (target == null || target.kanji == null || target.kanji.trim().isEmpty()) {
            return null;
        }
        String targetKanji = target.kanji.trim();
        String meaning = target.primaryMeaning == null ? "" : target.primaryMeaning.trim();
        if (meaning.isEmpty()) {
            return null;
        }
        String normalizedTargetMeaning = normalizeMeaning(meaning);
        Map<String, String> eligible = eligibleKanji(rows, inventory);
        eligible.put(targetKanji, normalizedTargetMeaning);
        eligible.remove("");
        if (eligible.size() < CHOICE_COUNT) {
            return null;
        }
        Random rng = Objects.requireNonNullElseGet(random, Random::new);
        List<String> decoys = new ArrayList<>(eligible.keySet());
        decoys.remove(targetKanji);
        decoys.removeIf(decoy -> !eligible.getOrDefault(decoy, "").isEmpty()
                && eligible.getOrDefault(decoy, "").equals(normalizedTargetMeaning));
        Collections.shuffle(decoys, rng);

        List<String> choices = new ArrayList<>();
        choices.add(targetKanji);
        for (String decoy : decoys) {
            if (choices.size() >= CHOICE_COUNT) {
                break;
            }
            choices.add(decoy);
        }
        if (choices.size() < CHOICE_COUNT) {
            return null;
        }
        Collections.shuffle(choices, rng);
        return new RecordsImportModels.MeaningKanjiChoiceCard(targetKanji, meaning, target.reading, choices);
    }

    private static Map<String, String> eligibleKanji(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsImportModels.KanjiInventoryItem> inventory
    ) {
        Map<String, String> out = new LinkedHashMap<>();
        addEligibleRows(out, rows);
        addEligibleInventory(out, inventory);
        return out;
    }

    private static void addEligibleRows(Map<String, String> out, List<RecordsImportModels.DashboardRow> rows) {
        if (rows != null) {
            for (RecordsImportModels.DashboardRow row : rows) {
                if (row != null && row.kanji != null && !row.kanji.trim().isEmpty()) {
                    out.put(row.kanji.trim(), normalizeMeaning(row.primaryMeaning));
                }
            }
        }
    }

    private static void addEligibleInventory(
            Map<String, String> out,
            List<RecordsImportModels.KanjiInventoryItem> inventory
    ) {
        if (inventory != null) {
            for (RecordsImportModels.KanjiInventoryItem item : inventory) {
                String kanji = item == null || item.kanji == null ? "" : item.kanji.trim();
                if (!kanji.isEmpty()) {
                    out.put(kanji, normalizeMeaning(item.primaryMeaning));
                }
            }
        }
    }

    private static String normalizeMeaning(String meaning) {
        return meaning == null ? "" : meaning.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
