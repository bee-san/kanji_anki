package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KanjiImportSelector {
    private final JitenKanjiRanks ranks;
    private final int minRank;
    private final int maxRank;

    public KanjiImportSelector(JitenKanjiRanks ranks, int cutoff) {
        this(ranks, RecordsBase.DEFAULT_SUSPENDED_RANK_MIN, cutoff);
    }

    public KanjiImportSelector(JitenKanjiRanks ranks, int minRank, int maxRank) {
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
        if (snapshot == null || settings == null || !settings.hasImportSourceEnabled()) {
            return new ArrayList<>();
        }
        Map<Long, RecordsSyncModels.Note> notesById = snapshot.notesById();
        Map<String, Map<Long, RecordsImportModels.SuspendedSource>> sourcesByKanji = new LinkedHashMap<>();
        for (RecordsSyncModels.Card card : snapshot.cards) {
            RecordsSyncModels.Note note = notesById.get(card.noteId);
            if (note != null) {
                SourceMatch match = sourceMatch(card, note, settings);
                if (match.matches()) {
                    addSources(sourcesByKanji, card, note, settings, match);
                }
            }
        }

        List<RecordsImportModels.SuspendedImport> results = new ArrayList<>();
        for (Map.Entry<String, Map<Long, RecordsImportModels.SuspendedSource>> entry : sourcesByKanji.entrySet()) {
            if (entry.getValue().size() < settings.importMinMatchingCardsPerKanji) {
                continue;
            }
            Integer rank = ranks.rankOf(entry.getKey());
            results.add(new RecordsImportModels.SuspendedImport(
                    entry.getKey(),
                    rank,
                    true,
                    maxRank,
                    new ArrayList<>(entry.getValue().values())
            ));
        }
        results.sort(Comparator
                .comparingInt((RecordsImportModels.SuspendedImport item) -> item.jitenRank)
                .thenComparing(item -> item.kanji));
        return results;
    }

    private SourceMatch sourceMatch(RecordsSyncModels.Card card, RecordsSyncModels.Note note, RecordsSyncModels.Settings settings) {
        boolean activeMatch = settings.importActiveCards && !card.suspended;
        boolean suspendedMatch = settings.importSuspendedCards && card.suspended;
        boolean taggedMatch = settings.importTaggedCardsEnabled() && hasMatchingTag(note, settings.importTags);
        boolean weakMatch = settings.importWeakCards && weakCard(card, settings);
        boolean browserQueryMatch = settings.browserQueryImportEnabled() && card.browserQueryMatched;
        return new SourceMatch(activeMatch, suspendedMatch, taggedMatch, weakMatch, browserQueryMatch);
    }

    private boolean hasMatchingTag(RecordsSyncModels.Note note, List<String> importTags) {
        if (note.tags.isEmpty()) {
            return false;
        }
        Set<String> noteTags = new LinkedHashSet<>(note.tags);
        for (String tag : importTags) {
            if (noteTags.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private boolean weakCard(RecordsSyncModels.Card card, RecordsSyncModels.Settings settings) {
        return (card.fsrsDifficulty != null && card.fsrsDifficulty >= settings.importWeakFsrsDifficultyThreshold)
                || card.lapses >= settings.importWeakLapsesThreshold;
    }

    private void addSources(
            Map<String, Map<Long, RecordsImportModels.SuspendedSource>> sourcesByKanji,
            RecordsSyncModels.Card card,
            RecordsSyncModels.Note note,
            RecordsSyncModels.Settings settings,
            SourceMatch match
    ) {
        String expression = TextUtil.normalizeJapanese(note.expression(settings));
        for (String kanji : TextUtil.extractKanji(expression)) {
            Integer rank = ranks.rankOf(kanji);
            if (rank != null && rank >= minRank && rank <= maxRank) {
                sourcesByKanji.computeIfAbsent(kanji, ignored -> new LinkedHashMap<>())
                        .put(card.cardId, sourceFromCard(kanji, card, note, expression, settings, match));
            }
        }
    }

    private RecordsImportModels.SuspendedSource sourceFromCard(
            String kanji,
            RecordsSyncModels.Card card,
            RecordsSyncModels.Note note,
            String expression,
            RecordsSyncModels.Settings settings,
            SourceMatch match
    ) {
        String sourceType = resolveSourceType(card, match);
        return new RecordsImportModels.SuspendedSource(
                kanji,
                card.cardId,
                note.noteId,
                expression,
                TextUtil.normalizeJapanese(note.reading(settings)),
                TextUtil.firstMeaningLine(note.meaning(settings)),
                RecordsImportModels.SuspendedSourceDetails.builder(TextUtil.normalizeJapanese(note.sentence(settings)))
                        .sourceType(sourceType)
                        .suspended(card.suspended)
                        .forcePractice(match.forcePractice())
                        .mature(card.mature(settings.matureDays))
                        .reviewStats(card.lapses, card.intervalDays, card.reps)
                        .fsrs(card.fsrsStability, card.fsrsDifficulty, card.fsrsRetrievability)
                        .ruleTypes(match.ruleTypes(card))
                        .build()
        );
    }

    private static String resolveSourceType(RecordsSyncModels.Card card, SourceMatch match) {
        if (card.suspended) {
            return RecordsBase.SOURCE_SUSPENDED;
        }
        if (match.browserQuery()) {
            return RecordsBase.SOURCE_BROWSER_QUERY;
        }
        return RecordsBase.SOURCE_ACTIVE;
    }

    private record SourceMatch(boolean active, boolean suspended, boolean tagged, boolean weak, boolean browserQuery) {
        private boolean matches() {
            return active || suspended || tagged || weak || browserQuery;
        }

        private boolean forcePractice() {
            return suspended || tagged || weak || browserQuery;
        }

        private List<String> ruleTypes(RecordsSyncModels.Card card) {
            List<String> rules = new ArrayList<>();
            if (active && !card.suspended) {
                rules.add(RecordsBase.SOURCE_ACTIVE);
            }
            if (suspended) {
                rules.add(RecordsBase.SOURCE_SUSPENDED);
            }
            if (tagged) {
                rules.add(RecordsBase.SOURCE_TAGGED);
            }
            if (weak) {
                rules.add(RecordsBase.SOURCE_WEAK);
            }
            if (browserQuery) {
                rules.add(RecordsBase.SOURCE_BROWSER_QUERY);
            }
            return rules;
        }
    }
}
