package dev.bee.kanjianki.data

import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncdomain.ImportRuleMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreSyncImportAuditStoreTest {
    @Test
    fun saveImportAuditRedactsBrowserQueryFromStoredAuditRows() {
        val privateQuery = "deck:Private Sentence Tag rated:30:1"
        val db = SQLiteDatabase.create(null)
        try {
            LocalStoreTableCreator.createImportAuditTables(db)
            LocalStoreSyncImportAuditStore().saveImportAudit(
                db,
                listOf(browserQueryImport()),
                settings(privateQuery),
                finishedAt = 123L,
                syncId = 7L,
            )

            val rule = row(
                db,
                "import_rule_audits",
                "browser_query",
                "settings_json",
                "enabled_sources",
            )
            assertEquals("[redacted]", rule["browser_query"])
            assertEquals(ImportRuleMatch.SOURCE_BROWSER_QUERY, rule["enabled_sources"])
            assertFalse(rule["settings_json"]!!.contains(privateQuery))
            assertEquals(
                "{\"model_name\":\"Kiku\",\"import_active_cards\":false,\"import_suspended_cards\":false,\"import_tagged_cards\":false,\"import_tags\":[],\"import_weak_cards\":false,\"import_weak_fsrs_difficulty\":7.0,\"import_weak_lapses\":2,\"import_browser_query_cards\":true,\"import_browser_query\":\"[redacted]\",\"rank_min\":100,\"rank_max\":3000,\"min_matching_cards\":1}",
                rule["settings_json"],
            )

            val decision = row(db, "import_decisions", "reason_code", "reason_text", "rule_types")
            assertEquals("browser_query_import", decision["reason_code"])
            assertEquals(ImportRuleMatch.SOURCE_BROWSER_QUERY, decision["rule_types"])
            assertFalse(decision["reason_text"]!!.contains(privateQuery))
        } finally {
            db.close()
        }
    }

    private fun browserQueryImport(): RecordsImportModels.SuspendedImport {
        val source = RecordsImportModels.SuspendedSource(
            "私",
            10L,
            20L,
            "私",
            "わたし",
            "I",
            RecordsImportModels.SuspendedSourceDetails.builder("private sentence")
                .sourceType(ImportRuleMatch.SOURCE_BROWSER_QUERY)
                .suspended(false)
                .ruleTypes(listOf(ImportRuleMatch.SOURCE_BROWSER_QUERY))
                .build(),
        )
        return RecordsImportModels.SuspendedImport("私", 999, true, 3000, listOf(source))
    }

    private fun settings(browserQuery: String): RecordsSyncModels.Settings = RecordsSyncModels.Settings(
        "Kiku", "Mining", "Expression", "Reading", "Meaning", "Sentence",
        "Frequency", "FreqSort", 21, 2, 100, 3000, 24, 3, 3, 3, 3,
        false, false, false, emptyList<String>(), false, 7.0, 2, 1,
        true, browserQuery,
    )

    private fun row(db: SQLiteDatabase, table: String, vararg columns: String): Map<String, String> {
        db.query(table, columns, null, null, null, null, null).use { cursor ->
            check(cursor.moveToFirst()) { "Expected one row in $table" }
            return columns.associateWith { column -> cursor.getString(cursor.getColumnIndexOrThrow(column)) }
        }
    }
}
