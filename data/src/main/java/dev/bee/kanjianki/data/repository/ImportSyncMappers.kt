package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.importing.ImportDecisionEntity
import dev.bee.kanjianki.data.importing.ImportRuleAuditEntity
import dev.bee.kanjianki.data.importing.SuspendedArchiveEntity
import dev.bee.kanjianki.data.importing.SuspendedImportEntity
import dev.bee.kanjianki.data.importing.SuspendedSourceEntity
import dev.bee.kanjianki.domain.importing.ImportSourceEvidence
import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.ImportSource

internal fun ImportSettings.toRuleAuditEntity(
    syncRunId: SyncRunId,
    createdAt: Long,
): ImportRuleAuditEntity = ImportRuleAuditEntity(
    syncId = syncRunId.value,
    createdAt = createdAt,
    modelName = noteMapping.noteTypeName,
    enabledSources = enabledSources.joinToString(" ") { it.wireName },
    rankMin = suspendedRankMin,
    rankMax = suspendedRankMax,
    minMatchingCards = importMinMatchingCardsPerKanji,
    importTags = importTags.joinToString(" "),
    weakFsrsDifficulty = importWeakFsrsDifficultyThreshold,
    weakLapses = importWeakLapsesThreshold,
    browserQuery = importBrowserQuery.trim(),
    settingsJson = importSettingsJson(),
)

internal fun ImportedKanjiCandidate.toImportDecisionEntity(
    syncRunId: SyncRunId,
    settings: ImportSettings,
    createdAt: Long,
): ImportDecisionEntity {
    val sourceTypes = linkedSetOf<ImportSource>()
    val ruleTypes = linkedSetOf<ImportSource>()
    val cardIds = linkedSetOf<Long>()
    val noteIds = linkedSetOf<Long>()
    for (source in sources) {
        sourceTypes += source.sourceType
        ruleTypes += source.ruleTypes
        cardIds += source.cardId.value
        noteIds += source.noteId.value
    }
    return ImportDecisionEntity(
        syncId = syncRunId.value,
        kanji = kanji,
        decision = "imported",
        reasonCode = importReasonCode(ruleTypes),
        reasonText = importReasonText(settings, ruleTypes, cardIds.size),
        jitenRank = jitenRank,
        rankKnown = 1,
        rankMin = settings.suspendedRankMin,
        rankMax = settings.suspendedRankMax,
        minMatchingCards = settings.importMinMatchingCardsPerKanji,
        sourceCount = cardIds.size,
        sourceTypes = sourceTypes.joinToString(" ") { it.wireName },
        ruleTypes = ruleTypes.joinToString(" ") { it.wireName },
        sourceCardIds = cardIds.joinToString(" "),
        sourceNoteIds = noteIds.joinToString(" "),
        createdAt = createdAt,
    )
}

internal fun ImportedKanjiCandidate.suspendedOnly(): ImportedKanjiCandidate? {
    val suspendedSources = sources.filter { it.suspended }
    return if (suspendedSources.isEmpty()) {
        null
    } else {
        copy(sources = suspendedSources)
    }
}

internal fun ImportedKanjiCandidate.toSuspendedImportEntity(
    syncRunId: SyncRunId,
    firstImportedAt: Long,
): SuspendedImportEntity = SuspendedImportEntity(
    kanji = kanji,
    jitenRank = jitenRank,
    rankKnown = 1,
    cutoffUsed = rankRangeMax,
    firstImportedAt = firstImportedAt,
    lastSeenSyncId = syncRunId.value,
)

internal fun ImportedKanjiCandidate.toSuspendedSourceEntities(
    syncRunId: SyncRunId,
): List<SuspendedSourceEntity> = sources.map { source ->
    source.toSuspendedSourceEntity(syncRunId)
}

private fun ImportSourceEvidence.toSuspendedSourceEntity(
    syncRunId: SyncRunId,
): SuspendedSourceEntity = SuspendedSourceEntity(
    kanji = kanji,
    cardId = cardId.value,
    noteId = noteId.value,
    expression = expression,
    reading = reading,
    meaning = meaning,
    sentence = sentence,
    syncId = syncRunId.value,
)

internal fun SuspendedImportEntity.toRetainedImportCandidate(
    sources: List<SuspendedSourceEntity>,
    archiveRows: List<SuspendedArchiveEntity> = emptyList(),
): ImportedKanjiCandidate? {
    val rank = jitenRank ?: return null
    val sourceEvidence = sources.map { it.toRetainedImportSourceEvidence() }
    val sourceCardIds = sourceEvidence.mapTo(mutableSetOf()) { it.cardId.value }
    val archiveEvidence = archiveRows
        .filterNot { it.cardId in sourceCardIds }
        .map { it.toRetainedImportSourceEvidence(kanji) }
    val retainedSources = sourceEvidence + archiveEvidence
    if (retainedSources.isEmpty()) {
        return null
    }
    return ImportedKanjiCandidate(
        kanji = kanji,
        jitenRank = rank,
        rankRangeMax = retainedRankRangeMax(rank),
        sources = retainedSources,
    )
}

private fun SuspendedImportEntity.retainedRankRangeMax(rank: Int): Int =
    if (cutoffUsed in 1..20_000) cutoffUsed else rank

private fun SuspendedSourceEntity.toRetainedImportSourceEvidence(): ImportSourceEvidence =
    ImportSourceEvidence(
        kanji = kanji,
        cardId = CardId(cardId),
        noteId = NoteId(noteId),
        expression = expression,
        reading = reading,
        meaning = meaning,
        sentence = sentence,
        sourceType = ImportSource.SUSPENDED,
        suspended = true,
        forcePractice = true,
        mature = false,
        lapses = 0,
        intervalDays = 0,
        reps = 0,
        fsrsStability = null,
        fsrsDifficulty = null,
        fsrsRetrievability = null,
        ruleTypes = setOf(ImportSource.SUSPENDED),
    )

private fun SuspendedArchiveEntity.toRetainedImportSourceEvidence(kanji: String): ImportSourceEvidence =
    ImportSourceEvidence(
        kanji = kanji,
        cardId = CardId(cardId),
        noteId = NoteId(noteId),
        expression = expression,
        reading = reading,
        meaning = meaning,
        sentence = sentence,
        sourceType = ImportSource.SUSPENDED,
        suspended = true,
        forcePractice = true,
        mature = false,
        lapses = 0,
        intervalDays = 0,
        reps = 0,
        fsrsStability = null,
        fsrsDifficulty = null,
        fsrsRetrievability = null,
        ruleTypes = setOf(ImportSource.SUSPENDED),
    )

private fun importReasonCode(ruleTypes: Set<ImportSource>): String = when {
    ruleTypes.size > 1 -> "multiple_import_rules"
    ImportSource.BROWSER_QUERY in ruleTypes -> "browser_query_import"
    ImportSource.SUSPENDED in ruleTypes -> "suspended_import"
    ImportSource.TAGGED in ruleTypes -> "tagged_import"
    ImportSource.WEAK in ruleTypes -> "weak_card_import"
    ImportSource.ACTIVE in ruleTypes -> "active_import"
    else -> "imported"
}

private fun ImportedKanjiCandidate.importReasonText(
    settings: ImportSettings,
    ruleTypes: Set<ImportSource>,
    sourceCount: Int,
): String {
    val rules = if (ruleTypes.isEmpty()) {
        "unknown rule"
    } else {
        ruleTypes.joinToString(" + ") { it.wireName }
    }
    return "Imported by $rules; $sourceCount source card${if (sourceCount == 1) "" else "s"}; " +
        "Jiten rank $jitenRank; rank range ${settings.suspendedRankMin}-${settings.suspendedRankMax}; " +
        "minimum matching cards ${settings.importMinMatchingCardsPerKanji}."
}

private fun ImportSettings.importSettingsJson(): String = buildString {
    append('{')
    append("\"model_name\":").appendJson(noteMapping.noteTypeName)
    append(",\"import_active_cards\":").append(importActiveCards)
    append(",\"import_suspended_cards\":").append(importSuspendedCards)
    append(",\"import_tagged_cards\":").append(importTaggedCards && importTags.isNotEmpty())
    append(",\"import_tags\":")
    appendJsonArray(importTags)
    append(",\"import_weak_cards\":").append(importWeakCards)
    append(",\"import_weak_fsrs_difficulty\":").append(importWeakFsrsDifficultyThreshold)
    append(",\"import_weak_lapses\":").append(importWeakLapsesThreshold)
    append(",\"import_browser_query_cards\":").append(importBrowserQueryCards)
    append(",\"import_browser_query\":").appendJson(importBrowserQuery.trim())
    append(",\"rank_min\":").append(suspendedRankMin)
    append(",\"rank_max\":").append(suspendedRankMax)
    append(",\"min_matching_cards\":").append(importMinMatchingCardsPerKanji)
    append('}')
}

private fun StringBuilder.appendJsonArray(values: List<String>) {
    append('[')
    values.forEachIndexed { index, value ->
        if (index > 0) {
            append(',')
        }
        appendJson(value)
    }
    append(']')
}

private fun StringBuilder.appendJson(value: String): StringBuilder {
    append('"')
    for (character in value) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
    append('"')
    return this
}
