package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SuspendedKanjiImporter {
    private final JitenKanjiRanks ranks;
    private final int minRank;
    private final int maxRank;

    public SuspendedKanjiImporter(JitenKanjiRanks ranks, int cutoff) {
        this(ranks, RecordsBase.DEFAULT_SUSPENDED_RANK_MIN, cutoff);
    }

    public SuspendedKanjiImporter(JitenKanjiRanks ranks, int minRank, int maxRank) {
        this.ranks = ranks;
        int normalizedMin = Math.max(1, Math.min(20000, minRank));
        int normalizedMax = Math.max(1, Math.min(20000, maxRank));
        if (normalizedMin > normalizedMax) {
            int swap = normalizedMin;
            normalizedMin = normalizedMax;
            normalizedMax = swap;
        }
        this.minRank = normalizedMin;
        this.maxRank = normalizedMax;
    }

    public List<RecordsImportModels.SuspendedImport> importFrom(RecordsSyncModels.CollectionSnapshot snapshot, RecordsSyncModels.Settings settings) {
        Map<Long, RecordsSyncModels.Note> notesById = snapshot.notesById();
        Map<String, List<RecordsImportModels.SuspendedSource>> sourcesByKanji = new LinkedHashMap<>();
        for (RecordsSyncModels.Card card : snapshot.cards) {
            RecordsSyncModels.Note note = notesById.get(card.noteId);
            if (card.suspended && note != null) {
                addSuspendedSources(sourcesByKanji, card, note, settings);
            }
        }

        List<RecordsImportModels.SuspendedImport> results = new ArrayList<>();
        for (Map.Entry<String, List<RecordsImportModels.SuspendedSource>> entry : sourcesByKanji.entrySet()) {
            Integer rank = ranks.rankOf(entry.getKey());
            results.add(new RecordsImportModels.SuspendedImport(
                    entry.getKey(),
                    rank,
                    true,
                    maxRank,
                    entry.getValue()
            ));
        }
        results.sort(Comparator
                .comparingInt((RecordsImportModels.SuspendedImport item) -> item.jitenRank)
                .thenComparing(item -> item.kanji));
        return results;
    }

    private void addSuspendedSources(
            Map<String, List<RecordsImportModels.SuspendedSource>> sourcesByKanji,
            RecordsSyncModels.Card card,
            RecordsSyncModels.Note note,
            RecordsSyncModels.Settings settings
    ) {
        String expression = TextUtil.normalizeJapanese(note.expression(settings));
        for (String kanji : TextUtil.extractKanji(expression)) {
            Integer rank = ranks.rankOf(kanji);
            if (rank != null && rank >= minRank && rank <= maxRank) {
                sourcesByKanji.computeIfAbsent(kanji, ignored -> new ArrayList<>())
                        .add(new RecordsImportModels.SuspendedSource(
                                kanji,
                                card.cardId,
                                note.noteId,
                                expression,
                                TextUtil.normalizeJapanese(note.reading(settings)),
                                TextUtil.firstMeaningLine(note.meaning(settings)),
                                TextUtil.normalizeJapanese(note.sentence(settings))
                        ));
            }
        }
    }
}
