package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KanjiAnalyzer {
    private static final String SOURCE_ACTIVE = "active";
    private static final String SOURCE_SUSPENDED = "suspended";

    public List<Records.DashboardRow> rebuild(
            Records.CollectionSnapshot snapshot,
            List<Records.SuspendedImport> suspendedImports,
            JitenKanjiRanks ranks,
            Records.Settings settings
    ) {
        return rebuild(snapshot, suspendedImports, ranks, settings, false);
    }

    public List<Records.DashboardRow> rebuildSelectedSources(
            Records.CollectionSnapshot snapshot,
            List<Records.SuspendedImport> imports,
            JitenKanjiRanks ranks,
            Records.Settings settings
    ) {
        return rebuild(snapshot, imports, ranks, settings, true);
    }

    private List<Records.DashboardRow> rebuild(
            Records.CollectionSnapshot snapshot,
            List<Records.SuspendedImport> imports,
            JitenKanjiRanks ranks,
            Records.Settings settings,
            boolean selectedOnly
    ) {
        Map<Long, Records.Note> notesById = snapshot.notesById();
        Map<String, MutableRow> rows = new LinkedHashMap<>();
        Set<Long> cardIdsWithExamples = new LinkedHashSet<>();
        ImportSourceIndex importIndex = new ImportSourceIndex(imports, selectedOnly);

        addCardExamples(snapshot, notesById, rows, cardIdsWithExamples, settings, importIndex);
        addImportedSources(imports, rows, cardIdsWithExamples);

        List<Records.DashboardRow> out = dashboardRows(rows, ranks, settings, importIndex);
        out.sort(Comparator
                .comparingInt((Records.DashboardRow row) -> row.weaknessScore).reversed()
                .thenComparing((Records.DashboardRow row) -> row.suspendedExampleCount, Comparator.reverseOrder())
                .thenComparing(row -> row.jitenRank == null ? Integer.MAX_VALUE : row.jitenRank)
                .thenComparing(row -> row.kanji));
        return out;
    }

    private static void addCardExamples(
            Records.CollectionSnapshot snapshot,
            Map<Long, Records.Note> notesById,
            Map<String, MutableRow> rows,
            Set<Long> cardIdsWithExamples,
            Records.Settings settings,
            ImportSourceIndex importIndex
    ) {
        for (Records.Card card : snapshot.cards) {
            if (!importIndex.shouldReadCard(card.cardId)) {
                continue;
            }
            Records.Note note = notesById.get(card.noteId);
            if (note != null) {
                addCardExample(card, note, rows, cardIdsWithExamples, settings, importIndex);
            }
        }
    }

    private static void addCardExample(
            Records.Card card,
            Records.Note note,
            Map<String, MutableRow> rows,
            Set<Long> cardIdsWithExamples,
            Records.Settings settings,
            ImportSourceIndex importIndex
    ) {
        cardIdsWithExamples.add(card.cardId);
        String expression = TextUtil.normalizeJapanese(note.expression(settings));
        List<String> kanjiList = TextUtil.extractKanji(expression);
        if (kanjiList.isEmpty()) {
            kanjiList = TextUtil.extractKanji(note.sentence(settings));
        }
        Records.Example example = exampleFromCard(card, note, expression, settings);
        for (String kanji : kanjiList) {
            if (!importIndex.shouldReadKanji(kanji)) {
                continue;
            }
            MutableRow row = rows.computeIfAbsent(kanji, MutableRow::new);
            row.examples.add(example);
            row.forcePractice = row.forcePractice || importIndex.forcePractice(kanji, card.cardId);
        }
    }

    private static Records.Example exampleFromCard(
            Records.Card card,
            Records.Note note,
            String expression,
            Records.Settings settings
    ) {
        return new Records.Example(
                card.suspended ? SOURCE_SUSPENDED : SOURCE_ACTIVE,
                card.cardId,
                note.noteId,
                expression,
                TextUtil.normalizeJapanese(note.reading(settings)),
                TextUtil.firstMeaningLine(note.meaning(settings)),
                TextUtil.normalizeJapanese(note.sentence(settings)),
                card.mature(settings.matureDays),
                card.lapses,
                card.intervalDays,
                card.reps,
                card.fsrsStability,
                card.fsrsDifficulty,
                card.fsrsRetrievability
        );
    }

    private static void addImportedSources(
            List<Records.SuspendedImport> imports,
            Map<String, MutableRow> rows,
            Set<Long> cardIdsWithExamples
    ) {
        for (Records.SuspendedImport imported : imports) {
            MutableRow row = rows.computeIfAbsent(imported.kanji, MutableRow::new);
            for (Records.SuspendedSource source : imported.sources) {
                row.forcePractice = row.forcePractice || source.forcePractice;
                if (!cardIdsWithExamples.contains(source.cardId)) {
                    row.examples.add(exampleFromImportedSource(source));
                }
            }
        }
    }

    private static Records.Example exampleFromImportedSource(Records.SuspendedSource source) {
        return new Records.Example(
                source.sourceType,
                source.cardId,
                source.noteId,
                source.expression,
                source.reading,
                source.meaning,
                source.sentence,
                source.mature,
                source.lapses,
                source.intervalDays,
                source.reps,
                source.fsrsStability,
                source.fsrsDifficulty,
                source.fsrsRetrievability
        );
    }

    private static List<Records.DashboardRow> dashboardRows(
            Map<String, MutableRow> rows,
            JitenKanjiRanks ranks,
            Records.Settings settings,
            ImportSourceIndex importIndex
    ) {
        List<Records.DashboardRow> out = new ArrayList<>();
        for (MutableRow row : rows.values()) {
            Records.DashboardRow built = row.build(ranks, settings);
            if (built.weaknessScore > 0 || built.suspendedExampleCount > 0 || row.forcePractice || importIndex.forcePractice(row.kanji)) {
                out.add(built);
            }
        }
        return out;
    }

    private static final class MutableRow {
        private final String kanji;
        private final List<Records.Example> examples = new ArrayList<>();
        private boolean forcePractice;

        private MutableRow(String kanji) {
            this.kanji = kanji;
        }

        private Records.DashboardRow build(JitenKanjiRanks ranks, Records.Settings settings) {
            RowSummary summary = summarize(settings);
            int supportDeficit = Math.max(0, settings.matureSupportThreshold - summary.mature);
            int weakness = summary.suspended * 12
                    + supportDeficit * 5
                    + Math.min(8, summary.lapses * 2)
                    + Math.min(6, summary.intervalPressure * 2)
                    + Math.min(12, summary.fsrsPressure);
            Reason reason = reasonFor(summary, supportDeficit);
            return new Records.DashboardRow(
                    kanji,
                    ranks.rankOf(kanji),
                    summary.meaning,
                    summary.reading,
                    TextUtil.browserSearchForKanji(kanji, settings),
                    weakness,
                    reason.code,
                    reason.text,
                    summary.active,
                    summary.suspended,
                    summary.mature,
                    summary.trimmed
            );
        }

        private RowSummary summarize(Records.Settings settings) {
            RowSummary summary = new RowSummary();
            Set<Long> seenCards = new LinkedHashSet<>();
            for (Records.Example example : examples) {
                summary.addExample(example, fsrsPressure(example, settings), seenCards);
            }
            return summary;
        }

        private Reason reasonFor(RowSummary summary, int supportDeficit) {
            if (summary.suspended > 0) {
                return new Reason("suspended_archive", summary.suspended + " missed example" + (summary.suspended == 1 ? "" : "s") + " made this a writing-practice target.");
            } else if (summary.fsrsPressure > 0) {
                return new Reason("fsrs_weak_memory", "Anki FSRS memory state marks this kanji as fragile.");
            } else if (supportDeficit > 0) {
                return new Reason("weak_support", "Only " + summary.mature + " known example" + (summary.mature == 1 ? "" : "s") + " support this kanji.");
            } else if (summary.intervalPressure > 0) {
                return new Reason("anki_scheduler_weakness", "Anki has " + summary.reps + " active review" + (summary.reps == 1 ? "" : "s") + " but little mature support for this kanji.");
            } else if (summary.lapses > 0) {
                return new Reason("anki_lapses", "Your active Anki cards containing this kanji have " + summary.lapses + " lapse" + (summary.lapses == 1 ? "" : "s") + ".");
            } else {
                return new Reason("watch", "This kanji appears in your active cards and is ready for examples.");
            }
        }

        private int fsrsPressure(Records.Example example, Records.Settings settings) {
            int pressure = 0;
            Double retrievability = normalizedRetrievability(example.fsrsRetrievability);
            if (retrievability != null && retrievability < 0.75) {
                pressure += retrievability < 0.50 ? 6 : 3;
            }
            if (example.fsrsDifficulty != null && example.fsrsDifficulty >= 7.0) {
                pressure += 3;
            }
            if (example.fsrsStability != null && example.reps >= 5 && example.fsrsStability < settings.matureDays) {
                pressure += 3;
            }
            if (example.mature
                    && example.fsrsStability != null
                    && example.fsrsStability >= settings.matureDays * 2.0
                    && pressure == 0) {
                pressure -= 2;
            }
            return Math.max(0, pressure);
        }

        private Double normalizedRetrievability(Double value) {
            if (value == null || value < 0.0) {
                return null;
            }
            if (value > 1.0 && value <= 100.0) {
                return value / 100.0;
            }
            return value > 1.0 ? null : value;
        }
    }

    private static final class RowSummary {
        private int active;
        private int suspended;
        private int mature;
        private int lapses;
        private int reps;
        private int intervalPressure;
        private int fsrsPressure;
        private String meaning = "";
        private String reading = "";
        private final List<Records.Example> trimmed = new ArrayList<>();

        private void addExample(Records.Example example, int fsrsPressureValue, Set<Long> seenCards) {
            if (seenCards.add(example.cardId) && trimmed.size() < 8) {
                trimmed.add(example);
            }
            if (SOURCE_SUSPENDED.equals(example.sourceType)) {
                suspended++;
            } else {
                addActiveExample(example, fsrsPressureValue);
            }
            if (meaning.isEmpty() && !example.meaning.isEmpty()) {
                meaning = example.meaning;
            }
            if (reading.isEmpty() && !example.reading.isEmpty()) {
                reading = example.reading;
            }
        }

        private void addActiveExample(Records.Example example, int fsrsPressureValue) {
            active++;
            if (example.mature) {
                mature++;
            }
            lapses += example.lapses;
            reps += example.reps;
            if (example.reps >= 8 && !example.mature) {
                intervalPressure++;
            }
            fsrsPressure += fsrsPressureValue;
        }
    }

    private static final class Reason {
        private final String code;
        private final String text;

        private Reason(String code, String text) {
            this.code = code;
            this.text = text;
        }
    }

    private static final class ImportSourceIndex {
        private final boolean selectedOnly;
        private final Set<String> importedKanji = new LinkedHashSet<>();
        private final Set<String> forcePracticeKanji = new LinkedHashSet<>();
        private final Set<Long> selectedCardIds = new LinkedHashSet<>();
        private final Map<String, Map<Long, Records.SuspendedSource>> sourcesByKanji = new LinkedHashMap<>();

        private ImportSourceIndex(List<Records.SuspendedImport> imports, boolean selectedOnly) {
            this.selectedOnly = selectedOnly;
            for (Records.SuspendedImport imported : imports) {
                importedKanji.add(imported.kanji);
                Map<Long, Records.SuspendedSource> sources = sourcesByKanji.computeIfAbsent(imported.kanji, ignored -> new LinkedHashMap<>());
                for (Records.SuspendedSource source : imported.sources) {
                    selectedCardIds.add(source.cardId);
                    sources.put(source.cardId, source);
                    if (source.forcePractice) {
                        forcePracticeKanji.add(imported.kanji);
                    }
                }
            }
        }

        private boolean shouldReadCard(long cardId) {
            return !selectedOnly || selectedCardIds.contains(cardId);
        }

        private boolean shouldReadKanji(String kanji) {
            return !selectedOnly || importedKanji.contains(kanji);
        }

        private boolean forcePractice(String kanji) {
            return forcePracticeKanji.contains(kanji);
        }

        private boolean forcePractice(String kanji, long cardId) {
            Map<Long, Records.SuspendedSource> sources = sourcesByKanji.get(kanji);
            Records.SuspendedSource source = sources == null ? null : sources.get(cardId);
            return source != null && source.forcePractice;
        }
    }
}
