package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SuspendedKanjiImporter {
    private final JitenKanjiRanks ranks;
    private final int cutoff;

    public SuspendedKanjiImporter(JitenKanjiRanks ranks, int cutoff) {
        this.ranks = ranks;
        this.cutoff = cutoff;
    }

    public List<Records.SuspendedImport> importFrom(Records.CollectionSnapshot snapshot, Records.Settings settings) {
        Map<Long, Records.Note> notesById = snapshot.notesById();
        Map<String, List<Records.SuspendedSource>> sourcesByKanji = new LinkedHashMap<>();
        for (Records.Card card : snapshot.cards) {
            if (!card.suspended) {
                continue;
            }
            Records.Note note = notesById.get(card.noteId);
            if (note == null) {
                continue;
            }
            String expression = TextUtil.normalizeJapanese(note.expression(settings));
            for (String kanji : TextUtil.extractKanji(expression)) {
                Integer rank = ranks.rankOf(kanji);
                if (rank == null || rank > cutoff) {
                    sourcesByKanji.computeIfAbsent(kanji, ignored -> new ArrayList<>())
                            .add(new Records.SuspendedSource(
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

        List<Records.SuspendedImport> results = new ArrayList<>();
        for (Map.Entry<String, List<Records.SuspendedSource>> entry : sourcesByKanji.entrySet()) {
            Integer rank = ranks.rankOf(entry.getKey());
            results.add(new Records.SuspendedImport(
                    entry.getKey(),
                    rank,
                    rank != null,
                    cutoff,
                    entry.getValue()
            ));
        }
        results.sort(Comparator
                .comparing((Records.SuspendedImport item) -> item.jitenRank == null ? Integer.MAX_VALUE : item.jitenRank)
                .thenComparing(item -> item.kanji));
        return results;
    }
}
