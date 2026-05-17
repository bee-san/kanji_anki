package dev.bee.kanjianki.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaniRoomSchemaParityTest {
    @Test
    fun exportedSchemaContainsCurrentUserDataTables() {
        val tableNames = Regex(""""tableName"\s*:\s*"([^"]+)"""")
            .findAll(schemaText())
            .map { it.groupValues[1] }
            .toList()

        assertEquals(expectedTables, tableNames)
    }

    @Test
    fun exportedSchemaKeepsLegacyIndexesAndDefaults() {
        val schema = schemaText()

        assertTrue(schema.contains("`suspended` INTEGER NOT NULL"))
        assertTrue(schema.contains("`browser_query_matched` INTEGER NOT NULL"))
        assertTrue(schema.contains("PRIMARY KEY(`kanji`, `answer_signature`)"))
        assertTrue(schema.contains("`rung` TEXT NOT NULL DEFAULT 'kanji_meaning'"))
        assertTrue(schema.contains("`phase` TEXT NOT NULL DEFAULT 'new_learning'"))
        assertTrue(schema.contains("idx_kanji_inventory_search"))
        assertTrue(schema.contains("idx_timeline_dedupe"))
        assertTrue(schema.contains("idx_sync_card_snapshots_sync_card"))
        assertTrue(schema.contains("idx_similar_repair_due"))
        assertTrue(schema.contains("idx_study_task_log_answered"))
    }

    private fun schemaText(): String = schemaCandidates()
        .first(File::isFile)
        .readText()

    private fun schemaCandidates(): Sequence<File> = sequenceOf(
        File("schemas/dev.bee.kanjianki.data.KaniRoomDatabase/${KaniRoomDatabase.SCHEMA_VERSION}.json"),
        File("data/schemas/dev.bee.kanjianki.data.KaniRoomDatabase/${KaniRoomDatabase.SCHEMA_VERSION}.json"),
    )

    private companion object {
        val expectedTables = listOf(
            "settings",
            "source_notes",
            "source_cards",
            "sync_runs",
            "suspended_archive",
            "suspended_imports",
            "suspended_sources",
            "import_rule_audits",
            "import_decisions",
            "dashboard_rows",
            "kanji_examples",
            "kanji_inventory",
            "local_kanji_suspensions",
            "study_items",
            "learning_repeats",
            "review_log",
            "study_task_log",
            "similar_kanji_pairs",
            "similar_kanji_choice_state",
            "similar_kanji_repair_queue",
            "similar_kanji_review_log",
            "kanji_timeline_events",
            "sync_card_snapshots",
            "sync_note_snapshots",
            "sync_kanji_snapshots",
        )
    }
}
