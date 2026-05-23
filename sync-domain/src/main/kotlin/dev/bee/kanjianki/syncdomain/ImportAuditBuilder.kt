package dev.bee.kanjianki.syncdomain

import java.util.Collections
import java.util.Locale

class ImportAuditBuilder private constructor() {
    class SettingsSnapshot(
        modelName: String?,
        private val importActiveCards: Boolean,
        private val importSuspendedCards: Boolean,
        private val importTaggedCards: Boolean,
        importTags: List<String>?,
        private val importWeakCards: Boolean,
        private val weakFsrsDifficulty: Double,
        private val weakLapses: Int,
        private val minMatchingCards: Int,
        private val importBrowserQueryCards: Boolean,
        importBrowserQuery: String?,
        private val rankMin: Int,
        private val rankMax: Int,
    ) {
        private val modelName: String = modelName ?: ""
        private val importTags: List<String> = Collections.unmodifiableList(ArrayList(importTags ?: emptyList()))
        private val importBrowserQuery: String = javaTrim(importBrowserQuery)

        fun modelName(): String = modelName
        fun importActiveCards(): Boolean = importActiveCards
        fun importSuspendedCards(): Boolean = importSuspendedCards
        fun importTaggedCards(): Boolean = importTaggedCards
        fun importTags(): List<String> = importTags
        fun importWeakCards(): Boolean = importWeakCards
        fun weakFsrsDifficulty(): Double = weakFsrsDifficulty
        fun weakLapses(): Int = weakLapses
        fun minMatchingCards(): Int = minMatchingCards
        fun importBrowserQueryCards(): Boolean = importBrowserQueryCards
        fun importBrowserQuery(): String = importBrowserQuery
        fun rankMin(): Int = rankMin
        fun rankMax(): Int = rankMax
    }

    class ImportCandidate(
        kanji: String?,
        private val jitenRank: Int?,
        private val rankKnown: Boolean,
        sources: List<ImportSource>?,
    ) {
        private val kanji: String = kanji ?: ""
        private val sources: List<ImportSource> = Collections.unmodifiableList(ArrayList(sources ?: emptyList()))

        fun kanji(): String = kanji
        fun jitenRank(): Int? = jitenRank
        fun rankKnown(): Boolean = rankKnown
        fun sources(): List<ImportSource> = sources
    }

    class ImportSource(
        private val cardId: Long,
        private val noteId: Long,
        sourceType: String?,
        ruleTypes: List<String>?,
    ) {
        private val sourceType: String = sourceType ?: ""
        private val ruleTypes: List<String> = Collections.unmodifiableList(ArrayList(ruleTypes ?: emptyList()))

        fun cardId(): Long = cardId
        fun noteId(): Long = noteId
        fun sourceType(): String = sourceType
        fun ruleTypes(): List<String> = ruleTypes
    }

    class RuleAudit private constructor(enabledSources: List<String>, private val settingsJson: String) {
        private val enabledSources: List<String> = Collections.unmodifiableList(ArrayList(enabledSources))

        fun enabledSources(): List<String> = enabledSources
        fun settingsJson(): String = settingsJson

        companion object {
            fun create(enabledSources: List<String>, settingsJson: String): RuleAudit {
                return RuleAudit(enabledSources, settingsJson)
            }
        }
    }

    class ImportDecisionAudit private constructor(
        private val reasonCode: String,
        private val reasonText: String,
        private val sourceCount: Int,
        sourceTypes: List<String>,
        ruleTypes: List<String>,
        private val sourceCardIds: String,
        private val sourceNoteIds: String,
    ) {
        private val sourceTypes: List<String> = Collections.unmodifiableList(ArrayList(sourceTypes))
        private val ruleTypes: List<String> = Collections.unmodifiableList(ArrayList(ruleTypes))

        fun reasonCode(): String = reasonCode
        fun reasonText(): String = reasonText
        fun sourceCount(): Int = sourceCount
        fun sourceTypes(): List<String> = sourceTypes
        fun ruleTypes(): List<String> = ruleTypes
        fun sourceCardIds(): String = sourceCardIds
        fun sourceNoteIds(): String = sourceNoteIds

        companion object {
            fun create(
                reasonCode: String,
                reasonText: String,
                sourceCount: Int,
                sourceTypes: List<String>,
                ruleTypes: List<String>,
                sourceCardIds: String,
                sourceNoteIds: String,
            ): ImportDecisionAudit {
                return ImportDecisionAudit(
                    reasonCode,
                    reasonText,
                    sourceCount,
                    sourceTypes,
                    ruleTypes,
                    sourceCardIds,
                    sourceNoteIds
                )
            }
        }
    }

    companion object {
        @JvmStatic
        fun ruleAudit(settings: SettingsSnapshot?): RuleAudit {
            val safeSettings = safe(settings)
            return RuleAudit.create(enabledImportSources(safeSettings), settingsJson(safeSettings))
        }

        @JvmStatic
        fun decision(imported: ImportCandidate?, settings: SettingsSnapshot?): ImportDecisionAudit {
            val safeImport = safe(imported)
            val safeSettings = safe(settings)
            val sourceTypes = LinkedHashSet<String>()
            val ruleTypes = LinkedHashSet<String>()
            val cardIds = LinkedHashSet<Long>()
            val noteIds = LinkedHashSet<Long>()
            for (source in safeImport.sources()) {
                sourceTypes.add(source.sourceType())
                ruleTypes.addAll(source.ruleTypes())
                cardIds.add(source.cardId())
                noteIds.add(source.noteId())
            }
            return ImportDecisionAudit.create(
                reasonCode(ruleTypes),
                reasonText(safeImport, safeSettings, ruleTypes, cardIds.size),
                cardIds.size,
                ArrayList(sourceTypes),
                ArrayList(ruleTypes),
                joinLongs(cardIds),
                joinLongs(noteIds)
            )
        }

        @JvmStatic
        fun enabledImportSources(settings: SettingsSnapshot?): List<String> {
            val safeSettings = safe(settings)
            val sources = ArrayList<String>()
            if (safeSettings.importActiveCards()) {
                sources.add(ImportRuleMatch.SOURCE_ACTIVE)
            }
            if (safeSettings.importSuspendedCards()) {
                sources.add(ImportRuleMatch.SOURCE_SUSPENDED)
            }
            if (safeSettings.importTaggedCards()) {
                sources.add(ImportRuleMatch.SOURCE_TAGGED)
            }
            if (safeSettings.importWeakCards()) {
                sources.add(ImportRuleMatch.SOURCE_WEAK)
            }
            if (safeSettings.importBrowserQueryCards() && safeSettings.importBrowserQuery().isNotEmpty()) {
                sources.add(ImportRuleMatch.SOURCE_BROWSER_QUERY)
            }
            return Collections.unmodifiableList(sources)
        }

        @JvmStatic
        fun reasonCode(ruleTypes: Set<String>?): String {
            val rules = ruleTypes ?: emptySet()
            if (rules.size > 1) {
                return "multiple_import_rules"
            }
            if (rules.contains(ImportRuleMatch.SOURCE_BROWSER_QUERY)) {
                return "browser_query_import"
            }
            if (rules.contains(ImportRuleMatch.SOURCE_SUSPENDED)) {
                return "suspended_import"
            }
            if (rules.contains(ImportRuleMatch.SOURCE_TAGGED)) {
                return "tagged_import"
            }
            if (rules.contains(ImportRuleMatch.SOURCE_WEAK)) {
                return "weak_card_import"
            }
            if (rules.contains(ImportRuleMatch.SOURCE_ACTIVE)) {
                return "active_import"
            }
            return "imported"
        }

        @JvmStatic
        fun reasonText(
            imported: ImportCandidate?,
            settings: SettingsSnapshot?,
            ruleTypes: Set<String>?,
            sourceCount: Int,
        ): String {
            val safeImport = safe(imported)
            val safeSettings = safe(settings)
            val rulesSet = ruleTypes ?: emptySet()
            val rank = safeImport.jitenRank()?.toString() ?: "unknown"
            val rules = if (rulesSet.isEmpty()) "unknown rule" else rulesSet.joinToString(" + ")
            return "Imported by $rules" +
                "; $sourceCount source card${if (sourceCount == 1) "" else "s"}" +
                "; Jiten rank $rank" +
                "; rank range ${safeSettings.rankMin()}-${safeSettings.rankMax()}" +
                "; minimum matching cards ${safeSettings.minMatchingCards()}."
        }

        @JvmStatic
        fun settingsJson(settings: SettingsSnapshot?): String {
            val safeSettings = safe(settings)
            return "{" +
                "\"model_name\":" + jsonQuote(safeSettings.modelName()) +
                ",\"import_active_cards\":" + safeSettings.importActiveCards() +
                ",\"import_suspended_cards\":" + safeSettings.importSuspendedCards() +
                ",\"import_tagged_cards\":" + safeSettings.importTaggedCards() +
                ",\"import_tags\":" + jsonArray(safeSettings.importTags()) +
                ",\"import_weak_cards\":" + safeSettings.importWeakCards() +
                ",\"import_weak_fsrs_difficulty\":" + safeSettings.weakFsrsDifficulty() +
                ",\"import_weak_lapses\":" + safeSettings.weakLapses() +
                ",\"import_browser_query_cards\":" + safeSettings.importBrowserQueryCards() +
                ",\"import_browser_query\":" + jsonQuote(safeSettings.importBrowserQuery()) +
                ",\"rank_min\":" + safeSettings.rankMin() +
                ",\"rank_max\":" + safeSettings.rankMax() +
                ",\"min_matching_cards\":" + safeSettings.minMatchingCards() +
                "}"
        }

        private fun jsonArray(values: List<String>?): String {
            val out = StringBuilder("[")
            var first = true
            for (value in values ?: emptyList()) {
                if (!first) {
                    out.append(',')
                }
                first = false
                out.append(jsonQuote(value))
            }
            out.append(']')
            return out.toString()
        }

        private fun jsonQuote(value: String?): String {
            if (value == null) {
                return "null"
            }
            val out = StringBuilder(value.length + 2)
            out.append('"')
            for (i in value.indices) {
                appendJsonQuotedChar(out, value[i])
            }
            out.append('"')
            return out.toString()
        }

        private fun appendJsonQuotedChar(out: StringBuilder, c: Char) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> appendJsonDefaultChar(out, c)
            }
        }

        private fun appendJsonDefaultChar(out: StringBuilder, c: Char) {
            if (c.code < 0x20) {
                out.append(String.format(Locale.ROOT, "\\u%04x", c.code))
            } else {
                out.append(c)
            }
        }

        private fun joinLongs(values: Set<Long>): String {
            val out = StringBuilder()
            var first = true
            for (value in values) {
                if (!first) {
                    out.append(' ')
                }
                first = false
                out.append(value)
            }
            return out.toString()
        }

        private fun safe(settings: SettingsSnapshot?): SettingsSnapshot {
            return settings ?: SettingsSnapshot("", false, true, false, emptyList(), false, 0.0, 0, 1, false, "", 1, Int.MAX_VALUE)
        }

        private fun safe(imported: ImportCandidate?): ImportCandidate {
            return imported ?: ImportCandidate("", null, false, emptyList())
        }

        private fun javaTrim(value: String?): String {
            return value?.trim { it <= ' ' } ?: ""
        }
    }
}
