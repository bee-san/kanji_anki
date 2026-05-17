package dev.bee.kanjianki.ankidroid

import dev.bee.kanjianki.domain.importing.ImportSourceEvidence
import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.ImportSource
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.sync.SyncErrorCode
import dev.bee.kanjianki.domain.sync.CollectionGatewayException
import dev.bee.kanjianki.domain.sync.CollectionSnapshot
import dev.bee.kanjianki.domain.sync.SyncProgressListener
import dev.bee.kanjianki.domain.sync.SyncProgressSnapshot
import dev.bee.kanjianki.domain.sync.SyncProgressStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class AnkiDroidCollectionGatewayTest {
    @Test
    fun readCollectionMapsNotesCardsAndBrowserQueryMatches() = runBlocking {
        val provider = FakeProviderClient()
        provider.rows("models") {
            row("_id" to "7", "name" to "Kiku", "field_names" to kikuFields())
        }
        provider.rows("notes", selection = """note:"Kiku"""") {
            row("_id" to "10", "mid" to "7", "flds" to kikuValues("日本"), "tags" to "mined")
            row("_id" to "99", "mid" to "7", "flds" to kikuValues("古い"), "tags" to "kani_archived")
        }
        provider.rows("notes", selection = """note:"Kiku" (tag:focus)""") {
            row("_id" to "10", "mid" to "7")
        }
        provider.rows("notes", selection = """note:"Kiku" is:suspended""") {}
        provider.rows("notes/10/cards") {
            row(
                "_id" to "100",
                "note_id" to "10",
                "ord" to "0",
                "deck_id" to "Mining",
                "queue" to "0",
                "type" to "2",
                "due" to "5",
                "interval" to "21",
                "reps" to "3",
                "lapses" to "1",
                "fsrs_stability" to "4.5",
                "fsrs_difficulty" to "6.5",
                "fsrs_retrievability" to "0.72",
            )
        }
        val progressEvents = mutableListOf<SyncProgressSnapshot>()

        val snapshot = AnkiDroidCollectionGateway(provider).readCollection(
            ImportSettings(
                importBrowserQueryCards = true,
                importBrowserQuery = " tag:focus ",
            ),
            SyncProgressListener { progressEvents += it },
        )

        assertEquals(listOf(10L), snapshot.notes.map { it.noteId.value })
        assertEquals("日本", snapshot.notes.single().expression)
        assertEquals("にほん", snapshot.notes.single().reading)
        assertEquals("Japan", snapshot.notes.single().meaning)
        assertTrue(snapshot.notes.single().fieldsJson.contains("Expression"))
        assertEquals(listOf(100L), snapshot.cards.map { it.cardId.value })
        assertEquals("Mining", snapshot.cards.single().deckName)
        assertFalse(snapshot.cards.single().suspended)
        assertTrue(snapshot.cards.single().browserQueryMatched)
        assertEquals(4.5, snapshot.cards.single().fsrsStability!!, 0.0)
        assertEquals(6.5, snapshot.cards.single().fsrsDifficulty!!, 0.0)
        assertEquals(0.72, snapshot.cards.single().fsrsRetrievability!!, 0.0)
        assertEquals(
            listOf(
                SyncProgressStage.FINDING_NOTE_TYPE,
                SyncProgressStage.READING_NOTES,
                SyncProgressStage.SCANNING_CARDS,
                SyncProgressStage.SCANNING_CARDS,
                SyncProgressStage.SCANNING_CARDS,
            ),
            progressEvents.map { it.stage },
        )
        assertEquals(1, progressEvents.last().scannedCards)
        assertEquals(1, progressEvents.last().totalCards)
        assertFalse(progressEvents[2].totalKnown)
    }

    @Test
    fun fallsBackToNotesV2WhenProviderSearchFails() = runBlocking {
        val provider = FakeProviderClient()
        provider.rows("models") {
            row("_id" to "7", "name" to "Kiku", "field_names" to kikuFields())
        }
        provider.failure("notes", selection = """note:"Kiku"""", RuntimeException("search unsupported"))
        provider.rows("notes_v2", selection = "mid=?") {
            row("_id" to "10", "mid" to "7", "flds" to kikuValues("日本"), "tags" to "")
        }
        provider.rows("notes", selection = """note:"Kiku" is:suspended""") {}
        provider.rows("notes/10/cards") {
            row("note_id" to "10", "ord" to "0", "deck_id" to "Mining")
        }

        val snapshot = AnkiDroidCollectionGateway(provider).readCollection(ImportSettings())

        assertEquals(listOf(10L), snapshot.notes.map { it.noteId.value })
        assertEquals(listOf(10000L), snapshot.cards.map { it.cardId.value })
    }

    @Test
    fun browserQueryProviderFailureIsPermanentConfiguration() = runBlocking {
        val provider = FakeProviderClient()
        provider.rows("models") {
            row("_id" to "7", "name" to "Kiku", "field_names" to kikuFields())
        }
        provider.rows("notes", selection = """note:"Kiku"""") {
            row("_id" to "10", "mid" to "7", "flds" to kikuValues("日本"), "tags" to "")
        }
        provider.failure(
            "notes",
            selection = """note:"Kiku" (bad query)""",
            error = RuntimeException("invalid search"),
        )

        val error = runCatching {
            AnkiDroidCollectionGateway(provider).readCollection(
                ImportSettings(
                    importBrowserQueryCards = true,
                    importBrowserQuery = "bad query",
                ),
            )
        }.exceptionOrNull() as CollectionGatewayException

        assertTrue(error.permanent)
        assertEquals(SyncErrorCode.PERMANENT_CONFIGURATION, error.errorCode)
    }

    @Test
    fun coroutineCancellationIsRethrown() {
        val provider = FakeProviderClient()
        provider.rows("models") {
            row("_id" to "7", "name" to "Kiku", "field_names" to kikuFields())
        }
        provider.failure("notes", selection = """note:"Kiku"""", CancellationException("cancelled"))

        val error = assertThrows(CancellationException::class.java) {
            runBlocking {
                AnkiDroidCollectionGateway(provider).readCollection(ImportSettings())
            }
        }

        assertEquals("cancelled", error.message)
    }

    @Test
    fun rejectsUnsupportedTemplateOrdAsPermanentConfiguration() = runBlocking {
        val provider = FakeProviderClient()
        provider.rows("models") {
            row("_id" to "7", "name" to "Kiku", "field_names" to kikuFields())
        }
        provider.rows("notes", selection = """note:"Kiku"""") {
            row("_id" to "10", "mid" to "7", "flds" to kikuValues("日本"), "tags" to "")
        }
        provider.rows("notes", selection = """note:"Kiku" is:suspended""") {}
        provider.rows("notes/10/cards") {
            row("_id" to "100", "note_id" to "10", "ord" to "1", "deck_id" to "Mining")
        }

        val error = runCatching {
            AnkiDroidCollectionGateway(provider).readCollection(ImportSettings())
        }.exceptionOrNull() as CollectionGatewayException

        assertTrue(error.permanent)
        assertEquals(SyncErrorCode.PERMANENT_CONFIGURATION, error.errorCode)
    }

    @Test
    fun missingPermissionIsPermanentPermissionFailure() = runBlocking {
        val provider = FakeProviderClient(permissionGranted = false)

        val error = runCatching {
            AnkiDroidCollectionGateway(provider).readCollection(ImportSettings())
        }.exceptionOrNull() as CollectionGatewayException

        assertTrue(error.permanent)
        assertEquals(SyncErrorCode.PERMANENT_PERMISSION, error.errorCode)
    }

    @Test
    fun archiveSelectedSuspendedCardsTagsFullySelectedSuspendedNotes() = runBlocking {
        val provider = FakeProviderClient()
        provider.rows("notes/10") {
            row("tags" to "leech")
        }

        val summary = AnkiDroidCollectionGateway(provider).archiveSelectedSuspendedCards(
            snapshot = CollectionSnapshot(
                notes = emptyList(),
                cards = listOf(sourceCard(cardId = 100, noteId = 10, suspended = true)),
            ),
            importCandidates = listOf(importCandidate(cardId = 100, noteId = 10)),
        )

        assertEquals(1, summary.sourceCards)
        assertEquals(1, summary.taggedNotes)
        assertEquals(mapOf("tags" to "leech kani_archived"), provider.updatedValues.single())
        assertTrue(summary.message.contains("tagged in AnkiDroid"))
    }

    @Test
    fun archiveSelectedSuspendedCardsKeepsPartiallySuspendedNotesLocal() = runBlocking {
        val provider = FakeProviderClient()

        val summary = AnkiDroidCollectionGateway(provider).archiveSelectedSuspendedCards(
            snapshot = CollectionSnapshot(
                notes = emptyList(),
                cards = listOf(
                    sourceCard(cardId = 100, noteId = 10, suspended = true),
                    sourceCard(cardId = 101, noteId = 10, suspended = false),
                ),
            ),
            importCandidates = listOf(importCandidate(cardId = 100, noteId = 10)),
        )

        assertEquals(1, summary.sourceCards)
        assertEquals(0, summary.taggedNotes)
        assertTrue(provider.updatedValues.isEmpty())
        assertTrue(summary.message.contains("kept in the local archive"))
    }

    private class FakeProviderClient(
        private val permissionGranted: Boolean = true,
    ) : AnkiDroidProviderClient {
        private val target = AnkiDroidProviderTarget("fake.authority", "permission")
        private val responses = linkedMapOf<QueryKey, List<Map<String, String>>>()
        private val failures = linkedMapOf<QueryKey, RuntimeException>()
        val updatedValues = mutableListOf<Map<String, String>>()

        override fun resolveTarget(targets: List<AnkiDroidProviderTarget>): AnkiDroidProviderTarget = target

        override fun hasPermission(permission: String?): Boolean = permissionGranted

        override fun query(
            authority: String,
            pathSegments: List<String>,
            projection: List<String>?,
            selection: String?,
            selectionArgs: List<String>?,
        ): AnkiDroidCursor? {
            val key = QueryKey(pathSegments.joinToString("/"), selection)
            failures[key]?.let { throw it }
            return FakeCursor(responses[key].orEmpty())
        }

        override fun update(
            authority: String,
            pathSegments: List<String>,
            values: Map<String, String>,
            selection: String?,
            selectionArgs: List<String>?,
        ): Int {
            updatedValues += values
            return 1
        }

        fun rows(
            path: String,
            selection: String? = null,
            block: MutableList<Map<String, String>>.() -> Unit,
        ) {
            responses[QueryKey(path, selection)] = buildList(block)
        }

        fun failure(
            path: String,
            selection: String?,
            error: RuntimeException,
        ) {
            failures[QueryKey(path, selection)] = error
        }
    }

    private class FakeCursor(
        private val rows: List<Map<String, String>>,
    ) : AnkiDroidCursor {
        private var index = -1

        override fun moveToNext(): Boolean {
            index++
            return index < rows.size
        }

        override fun string(column: String): String? = rows[index][column]

        override fun close() = Unit
    }

    private data class QueryKey(
        val path: String,
        val selection: String?,
    )

    private fun MutableList<Map<String, String>>.row(vararg values: Pair<String, String>) {
        add(values.toMap())
    }

    private fun kikuFields(): String = listOf(
        "Expression",
        "ExpressionReading",
        "MainDefinition",
        "Sentence",
        "Frequency",
        "FreqSort",
    ).joinToString(FIELD_SEPARATOR)

    private fun kikuValues(expression: String): String = listOf(
        expression,
        "にほん",
        "Japan",
        "${expression}へ行く。",
        "100",
        "100",
    ).joinToString(FIELD_SEPARATOR)

    private fun sourceCard(
        cardId: Long,
        noteId: Long,
        suspended: Boolean,
    ): SourceCard = SourceCard(
        cardId = CardId(cardId),
        noteId = NoteId(noteId),
        deckName = "Mining",
        ord = 0,
        queue = if (suspended) -1 else 0,
        type = 2,
        due = 0,
        intervalDays = 0,
        reps = 0,
        lapses = 0,
        suspended = suspended,
        browserQueryMatched = false,
        fsrsStability = null,
        fsrsDifficulty = null,
        fsrsRetrievability = null,
        lastSeenSyncId = SyncRunId(0),
    )

    private fun importCandidate(
        cardId: Long,
        noteId: Long,
    ): ImportedKanjiCandidate = ImportedKanjiCandidate(
        kanji = "日",
        jitenRank = 100,
        rankRangeMax = 3000,
        sources = listOf(
            ImportSourceEvidence(
                kanji = "日",
                cardId = CardId(cardId),
                noteId = NoteId(noteId),
                expression = "日本",
                reading = "にほん",
                meaning = "Japan",
                sentence = "日本へ行く。",
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
            ),
        ),
    )

    private companion object {
        const val FIELD_SEPARATOR = "\u001f"
    }
}
