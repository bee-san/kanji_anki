package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KanjiAnalyzer {
    public List<Records.DashboardRow> rebuild(
            Records.CollectionSnapshot snapshot,
            List<Records.SuspendedImport> suspendedImports,
            JitenKanjiRanks ranks,
            Records.Settings settings
    ) {
        Map<Long, Records.Note> notesById = snapshot.notesById();
        Map<String, MutableRow> rows = new LinkedHashMap<>();
        Set<Long> suspendedCardIds = new LinkedHashSet<>();

        for (Records.Card card : snapshot.cards) {
            Records.Note note = notesById.get(card.noteId);
            if (note == null) {
                continue;
            }
            if (card.suspended) {
                suspendedCardIds.add(card.cardId);
            }
            String sourceType = card.suspended ? "suspended" : "active";
            String expression = TextUtil.normalizeJapanese(note.expression(settings));
            List<String> kanjiList = TextUtil.extractKanji(expression);
            if (kanjiList.isEmpty()) {
                kanjiList = TextUtil.extractKanji(note.sentence(settings));
            }
            Records.Example example = new Records.Example(
                    sourceType,
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
            for (String kanji : kanjiList) {
                rows.computeIfAbsent(kanji, MutableRow::new).examples.add(example);
            }
        }

        for (Records.SuspendedImport imported : suspendedImports) {
            MutableRow row = rows.computeIfAbsent(imported.kanji, MutableRow::new);
            for (Records.SuspendedSource source : imported.sources) {
                if (suspendedCardIds.contains(source.cardId)) {
                    continue;
                }
                row.examples.add(new Records.Example(
                        "suspended",
                        source.cardId,
                        source.noteId,
                        source.expression,
                        source.reading,
                        source.meaning,
                        source.sentence,
                        false,
                        0
                ));
            }
        }

        List<Records.DashboardRow> out = new ArrayList<>();
        for (MutableRow row : rows.values()) {
            Records.DashboardRow built = row.build(ranks, settings);
            if (built.weaknessScore > 0 || built.suspendedExampleCount > 0) {
                out.add(built);
            }
        }
        out.sort(Comparator
                .comparingInt((Records.DashboardRow row) -> row.weaknessScore).reversed()
                .thenComparing((Records.DashboardRow row) -> row.suspendedExampleCount, Comparator.reverseOrder())
                .thenComparing(row -> row.jitenRank == null ? Integer.MAX_VALUE : row.jitenRank)
                .thenComparing(row -> row.kanji));
        return out;
    }

    private static final class MutableRow {
        private final String kanji;
        private final List<Records.Example> examples = new ArrayList<>();

        private MutableRow(String kanji) {
            this.kanji = kanji;
        }

        private Records.DashboardRow build(JitenKanjiRanks ranks, Records.Settings settings) {
            int active = 0;
            int suspended = 0;
            int mature = 0;
            int lapses = 0;
            int reps = 0;
            int intervalPressure = 0;
            int fsrsPressure = 0;
            String meaning = "";
            String reading = "";
            List<Records.Example> trimmed = new ArrayList<>();
            Set<Long> seenCards = new LinkedHashSet<>();
            for (Records.Example example : examples) {
                if (seenCards.add(example.cardId) && trimmed.size() < 8) {
                    trimmed.add(example);
                }
                if ("suspended".equals(example.sourceType)) {
                    suspended++;
                } else {
                    active++;
                    if (example.mature) {
                        mature++;
                    }
                    lapses += example.lapses;
                    reps += example.reps;
                    if (example.reps >= 8 && !example.mature) {
                        intervalPressure++;
                    }
                    fsrsPressure += fsrsPressure(example, settings);
                }
                if (meaning.isEmpty() && !example.meaning.isEmpty()) {
                    meaning = example.meaning;
                }
                if (reading.isEmpty() && !example.reading.isEmpty()) {
                    reading = example.reading;
                }
            }
            int supportDeficit = Math.max(0, settings.matureSupportThreshold - mature);
            int weakness = suspended * 12
                    + supportDeficit * 5
                    + Math.min(8, lapses * 2)
                    + Math.min(6, intervalPressure * 2)
                    + Math.min(12, fsrsPressure);
            String reasonCode;
            String reasonText;
            if (suspended > 0) {
                reasonCode = "suspended_archive";
                reasonText = suspended + " missed example" + (suspended == 1 ? "" : "s") + " made this a writing-practice target.";
            } else if (fsrsPressure > 0) {
                reasonCode = "fsrs_weak_memory";
                reasonText = "Anki FSRS memory state marks this kanji as fragile.";
            } else if (supportDeficit > 0) {
                reasonCode = "weak_support";
                reasonText = "Only " + mature + " known example" + (mature == 1 ? "" : "s") + " support this kanji.";
            } else if (intervalPressure > 0) {
                reasonCode = "anki_scheduler_weakness";
                reasonText = "Anki has " + reps + " active review" + (reps == 1 ? "" : "s") + " but little mature support for this kanji.";
            } else if (lapses > 0) {
                reasonCode = "anki_lapses";
                reasonText = "Your active Anki cards containing this kanji have " + lapses + " lapse" + (lapses == 1 ? "" : "s") + ".";
            } else {
                reasonCode = "watch";
                reasonText = "This kanji appears in your active cards and is ready for examples.";
            }
            return new Records.DashboardRow(
                    kanji,
                    ranks.rankOf(kanji),
                    meaning,
                    reading,
                    TextUtil.browserSearchForKanji(kanji, settings),
                    weakness,
                    reasonCode,
                    reasonText,
                    active,
                    suspended,
                    mature,
                    trimmed
            );
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
}
