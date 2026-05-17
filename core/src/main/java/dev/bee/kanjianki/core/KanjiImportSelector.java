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
        this(ranks, Records.DEFAULT_SUSPENDED_RANK_MIN, cutoff);
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

    public List<Records.SuspendedImport> importFrom(Records.CollectionSnapshot snapshot, Records.Settings settings) {
        if (snapshot == null || settings == null || !settings.hasImportSourceEnabled()) {
            return new ArrayList<>();
        }
        Map<Long, Records.Note> notesById = snapshot.notesById();
        Map<String, Map<Long, Records.SuspendedSource>> sourcesByKanji = new LinkedHashMap<>();
        for (Records.Card card : snapshot.cards) {
            Records.Note note = notesById.get(card.noteId);
            if (note != null) {
                SourceMatch match = sourceMatch(card, note, settings);
                if (match.matches()) {
                    addSources(sourcesByKanji, card, note, settings, match);
                }
            }
        }

        List<Records.SuspendedImport> results = new ArrayList<>();
        for (Map.Entry<String, Map<Long, Records.SuspendedSource>> entry : sourcesByKanji.entrySet()) {
            if (entry.getValue().size() < settings.importMinMatchingCardsPerKanji) {
                continue;
            }
            Integer rank = ranks.rankOf(entry.getKey());
            results.add(new Records.SuspendedImport(
                    entry.getKey(),
                    rank,
                    true,
                    maxRank,
                    new ArrayList<>(entry.getValue().values())
            ));
        }
        results.sort(Comparator
                .comparingInt((Records.SuspendedImport item) -> item.jitenRank)
                .thenComparing(item -> item.kanji));
        return results;
    }

    private SourceMatch sourceMatch(Records.Card card, Records.Note note, Records.Settings settings) {
        boolean activeMatch = settings.importActiveCards && !card.suspended;
        boolean suspendedMatch = settings.importSuspendedCards && card.suspended;
        boolean taggedMatch = settings.importTaggedCardsEnabled() && hasMatchingTag(note, settings.importTags);
        boolean weakMatch = settings.importWeakCards && weakCard(card, settings);
        boolean browserQueryMatch = settings.browserQueryImportEnabled() && card.browserQueryMatched;
        return new SourceMatch(activeMatch, suspendedMatch, taggedMatch, weakMatch, browserQueryMatch);
    }

    private boolean hasMatchingTag(Records.Note note, List<String> importTags) {
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

    private boolean weakCard(Records.Card card, Records.Settings settings) {
        return (card.fsrsDifficulty != null && card.fsrsDifficulty >= settings.importWeakFsrsDifficultyThreshold)
                || card.lapses >= settings.importWeakLapsesThreshold;
    }

    private void addSources(
            Map<String, Map<Long, Records.SuspendedSource>> sourcesByKanji,
            Records.Card card,
            Records.Note note,
            Records.Settings settings,
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

    private Records.SuspendedSource sourceFromCard(
            String kanji,
            Records.Card card,
            Records.Note note,
            String expression,
            Records.Settings settings,
            SourceMatch match
    ) {
        String sourceType = resolveSourceType(card, match);
        return new Records.SuspendedSource(
                kanji,
                card.cardId,
                note.noteId,
                expression,
                TextUtil.normalizeJapanese(note.reading(settings)),
                TextUtil.firstMeaningLine(note.meaning(settings)),
                Records.SuspendedSourceDetails.builder(TextUtil.normalizeJapanese(note.sentence(settings)))
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

    private static String resolveSourceType(Records.Card card, SourceMatch match) {
        if (card.suspended) {
            return Records.SOURCE_SUSPENDED;
        }
        if (match.browserQuery()) {
            return Records.SOURCE_BROWSER_QUERY;
        }
        return Records.SOURCE_ACTIVE;
    }

    private record SourceMatch(boolean active, boolean suspended, boolean tagged, boolean weak, boolean browserQuery) {
        private boolean matches() {
            return active || suspended || tagged || weak || browserQuery;
        }

        private boolean forcePractice() {
            return suspended || tagged || weak || browserQuery;
        }

        private List<String> ruleTypes(Records.Card card) {
            List<String> rules = new ArrayList<>();
            if (active && !card.suspended) {
                rules.add(Records.SOURCE_ACTIVE);
            }
            if (suspended) {
                rules.add(Records.SOURCE_SUSPENDED);
            }
            if (tagged) {
                rules.add(Records.SOURCE_TAGGED);
            }
            if (weak) {
                rules.add(Records.SOURCE_WEAK);
            }
            if (browserQuery) {
                rules.add(Records.SOURCE_BROWSER_QUERY);
            }
            return rules;
        }
    }
}
