package dev.bee.kanjianki.syncdomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ImportAuditBuilder {
    private ImportAuditBuilder() {
    }

    public static RuleAudit ruleAudit(SettingsSnapshot settings) {
        SettingsSnapshot safeSettings = SettingsSnapshot.safe(settings);
        return new RuleAudit(enabledImportSources(safeSettings), settingsJson(safeSettings));
    }

    public static ImportDecisionAudit decision(ImportCandidate imported, SettingsSnapshot settings) {
        ImportCandidate safeImport = ImportCandidate.safe(imported);
        SettingsSnapshot safeSettings = SettingsSnapshot.safe(settings);
        Set<String> sourceTypes = new LinkedHashSet<>();
        Set<String> ruleTypes = new LinkedHashSet<>();
        Set<Long> cardIds = new LinkedHashSet<>();
        Set<Long> noteIds = new LinkedHashSet<>();
        for (ImportSource source : safeImport.sources) {
            sourceTypes.add(source.sourceType());
            ruleTypes.addAll(source.ruleTypes());
            cardIds.add(source.cardId());
            noteIds.add(source.noteId());
        }
        return new ImportDecisionAudit(
                reasonCode(ruleTypes),
                reasonText(safeImport, safeSettings, ruleTypes, cardIds.size()),
                cardIds.size(),
                new ArrayList<>(sourceTypes),
                new ArrayList<>(ruleTypes),
                joinLongs(cardIds),
                joinLongs(noteIds)
        );
    }

    public static List<String> enabledImportSources(SettingsSnapshot settings) {
        SettingsSnapshot safeSettings = SettingsSnapshot.safe(settings);
        List<String> sources = new ArrayList<>();
        if (safeSettings.importActiveCards()) {
            sources.add(ImportRuleMatch.SOURCE_ACTIVE);
        }
        if (safeSettings.importSuspendedCards()) {
            sources.add(ImportRuleMatch.SOURCE_SUSPENDED);
        }
        if (safeSettings.importTaggedCards()) {
            sources.add(ImportRuleMatch.SOURCE_TAGGED);
        }
        if (safeSettings.importWeakCards()) {
            sources.add(ImportRuleMatch.SOURCE_WEAK);
        }
        if (safeSettings.importBrowserQueryCards() && !safeSettings.importBrowserQuery().isEmpty()) {
            sources.add(ImportRuleMatch.SOURCE_BROWSER_QUERY);
        }
        return Collections.unmodifiableList(sources);
    }

    public static String reasonCode(Set<String> ruleTypes) {
        Set<String> rules = ruleTypes == null ? Collections.emptySet() : ruleTypes;
        if (rules.size() > 1) {
            return "multiple_import_rules";
        }
        if (rules.contains(ImportRuleMatch.SOURCE_BROWSER_QUERY)) {
            return "browser_query_import";
        }
        if (rules.contains(ImportRuleMatch.SOURCE_SUSPENDED)) {
            return "suspended_import";
        }
        if (rules.contains(ImportRuleMatch.SOURCE_TAGGED)) {
            return "tagged_import";
        }
        if (rules.contains(ImportRuleMatch.SOURCE_WEAK)) {
            return "weak_card_import";
        }
        if (rules.contains(ImportRuleMatch.SOURCE_ACTIVE)) {
            return "active_import";
        }
        return "imported";
    }

    public static String reasonText(
            ImportCandidate imported,
            SettingsSnapshot settings,
            Set<String> ruleTypes,
            int sourceCount
    ) {
        ImportCandidate safeImport = ImportCandidate.safe(imported);
        SettingsSnapshot safeSettings = SettingsSnapshot.safe(settings);
        Set<String> rulesSet = ruleTypes == null ? Collections.emptySet() : ruleTypes;
        String rank = safeImport.jitenRank() == null ? "unknown" : Integer.toString(safeImport.jitenRank());
        String rules = rulesSet.isEmpty() ? "unknown rule" : String.join(" + ", rulesSet);
        return "Imported by " + rules
                + "; " + sourceCount + " source card" + (sourceCount == 1 ? "" : "s")
                + "; Jiten rank " + rank
                + "; rank range " + safeSettings.rankMin() + "-" + safeSettings.rankMax()
                + "; minimum matching cards " + safeSettings.minMatchingCards() + ".";
    }

    public static String settingsJson(SettingsSnapshot settings) {
        SettingsSnapshot safeSettings = SettingsSnapshot.safe(settings);
        return "{"
                + "\"model_name\":" + jsonQuote(safeSettings.modelName())
                + ",\"import_active_cards\":" + safeSettings.importActiveCards()
                + ",\"import_suspended_cards\":" + safeSettings.importSuspendedCards()
                + ",\"import_tagged_cards\":" + safeSettings.importTaggedCards()
                + ",\"import_tags\":" + jsonArray(safeSettings.importTags())
                + ",\"import_weak_cards\":" + safeSettings.importWeakCards()
                + ",\"import_weak_fsrs_difficulty\":" + safeSettings.weakFsrsDifficulty()
                + ",\"import_weak_lapses\":" + safeSettings.weakLapses()
                + ",\"import_browser_query_cards\":" + safeSettings.importBrowserQueryCards()
                + ",\"import_browser_query\":" + jsonQuote(safeSettings.importBrowserQuery())
                + ",\"rank_min\":" + safeSettings.rankMin()
                + ",\"rank_max\":" + safeSettings.rankMax()
                + ",\"min_matching_cards\":" + safeSettings.minMatchingCards()
                + "}";
    }

    private static String jsonArray(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        for (String value : values == null ? Collections.<String>emptyList() : values) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(jsonQuote(value));
        }
        out.append(']');
        return out.toString();
    }

    private static String jsonQuote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            appendJsonQuotedChar(out, value.charAt(i));
        }
        out.append('"');
        return out.toString();
    }

    private static void appendJsonQuotedChar(StringBuilder out, char c) {
        switch (c) {
            case '"':
                out.append("\\\"");
                break;
            case '\\':
                out.append("\\\\");
                break;
            case '\n':
                out.append("\\n");
                break;
            case '\r':
                out.append("\\r");
                break;
            case '\t':
                out.append("\\t");
                break;
            default:
                appendJsonDefaultChar(out, c);
                break;
        }
    }

    private static void appendJsonDefaultChar(StringBuilder out, char c) {
        if (c < 0x20) {
            out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
        } else {
            out.append(c);
        }
    }

    private static String joinLongs(Set<Long> values) {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (Long value : values) {
            if (!first) {
                out.append(' ');
            }
            first = false;
            out.append(value);
        }
        return out.toString();
    }

    public static final class SettingsSnapshot {
        private final String modelName;
        private final boolean importActiveCards;
        private final boolean importSuspendedCards;
        private final boolean importTaggedCards;
        private final List<String> importTags;
        private final boolean importWeakCards;
        private final double weakFsrsDifficulty;
        private final int weakLapses;
        private final int minMatchingCards;
        private final boolean importBrowserQueryCards;
        private final String importBrowserQuery;
        private final int rankMin;
        private final int rankMax;

        public SettingsSnapshot(
                String modelName,
                boolean importActiveCards,
                boolean importSuspendedCards,
                boolean importTaggedCards,
                List<String> importTags,
                boolean importWeakCards,
                double weakFsrsDifficulty,
                int weakLapses,
                int minMatchingCards,
                boolean importBrowserQueryCards,
                String importBrowserQuery,
                int rankMin,
                int rankMax
        ) {
            this.modelName = modelName == null ? "" : modelName;
            this.importActiveCards = importActiveCards;
            this.importSuspendedCards = importSuspendedCards;
            this.importTaggedCards = importTaggedCards;
            this.importTags = Collections.unmodifiableList(new ArrayList<>(importTags == null ? Collections.emptyList() : importTags));
            this.importWeakCards = importWeakCards;
            this.weakFsrsDifficulty = weakFsrsDifficulty;
            this.weakLapses = weakLapses;
            this.minMatchingCards = minMatchingCards;
            this.importBrowserQueryCards = importBrowserQueryCards;
            this.importBrowserQuery = importBrowserQuery == null ? "" : importBrowserQuery.trim();
            this.rankMin = rankMin;
            this.rankMax = rankMax;
        }

        private static SettingsSnapshot safe(SettingsSnapshot settings) {
            return settings == null
                    ? new SettingsSnapshot("", false, true, false, Collections.emptyList(), false, 0.0, 0, 1, false, "", 1, Integer.MAX_VALUE)
                    : settings;
        }

        public String modelName() {
            return modelName;
        }

        public boolean importActiveCards() {
            return importActiveCards;
        }

        public boolean importSuspendedCards() {
            return importSuspendedCards;
        }

        public boolean importTaggedCards() {
            return importTaggedCards;
        }

        public List<String> importTags() {
            return importTags;
        }

        public boolean importWeakCards() {
            return importWeakCards;
        }

        public double weakFsrsDifficulty() {
            return weakFsrsDifficulty;
        }

        public int weakLapses() {
            return weakLapses;
        }

        public int minMatchingCards() {
            return minMatchingCards;
        }

        public boolean importBrowserQueryCards() {
            return importBrowserQueryCards;
        }

        public String importBrowserQuery() {
            return importBrowserQuery;
        }

        public int rankMin() {
            return rankMin;
        }

        public int rankMax() {
            return rankMax;
        }
    }

    public static final class ImportCandidate {
        private final String kanji;
        private final Integer jitenRank;
        private final boolean rankKnown;
        private final List<ImportSource> sources;

        public ImportCandidate(String kanji, Integer jitenRank, boolean rankKnown, List<ImportSource> sources) {
            this.kanji = kanji == null ? "" : kanji;
            this.jitenRank = jitenRank;
            this.rankKnown = rankKnown;
            this.sources = Collections.unmodifiableList(new ArrayList<>(sources == null ? Collections.emptyList() : sources));
        }

        private static ImportCandidate safe(ImportCandidate imported) {
            return imported == null ? new ImportCandidate("", null, false, Collections.emptyList()) : imported;
        }

        public String kanji() {
            return kanji;
        }

        public Integer jitenRank() {
            return jitenRank;
        }

        public boolean rankKnown() {
            return rankKnown;
        }

        public List<ImportSource> sources() {
            return sources;
        }
    }

    public static final class ImportSource {
        private final long cardId;
        private final long noteId;
        private final String sourceType;
        private final List<String> ruleTypes;

        public ImportSource(long cardId, long noteId, String sourceType, List<String> ruleTypes) {
            this.cardId = cardId;
            this.noteId = noteId;
            this.sourceType = sourceType == null ? "" : sourceType;
            this.ruleTypes = Collections.unmodifiableList(new ArrayList<>(ruleTypes == null ? Collections.emptyList() : ruleTypes));
        }

        public long cardId() {
            return cardId;
        }

        public long noteId() {
            return noteId;
        }

        public String sourceType() {
            return sourceType;
        }

        public List<String> ruleTypes() {
            return ruleTypes;
        }
    }

    public static final class RuleAudit {
        private final List<String> enabledSources;
        private final String settingsJson;

        private RuleAudit(List<String> enabledSources, String settingsJson) {
            this.enabledSources = Collections.unmodifiableList(new ArrayList<>(enabledSources));
            this.settingsJson = settingsJson;
        }

        public List<String> enabledSources() {
            return enabledSources;
        }

        public String settingsJson() {
            return settingsJson;
        }
    }

    public static final class ImportDecisionAudit {
        private final String reasonCode;
        private final String reasonText;
        private final int sourceCount;
        private final List<String> sourceTypes;
        private final List<String> ruleTypes;
        private final String sourceCardIds;
        private final String sourceNoteIds;

        private ImportDecisionAudit(
                String reasonCode,
                String reasonText,
                int sourceCount,
                List<String> sourceTypes,
                List<String> ruleTypes,
                String sourceCardIds,
                String sourceNoteIds
        ) {
            this.reasonCode = reasonCode;
            this.reasonText = reasonText;
            this.sourceCount = sourceCount;
            this.sourceTypes = Collections.unmodifiableList(new ArrayList<>(sourceTypes));
            this.ruleTypes = Collections.unmodifiableList(new ArrayList<>(ruleTypes));
            this.sourceCardIds = sourceCardIds;
            this.sourceNoteIds = sourceNoteIds;
        }

        public String reasonCode() {
            return reasonCode;
        }

        public String reasonText() {
            return reasonText;
        }

        public int sourceCount() {
            return sourceCount;
        }

        public List<String> sourceTypes() {
            return sourceTypes;
        }

        public List<String> ruleTypes() {
            return ruleTypes;
        }

        public String sourceCardIds() {
            return sourceCardIds;
        }

        public String sourceNoteIds() {
            return sourceNoteIds;
        }
    }
}
