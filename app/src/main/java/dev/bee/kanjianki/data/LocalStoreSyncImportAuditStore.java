package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.syncdomain.ImportAuditBuilder;

import java.util.ArrayList;
import java.util.List;

final class LocalStoreSyncImportAuditStore {
    void saveImportAudit(
            SQLiteDatabase db,
            List<RecordsImportModels.SuspendedImport> imports,
            RecordsSyncModels.Settings settings,
            long finishedAt,
            long syncId
    ) {
        saveImportRuleAudit(db, settings, finishedAt, syncId);
        for (RecordsImportModels.SuspendedImport imported : imports) {
            saveImportDecision(db, imported, settings, finishedAt, syncId);
        }
    }

    void saveImportRuleAudit(SQLiteDatabase db, RecordsSyncModels.Settings settings, long finishedAt, long syncId) {
        ImportAuditBuilder.SettingsSnapshot snapshot = importSettings(settings);
        ImportAuditBuilder.RuleAudit audit = ImportAuditBuilder.ruleAudit(snapshot);
        ContentValues values = new ContentValues();
        values.put(LocalStoreBase.COLUMN_SYNC_ID, syncId);
        values.put(LocalStoreBase.COLUMN_CREATED_AT, finishedAt);
        values.put(LocalStoreBase.COLUMN_MODEL_NAME, settings.modelName);
        values.put(LocalStoreBase.COLUMN_ENABLED_SOURCES, String.join(" ", audit.enabledSources()));
        values.put("rank_min", settings.suspendedRankMin);
        values.put("rank_max", settings.suspendedRankMax);
        values.put("min_matching_cards", settings.importMinMatchingCardsPerKanji);
        values.put("import_tags", settings.importTagsText());
        values.put("weak_fsrs_difficulty", settings.importWeakFsrsDifficultyThreshold);
        values.put("weak_lapses", settings.importWeakLapsesThreshold);
        values.put("browser_query", settings.normalizedBrowserQuery());
        values.put(LocalStoreBase.COLUMN_SETTINGS_JSON, audit.settingsJson());
        db.insertWithOnConflict(LocalStoreBase.TABLE_IMPORT_RULE_AUDITS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void saveImportDecision(
            SQLiteDatabase db,
            RecordsImportModels.SuspendedImport imported,
            RecordsSyncModels.Settings settings,
            long finishedAt,
            long syncId
    ) {
        ImportAuditBuilder.ImportDecisionAudit decision = ImportAuditBuilder.decision(importCandidate(imported), importSettings(settings));

        ContentValues values = new ContentValues();
        values.put(LocalStoreBase.COLUMN_SYNC_ID, syncId);
        values.put(LocalStoreBase.COLUMN_KANJI, imported.kanji);
        values.put(LocalStoreBase.COLUMN_DECISION, "imported");
        values.put(LocalStoreBase.COLUMN_REASON_CODE, decision.reasonCode());
        values.put(LocalStoreBase.COLUMN_REASON_TEXT, decision.reasonText());
        if (imported.jitenRank != null) {
            values.put(LocalStoreBase.COLUMN_JITEN_RANK, imported.jitenRank);
        }
        values.put(LocalStoreBase.COLUMN_RANK_KNOWN, imported.rankKnown ? 1 : 0);
        values.put("rank_min", settings.suspendedRankMin);
        values.put("rank_max", settings.suspendedRankMax);
        values.put("min_matching_cards", settings.importMinMatchingCardsPerKanji);
        values.put(LocalStoreBase.COLUMN_SOURCE_COUNT, decision.sourceCount());
        values.put(LocalStoreBase.COLUMN_SOURCE_TYPES, String.join(" ", decision.sourceTypes()));
        values.put(LocalStoreBase.COLUMN_RULE_TYPES, String.join(" ", decision.ruleTypes()));
        values.put(LocalStoreBase.COLUMN_SOURCE_CARD_IDS, decision.sourceCardIds());
        values.put(LocalStoreBase.COLUMN_SOURCE_NOTE_IDS, decision.sourceNoteIds());
        values.put(LocalStoreBase.COLUMN_CREATED_AT, finishedAt);
        db.insertWithOnConflict(LocalStoreBase.TABLE_IMPORT_DECISIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private ImportAuditBuilder.ImportCandidate importCandidate(RecordsImportModels.SuspendedImport imported) {
        List<ImportAuditBuilder.ImportSource> sources = new ArrayList<>();
        for (RecordsImportModels.SuspendedSource source : imported.sources) {
            sources.add(new ImportAuditBuilder.ImportSource(source.cardId, source.noteId, source.sourceType, source.ruleTypes));
        }
        return new ImportAuditBuilder.ImportCandidate(imported.kanji, imported.jitenRank, imported.rankKnown, sources);
    }

    private ImportAuditBuilder.SettingsSnapshot importSettings(RecordsSyncModels.Settings settings) {
        return new ImportAuditBuilder.SettingsSnapshot(
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
                settings.suspendedRankMax
        );
    }
}
