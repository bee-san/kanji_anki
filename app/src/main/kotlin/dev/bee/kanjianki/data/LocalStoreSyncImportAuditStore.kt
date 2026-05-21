package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncdomain.ImportAuditBuilder

internal class LocalStoreSyncImportAuditStore {
    fun saveImportAudit(
        db: SQLiteDatabase,
        imports: List<RecordsImportModels.SuspendedImport>,
        settings: RecordsSyncModels.Settings,
        finishedAt: Long,
        syncId: Long,
    ) {
        saveImportRuleAudit(db, settings, finishedAt, syncId)
        for (imported in imports) {
            saveImportDecision(db, imported, settings, finishedAt, syncId)
        }
    }

    fun saveImportRuleAudit(
        db: SQLiteDatabase,
        settings: RecordsSyncModels.Settings,
        finishedAt: Long,
        syncId: Long,
    ) {
        val snapshot = importSettings(settings)
        val audit = ImportAuditBuilder.ruleAudit(snapshot)
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_SYNC_ID, syncId)
        values.put(LocalStoreBase.COLUMN_CREATED_AT, finishedAt)
        values.put(LocalStoreBase.COLUMN_MODEL_NAME, settings.modelName)
        values.put(LocalStoreBase.COLUMN_ENABLED_SOURCES, audit.enabledSources().joinToString(" "))
        values.put("rank_min", settings.suspendedRankMin)
        values.put("rank_max", settings.suspendedRankMax)
        values.put("min_matching_cards", settings.importMinMatchingCardsPerKanji)
        values.put("import_tags", settings.importTagsText())
        values.put("weak_fsrs_difficulty", settings.importWeakFsrsDifficultyThreshold)
        values.put("weak_lapses", settings.importWeakLapsesThreshold)
        values.put("browser_query", settings.normalizedBrowserQuery())
        values.put(LocalStoreBase.COLUMN_SETTINGS_JSON, audit.settingsJson())
        db.insertWithOnConflict(
            LocalStoreBase.TABLE_IMPORT_RULE_AUDITS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun saveImportDecision(
        db: SQLiteDatabase,
        imported: RecordsImportModels.SuspendedImport,
        settings: RecordsSyncModels.Settings,
        finishedAt: Long,
        syncId: Long,
    ) {
        val decision = ImportAuditBuilder.decision(importCandidate(imported), importSettings(settings))
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_SYNC_ID, syncId)
        values.put(LocalStoreBase.COLUMN_KANJI, imported.kanji)
        values.put(LocalStoreBase.COLUMN_DECISION, "imported")
        values.put(LocalStoreBase.COLUMN_REASON_CODE, decision.reasonCode())
        values.put(LocalStoreBase.COLUMN_REASON_TEXT, decision.reasonText())
        imported.jitenRank?.let { values.put(LocalStoreBase.COLUMN_JITEN_RANK, it) }
        values.put(LocalStoreBase.COLUMN_RANK_KNOWN, if (imported.rankKnown) 1 else 0)
        values.put("rank_min", settings.suspendedRankMin)
        values.put("rank_max", settings.suspendedRankMax)
        values.put("min_matching_cards", settings.importMinMatchingCardsPerKanji)
        values.put(LocalStoreBase.COLUMN_SOURCE_COUNT, decision.sourceCount())
        values.put(LocalStoreBase.COLUMN_SOURCE_TYPES, decision.sourceTypes().joinToString(" "))
        values.put(LocalStoreBase.COLUMN_RULE_TYPES, decision.ruleTypes().joinToString(" "))
        values.put(LocalStoreBase.COLUMN_SOURCE_CARD_IDS, decision.sourceCardIds())
        values.put(LocalStoreBase.COLUMN_SOURCE_NOTE_IDS, decision.sourceNoteIds())
        values.put(LocalStoreBase.COLUMN_CREATED_AT, finishedAt)
        db.insertWithOnConflict(
            LocalStoreBase.TABLE_IMPORT_DECISIONS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun importCandidate(imported: RecordsImportModels.SuspendedImport): ImportAuditBuilder.ImportCandidate {
        val sources = imported.sources.map { source ->
            ImportAuditBuilder.ImportSource(source.cardId, source.noteId, source.sourceType, source.ruleTypes)
        }
        return ImportAuditBuilder.ImportCandidate(imported.kanji, imported.jitenRank, imported.rankKnown, sources)
    }

    private fun importSettings(settings: RecordsSyncModels.Settings): ImportAuditBuilder.SettingsSnapshot {
        return ImportAuditBuilder.SettingsSnapshot(
            settings.modelName,
            settings.importActiveCards,
            settings.importSuspendedCards,
            settings.importTaggedCardsEnabled(),
            settings.importTags,
            settings.importWeakCards,
            settings.importWeakFsrsDifficultyThreshold,
            settings.importWeakLapsesThreshold,
            settings.importMinMatchingCardsPerKanji,
            settings.importBrowserQueryCards,
            settings.normalizedBrowserQuery(),
            settings.suspendedRankMin,
            settings.suspendedRankMax,
        )
    }
}
