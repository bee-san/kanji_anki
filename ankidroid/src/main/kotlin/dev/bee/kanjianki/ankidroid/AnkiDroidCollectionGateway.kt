package dev.bee.kanjianki.ankidroid

import android.content.ContentValues
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.OperationCanceledException
import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.model.CardId
import dev.bee.kanjianki.domain.model.NoteId
import dev.bee.kanjianki.domain.model.SyncRunId
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.source.SourceCard
import dev.bee.kanjianki.domain.model.source.SourceNote
import dev.bee.kanjianki.domain.model.sync.SyncErrorCode
import dev.bee.kanjianki.domain.sync.CollectionGateway
import dev.bee.kanjianki.domain.sync.CollectionGatewayException
import dev.bee.kanjianki.domain.sync.CollectionSnapshot
import dev.bee.kanjianki.domain.sync.NoOpSyncProgressListener
import dev.bee.kanjianki.domain.sync.SuspendedCardArchiveGateway
import dev.bee.kanjianki.domain.sync.SuspendedCardArchiveSummary
import dev.bee.kanjianki.domain.sync.SyncProgressListener
import dev.bee.kanjianki.domain.sync.SyncProgressSnapshot
import dev.bee.kanjianki.domain.sync.SyncProgressStage
import dev.bee.kanjianki.domain.sync.reportSyncProgress
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

class AnkiDroidCollectionGateway(
    private val provider: AnkiDroidProviderClient,
    private val targets: List<AnkiDroidProviderTarget> = AnkiDroidProviderTarget.defaults,
) : CollectionGateway, SuspendedCardArchiveGateway {
    constructor(context: Context) : this(AndroidAnkiDroidProviderClient(context.applicationContext))

    override suspend fun readCollection(settings: ImportSettings): CollectionSnapshot =
        readCollection(settings, NoOpSyncProgressListener)

    override suspend fun readCollection(
        settings: ImportSettings,
        progress: SyncProgressListener,
    ): CollectionSnapshot {
        val target = provider.resolveTarget(targets)
            ?: throw CollectionGatewayException(
                errorCode = SyncErrorCode.PERMANENT_CONFIGURATION,
                permanent = true,
                message = "AnkiDroid's flashcard provider is not installed.",
            )
        if (!provider.hasPermission(target.permission)) {
            throw CollectionGatewayException(
                errorCode = SyncErrorCode.PERMANENT_PERMISSION,
                permanent = true,
                message = "AnkiDroid permission is missing: ${target.permission}",
            )
        }
        try {
            progress.reportSyncProgress(SyncProgressSnapshot.atStage(SyncProgressStage.FINDING_NOTE_TYPE))
            val mapping = findConfiguredModel(target, settings)
            progress.reportSyncProgress(SyncProgressSnapshot.atStage(SyncProgressStage.READING_NOTES))
            val notes = queryNotes(target, mapping, settings).toMutableMap()
            val browserQueryNoteIds = queryBrowserQueryNoteIds(target, mapping, settings)
            if (browserQueryNoteIds.any { it !in notes.keys }) {
                notes.putAll(queryNotesBySearch(target, mapping, settings, configuredBrowserQuerySearch(settings)))
            }
            progress.reportSyncProgress(SyncProgressSnapshot.atStage(SyncProgressStage.SCANNING_CARDS))
            val cards = queryCardsByNote(target, settings, notes.keys, progress)
                .filter { it.noteId.value in notes.keys }
                .also { validateTemplateCards(it, settings) }
                .map { card ->
                    if (card.noteId.value in browserQueryNoteIds) {
                        card.copy(browserQueryMatched = true)
                    } else {
                        card
                    }
                }
            return CollectionSnapshot(notes = notes.values.toList(), cards = cards)
        } catch (error: CollectionGatewayException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: OperationCanceledException) {
            throw CollectionGatewayException(
                errorCode = SyncErrorCode.RETRYABLE,
                permanent = false,
                message = "Timed out while reading AnkiDroid.",
                cause = error,
            )
        } catch (error: SecurityException) {
            throw CollectionGatewayException(
                errorCode = SyncErrorCode.PERMANENT_PERMISSION,
                permanent = true,
                message = "AnkiDroid denied database access.",
                cause = error,
            )
        } catch (error: Exception) {
            throw CollectionGatewayException(
                errorCode = SyncErrorCode.RETRYABLE,
                permanent = false,
                message = "AnkiDroid provider read failed: ${error.message}",
                cause = error,
            )
        }
    }

    override suspend fun archiveSelectedSuspendedCards(
        snapshot: CollectionSnapshot,
        importCandidates: List<ImportedKanjiCandidate>,
    ): SuspendedCardArchiveSummary {
        val target = provider.resolveTarget(targets)
            ?: return SuspendedCardArchiveSummary(0, 0, "No provider removal attempted.")
        if (snapshot.cards.isEmpty()) {
            return SuspendedCardArchiveSummary(0, 0, "No provider removal attempted.")
        }
        val index = SuspendedCardArchiveIndex.from(snapshot.cards, selectedSuspendedCardIds(importCandidates))
        if (index.suspendedCards.isEmpty()) {
            return SuspendedCardArchiveSummary(0, 0, "No suspended cards needed provider cleanup.")
        }

        var tagged = 0
        var failed = index.partiallySuspendedCardCount()
        for (noteId in index.notesFullySuspended()) {
            if (tagNoteArchived(target, noteId)) {
                tagged++
            } else {
                failed++
            }
        }
        return SuspendedCardArchiveSummary(
            sourceCards = index.suspendedCards.size,
            taggedNotes = tagged,
            message = removalMessage(tagged, failed),
        )
    }

    private fun findConfiguredModel(
        target: AnkiDroidProviderTarget,
        settings: ImportSettings,
    ): ModelMapping {
        val configuredName = settings.noteMapping.noteTypeName
        for (noteType in queryNoteTypes(target)) {
            if (!noteType.name.equals(configuredName, ignoreCase = true)) {
                continue
            }
            val missing = settings.requiredFields().filter { it !in noteType.fields }
            if (missing.isNotEmpty()) {
                throw CollectionGatewayException(
                    errorCode = SyncErrorCode.PERMANENT_CONFIGURATION,
                    permanent = true,
                    message = "$configuredName is missing required fields: ${missing.joinToString()}",
                )
            }
            return ModelMapping(noteType.modelId, noteType.name, noteType.fields)
        }
        throw CollectionGatewayException(
            errorCode = SyncErrorCode.PERMANENT_CONFIGURATION,
            permanent = true,
            message = "$configuredName note type was not found in AnkiDroid.",
        )
    }

    private fun queryNoteTypes(target: AnkiDroidProviderTarget): List<NoteType> =
        provider.query(target.authority, listOf("models"), projection = null, selection = null, selectionArgs = null)
            .useRows("AnkiDroid returned no note model cursor.") { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            NoteType(
                                modelId = cursor.long(COLUMN_ID, 0),
                                name = cursor.string(COLUMN_NAME).orEmpty(),
                                fields = splitFields(cursor.string(COLUMN_FIELD_NAMES).orEmpty()),
                            ),
                        )
                    }
                }.sortedWith(
                    compareBy<NoteType> { !it.name.equals(DEFAULT_MODEL_NAME, ignoreCase = true) }
                        .thenBy { it.name.lowercase(Locale.ROOT) },
                )
            }

    private fun queryNotes(
        target: AnkiDroidProviderTarget,
        mapping: ModelMapping,
        settings: ImportSettings,
    ): Map<Long, SourceNote> {
        return try {
            queryNotesBySearch(target, mapping, settings, "note:\"${settings.noteMapping.noteTypeName}\"")
        } catch (searchError: CancellationException) {
            throw searchError
        } catch (searchError: Exception) {
            try {
                queryNotesBySql(target, mapping, settings)
            } catch (sqlError: CollectionGatewayException) {
                sqlError.addSuppressed(searchError)
                throw sqlError
            }
        }
    }

    private fun queryNotesBySearch(
        target: AnkiDroidProviderTarget,
        mapping: ModelMapping,
        settings: ImportSettings,
        search: String,
    ): Map<Long, SourceNote> =
        provider.query(target.authority, listOf("notes"), projection = null, selection = search, selectionArgs = null)
            .useRows("AnkiDroid returned no configured note cursor.") { cursor ->
                readNotesFromCursor(cursor, mapping, settings, filterModelId = true)
            }

    private fun queryNotesBySql(
        target: AnkiDroidProviderTarget,
        mapping: ModelMapping,
        settings: ImportSettings,
    ): Map<Long, SourceNote> =
        provider.query(
            target.authority,
            listOf("notes_v2"),
            projection = null,
            selection = "mid=?",
            selectionArgs = listOf(mapping.modelId.toString()),
        ).useRows("AnkiDroid returned no configured note cursor.") { cursor ->
            readNotesFromCursor(cursor, mapping, settings, filterModelId = false)
        }

    private fun readNotesFromCursor(
        cursor: AnkiDroidCursor,
        mapping: ModelMapping,
        settings: ImportSettings,
        filterModelId: Boolean,
    ): Map<Long, SourceNote> {
        val notes = linkedMapOf<Long, SourceNote>()
        while (cursor.moveToNext()) {
            val noteId = cursor.long(COLUMN_ID, 0)
            val modelId = cursor.long(COLUMN_MODEL_ID, mapping.modelId)
            if (filterModelId && modelId != mapping.modelId) {
                continue
            }
            val tags = splitTags(cursor.string(COLUMN_TAGS).orEmpty())
            if (tags.any { it == ARCHIVED_TAG || it == LEGACY_ARCHIVED_TAG }) {
                continue
            }
            val fieldValues = splitFields(cursor.string(COLUMN_FIELDS).orEmpty())
            val fields = selectRequiredFields(mapping.fields, fieldValues, settings)
            notes[noteId] = SourceNote(
                noteId = NoteId(noteId),
                modelName = mapping.name,
                expression = fields[settings.noteMapping.expressionField].orEmpty(),
                reading = fields[settings.noteMapping.readingField].orEmpty(),
                meaning = fields[settings.noteMapping.meaningField].orEmpty(),
                sentence = fields[settings.noteMapping.sentenceField].orEmpty(),
                fieldsJson = fields.toJsonObject(),
                tags = tags.joinToString(" "),
                lastSeenSyncId = SyncRunId(0),
            )
        }
        return notes
    }

    private fun queryBrowserQueryNoteIds(
        target: AnkiDroidProviderTarget,
        mapping: ModelMapping,
        settings: ImportSettings,
    ): Set<Long> {
        if (!settings.browserQueryImportEnabled()) {
            return emptySet()
        }
        val search = configuredBrowserQuerySearch(settings)
        return try {
            provider.query(target.authority, listOf("notes"), projection = null, selection = search, selectionArgs = null)
                .useRows(message = null) { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) {
                            if (cursor.long(COLUMN_MODEL_ID, mapping.modelId) == mapping.modelId) {
                                add(cursor.long(COLUMN_ID, 0))
                            }
                        }
                    }
                }
        } catch (error: CancellationException) {
            throw error
        } catch (error: OperationCanceledException) {
            throw error
        } catch (error: SecurityException) {
            throw error
        } catch (error: CollectionGatewayException) {
            throw error
        } catch (error: Exception) {
            throw CollectionGatewayException(
                errorCode = SyncErrorCode.PERMANENT_CONFIGURATION,
                permanent = true,
                message = "AnkiDroid could not run the browser query. Check the query in Import filters.",
                cause = error,
            )
        }
    }

    private fun queryCardsByNote(
        target: AnkiDroidProviderTarget,
        settings: ImportSettings,
        noteIds: Set<Long>,
        progress: SyncProgressListener,
    ): List<SourceCard> {
        val suspendedNoteIds = querySuspendedNoteIds(target, settings)
        val cards = mutableListOf<SourceCard>()
        val total = noteIds.size
        var scanned = 0
        progress.reportSyncProgress(SyncProgressSnapshot.cardsScanned(scanned, total))
        var projectionIndex = 0
        val projections = listOf(CARD_COLUMNS_WITH_FSRS, CARD_COLUMNS_WITH_SCHEDULER, CARD_COLUMNS_MINIMAL)
        for (noteId in noteIds) {
            var lastError: Exception? = null
            while (projectionIndex < projections.size) {
                try {
                    cards += queryCardsForNote(target, noteId, suspendedNoteIds, projections[projectionIndex])
                    scanned++
                    progress.reportSyncProgress(SyncProgressSnapshot.cardsScanned(scanned, total))
                    lastError = null
                    break
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    lastError = error
                    projectionIndex++
                }
            }
            if (lastError != null) {
                throw CollectionGatewayException(
                    errorCode = SyncErrorCode.RETRYABLE,
                    permanent = false,
                    message = "AnkiDroid card projection failed: ${lastError.message}",
                    cause = lastError,
                )
            }
        }
        return cards
    }

    private fun queryCardsForNote(
        target: AnkiDroidProviderTarget,
        noteId: Long,
        suspendedNoteIds: Set<Long>,
        columns: List<String>,
    ): List<SourceCard> =
        provider.query(target.authority, listOf("notes", noteId.toString(), "cards"), columns, null, null)
            .useRows("AnkiDroid returned no per-note card cursor.") { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val ord = cursor.int(COLUMN_ORD, 0)
                        val suspendedFromSearch = noteId in suspendedNoteIds
                        val queue = cursor.int(COLUMN_QUEUE, if (suspendedFromSearch) -1 else 0)
                        val suspended = suspendedFromSearch || queue < 0
                        val deckId = cursor.string(COLUMN_DECK_ID).orEmpty()
                        val fsrs = fsrsMemoryState(cursor)
                        add(
                            SourceCard(
                                cardId = CardId(cursor.long(COLUMN_CARD_ID, noteId * 1000L + ord)),
                                noteId = NoteId(cursor.long(COLUMN_NOTE_ID, noteId)),
                                deckName = deckId,
                                ord = ord,
                                queue = queue,
                                type = cursor.int(COLUMN_TYPE, if (suspended) 3 else 0),
                                due = cursor.int(COLUMN_DUE, 0),
                                intervalDays = cursor.int(COLUMN_INTERVAL, 0),
                                reps = cursor.int(COLUMN_REPS, 0),
                                lapses = cursor.int(COLUMN_LAPSES, 0),
                                suspended = suspended,
                                browserQueryMatched = false,
                                fsrsStability = fsrs.stability,
                                fsrsDifficulty = fsrs.difficulty,
                                fsrsRetrievability = fsrs.retrievability,
                                lastSeenSyncId = SyncRunId(0),
                            ),
                        )
                    }
                }
            }

    private fun querySuspendedNoteIds(
        target: AnkiDroidProviderTarget,
        settings: ImportSettings,
    ): Set<Long> {
        return try {
            provider.query(
                target.authority,
                listOf("notes"),
                projection = null,
                selection = "note:\"${settings.noteMapping.noteTypeName}\" is:suspended",
                selectionArgs = null,
            ).useRows(message = null) { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.long(COLUMN_ID, 0))
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun validateTemplateCards(
        cards: List<SourceCard>,
        settings: ImportSettings,
    ) {
        val unsupported = cards.firstOrNull { it.ord != 0 } ?: return
        throw CollectionGatewayException(
            errorCode = SyncErrorCode.PERMANENT_CONFIGURATION,
            permanent = true,
            message = "${settings.noteMapping.noteTypeName} has card template ord ${unsupported.ord}. This app supports only the first card template at ord 0.",
        )
    }

    private fun configuredBrowserQuerySearch(settings: ImportSettings): String =
        "note:\"${settings.noteMapping.noteTypeName}\" (${settings.importBrowserQuery.trim()})"

    private fun selectedSuspendedCardIds(importCandidates: List<ImportedKanjiCandidate>): Set<Long> =
        importCandidates.flatMap { candidate ->
            candidate.sources.filter { it.suspended }.map { it.cardId.value }
        }.toSet()

    private fun tagNoteArchived(
        target: AnkiDroidProviderTarget,
        noteId: Long,
    ): Boolean {
        return try {
            val tags = provider.query(
                target.authority,
                listOf("notes", noteId.toString()),
                projection = listOf(COLUMN_TAGS),
                selection = null,
                selectionArgs = null,
            )?.use { cursor ->
                if (cursor.moveToNext()) {
                    cursor.string(COLUMN_TAGS).orEmpty()
                } else {
                    ""
                }
            }.orEmpty()
            val nextTags = if (isArchivedTagPresent(splitTags(tags))) {
                tags
            } else {
                "$tags $ARCHIVED_TAG".trim()
            }
            provider.update(
                target.authority,
                listOf("notes", noteId.toString()),
                values = mapOf(COLUMN_TAGS to nextTags),
                selection = null,
                selectionArgs = null,
            ) > 0
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    private fun removalMessage(
        tagged: Int,
        failed: Int,
    ): String {
        return when {
            tagged > 0 && failed == 0 ->
                "Archived suspended notes were tagged in AnkiDroid and hidden from future syncs."
            tagged > 0 ->
                "Archived suspended notes were partly tagged in AnkiDroid; any leftovers stay in the local archive."
            else ->
                "Archived suspended cards were kept in the local archive; AnkiDroid did not allow provider tagging."
        }
    }

    private data class ModelMapping(
        val modelId: Long,
        val name: String,
        val fields: List<String>,
    )

    private data class NoteType(
        val modelId: Long,
        val name: String,
        val fields: List<String>,
    )

    private data class FsrsMemoryState(
        val stability: Double?,
        val difficulty: Double?,
        val retrievability: Double?,
    )

    private data class SuspendedCardArchiveIndex(
        val cardsByNote: Map<Long, Int>,
        val suspendedByNote: Map<Long, Int>,
        val selectedSuspendedByNote: Map<Long, Int>,
        val suspendedCards: List<SourceCard>,
    ) {
        fun notesFullySuspended(): Set<Long> =
            suspendedCards.mapNotNullTo(linkedSetOf()) { card ->
                val noteId = card.noteId.value
                if (cardsByNote[noteId] == suspendedByNote[noteId] &&
                    suspendedByNote[noteId] == selectedSuspendedByNote[noteId]
                ) {
                    noteId
                } else {
                    null
                }
            }

        fun partiallySuspendedCardCount(): Int =
            suspendedCards.count { card ->
                val noteId = card.noteId.value
                cardsByNote[noteId] != suspendedByNote[noteId] ||
                    suspendedByNote[noteId] != selectedSuspendedByNote[noteId]
            }

        companion object {
            fun from(
                cards: List<SourceCard>,
                selectedSuspendedCardIds: Set<Long>,
            ): SuspendedCardArchiveIndex {
                val cardsByNote = linkedMapOf<Long, Int>()
                val suspendedByNote = linkedMapOf<Long, Int>()
                val selectedSuspendedByNote = linkedMapOf<Long, Int>()
                val suspendedCards = mutableListOf<SourceCard>()
                for (card in cards) {
                    val noteId = card.noteId.value
                    cardsByNote[noteId] = cardsByNote.getOrDefault(noteId, 0) + 1
                    if (!card.suspended) {
                        continue
                    }
                    suspendedByNote[noteId] = suspendedByNote.getOrDefault(noteId, 0) + 1
                    if (card.cardId.value in selectedSuspendedCardIds) {
                        suspendedCards += card
                        selectedSuspendedByNote[noteId] = selectedSuspendedByNote.getOrDefault(noteId, 0) + 1
                    }
                }
                return SuspendedCardArchiveIndex(
                    cardsByNote = cardsByNote,
                    suspendedByNote = suspendedByNote,
                    selectedSuspendedByNote = selectedSuspendedByNote,
                    suspendedCards = suspendedCards,
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_MODEL_NAME = "Kiku"
        const val ARCHIVED_TAG = "kani_archived"
        const val LEGACY_ARCHIVED_TAG = "kanji_anki_archived"
        const val FIELD_SEPARATOR = '\u001f'
        const val COLUMN_CARD_ID = "_id"
        const val COLUMN_ID = "_id"
        const val COLUMN_NAME = "name"
        const val COLUMN_FIELD_NAMES = "field_names"
        const val COLUMN_FIELDS = "flds"
        const val COLUMN_TAGS = "tags"
        const val COLUMN_MODEL_ID = "mid"
        const val COLUMN_NOTE_ID = "note_id"
        const val COLUMN_ORD = "ord"
        const val COLUMN_DECK_ID = "deck_id"
        const val COLUMN_QUEUE = "queue"
        const val COLUMN_TYPE = "type"
        const val COLUMN_DUE = "due"
        const val COLUMN_INTERVAL = "interval"
        const val COLUMN_REPS = "reps"
        const val COLUMN_LAPSES = "lapses"
        const val COLUMN_FSRS_STABILITY = "fsrs_stability"
        const val COLUMN_FSRS_DIFFICULTY = "fsrs_difficulty"
        const val COLUMN_FSRS_RETRIEVABILITY = "fsrs_retrievability"
        const val COLUMN_STABILITY = "stability"
        const val COLUMN_DIFFICULTY = "difficulty"
        const val COLUMN_RETRIEVABILITY = "retrievability"
        const val COLUMN_DATA = "data"

        val CARD_COLUMNS_WITH_FSRS = listOf(
            COLUMN_CARD_ID,
            COLUMN_NOTE_ID,
            COLUMN_ORD,
            COLUMN_DECK_ID,
            COLUMN_QUEUE,
            COLUMN_TYPE,
            COLUMN_DUE,
            COLUMN_INTERVAL,
            COLUMN_REPS,
            COLUMN_LAPSES,
            COLUMN_FSRS_STABILITY,
            COLUMN_FSRS_DIFFICULTY,
            COLUMN_FSRS_RETRIEVABILITY,
            COLUMN_STABILITY,
            COLUMN_DIFFICULTY,
            COLUMN_RETRIEVABILITY,
            COLUMN_DATA,
        )
        val CARD_COLUMNS_WITH_SCHEDULER = listOf(
            COLUMN_CARD_ID,
            COLUMN_NOTE_ID,
            COLUMN_ORD,
            COLUMN_DECK_ID,
            COLUMN_QUEUE,
            COLUMN_TYPE,
            COLUMN_DUE,
            COLUMN_INTERVAL,
            COLUMN_REPS,
            COLUMN_LAPSES,
        )
        val CARD_COLUMNS_MINIMAL = listOf(COLUMN_NOTE_ID, COLUMN_ORD, COLUMN_DECK_ID)

        fun splitFields(value: String): List<String> = value.split(FIELD_SEPARATOR)

        fun splitTags(value: String): List<String> =
            value.split(Regex("\\s+")).mapNotNull { it.trim().takeIf(String::isNotEmpty) }

        fun isArchivedTagPresent(tags: List<String>): Boolean =
            ARCHIVED_TAG in tags || LEGACY_ARCHIVED_TAG in tags

        fun selectRequiredFields(
            modelFields: List<String>,
            values: List<String>,
            settings: ImportSettings,
        ): Map<String, String> = settings.requiredFields().associateWith { field ->
            val index = modelFields.indexOf(field)
            if (index >= 0 && index < values.size) values[index] else ""
        }

        fun fsrsMemoryState(cursor: AnkiDroidCursor): FsrsMemoryState {
            val stability = cursor.double(COLUMN_FSRS_STABILITY) ?: cursor.double(COLUMN_STABILITY)
            val difficulty = cursor.double(COLUMN_FSRS_DIFFICULTY) ?: cursor.double(COLUMN_DIFFICULTY)
            val retrievability = cursor.double(COLUMN_FSRS_RETRIEVABILITY) ?: cursor.double(COLUMN_RETRIEVABILITY)
            if (stability != null || difficulty != null || retrievability != null) {
                return FsrsMemoryState(stability, difficulty, retrievability)
            }
            return parseFsrsData(cursor.string(COLUMN_DATA).orEmpty())
        }

        fun parseFsrsData(data: String): FsrsMemoryState {
            if (data.isBlank()) {
                return FsrsMemoryState(null, null, null)
            }
            val regex = Regex("""(?i)(?:"|')?(stability|difficulty|retrievability|s|d|r)(?:"|')?\s*[:=]\s*"?([-+]?[0-9]+(?:\.[0-9]+)?)"?""")
            var stability: Double? = null
            var difficulty: Double? = null
            var retrievability: Double? = null
            for (match in regex.findAll(data)) {
                val value = match.groupValues[2].toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
                when (match.groupValues[1].lowercase(Locale.ROOT)) {
                    "stability", "s" -> stability = value
                    "difficulty", "d" -> difficulty = value
                    else -> retrievability = value
                }
            }
            return FsrsMemoryState(stability, difficulty, retrievability)
        }
    }
}

data class AnkiDroidProviderTarget(
    val authority: String,
    val permission: String?,
) {
    companion object {
        const val READ_WRITE_DATABASE_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
        const val DEBUG_READ_WRITE_DATABASE_PERMISSION = "com.ichi2.anki.debug.permission.READ_WRITE_DATABASE"

        val defaults: List<AnkiDroidProviderTarget> = listOf(
            AnkiDroidProviderTarget("com.ichi2.anki.api.provider", READ_WRITE_DATABASE_PERMISSION),
            AnkiDroidProviderTarget("com.ichi2.anki.flashcards", READ_WRITE_DATABASE_PERMISSION),
            AnkiDroidProviderTarget("com.ichi2.anki.debug.api.provider", DEBUG_READ_WRITE_DATABASE_PERMISSION),
            AnkiDroidProviderTarget("com.ichi2.anki.debug.flashcards", DEBUG_READ_WRITE_DATABASE_PERMISSION),
        )
    }
}

interface AnkiDroidProviderClient {
    fun resolveTarget(targets: List<AnkiDroidProviderTarget>): AnkiDroidProviderTarget?

    fun hasPermission(permission: String?): Boolean

    fun query(
        authority: String,
        pathSegments: List<String>,
        projection: List<String>?,
        selection: String?,
        selectionArgs: List<String>?,
    ): AnkiDroidCursor?

    fun update(
        authority: String,
        pathSegments: List<String>,
        values: Map<String, String>,
        selection: String?,
        selectionArgs: List<String>?,
    ): Int
}

interface AnkiDroidCursor : AutoCloseable {
    fun moveToNext(): Boolean

    fun string(column: String): String?

    fun long(
        column: String,
        fallback: Long,
    ): Long = string(column)?.toLongOrNull() ?: fallback

    fun int(
        column: String,
        fallback: Int,
    ): Int = string(column)?.toIntOrNull() ?: fallback

    fun double(column: String): Double? = string(column)?.toDoubleOrNull()?.takeIf { it.isFinite() }
}

private class AndroidAnkiDroidProviderClient(
    private val context: Context,
) : AnkiDroidProviderClient {
    private val resolver: ContentResolver = context.contentResolver

    override fun resolveTarget(targets: List<AnkiDroidProviderTarget>): AnkiDroidProviderTarget? {
        val packageManager = context.packageManager
        return targets.firstOrNull { target ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveContentProvider(target.authority, PackageManager.ComponentInfoFlags.of(0)) != null
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveContentProvider(target.authority, 0) != null
            }
        }
    }

    override fun hasPermission(permission: String?): Boolean =
        permission.isNullOrEmpty() || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override fun query(
        authority: String,
        pathSegments: List<String>,
        projection: List<String>?,
        selection: String?,
        selectionArgs: List<String>?,
    ): AnkiDroidCursor? {
        val uri = Uri.Builder().scheme("content").authority(authority).apply {
            for (segment in pathSegments) {
                appendPath(segment)
            }
        }.build()
        return resolver.query(
            uri,
            projection?.toTypedArray(),
            selection,
            selectionArgs?.toTypedArray(),
            null,
        )?.let(::AndroidAnkiDroidCursor)
    }

    override fun update(
        authority: String,
        pathSegments: List<String>,
        values: Map<String, String>,
        selection: String?,
        selectionArgs: List<String>?,
    ): Int {
        val uri = Uri.Builder().scheme("content").authority(authority).apply {
            for (segment in pathSegments) {
                appendPath(segment)
            }
        }.build()
        val contentValues = ContentValues().apply {
            for ((key, value) in values) {
                put(key, value)
            }
        }
        return resolver.update(uri, contentValues, selection, selectionArgs?.toTypedArray())
    }
}

private class AndroidAnkiDroidCursor(
    private val cursor: Cursor,
) : AnkiDroidCursor {
    override fun moveToNext(): Boolean = cursor.moveToNext()

    override fun string(column: String): String? {
        val index = cursor.getColumnIndex(column)
        if (index < 0 || cursor.isNull(index)) {
            return null
        }
        return cursor.getString(index)
    }

    override fun long(
        column: String,
        fallback: Long,
    ): Long {
        val index = cursor.getColumnIndex(column)
        return if (index < 0 || cursor.isNull(index)) fallback else cursor.getLong(index)
    }

    override fun int(
        column: String,
        fallback: Int,
    ): Int {
        val index = cursor.getColumnIndex(column)
        return if (index < 0 || cursor.isNull(index)) fallback else cursor.getInt(index)
    }

    override fun double(column: String): Double? {
        val index = cursor.getColumnIndex(column)
        if (index < 0 || cursor.isNull(index)) {
            return null
        }
        val value = cursor.getString(index)?.trim()?.toDoubleOrNull() ?: return null
        return value.takeIf { it.isFinite() }
    }

    override fun close() = cursor.close()
}

private inline fun <T> AnkiDroidCursor?.useRows(
    message: String?,
    block: (AnkiDroidCursor) -> T,
): T {
    val cursor = this ?: throw CollectionGatewayException(
        errorCode = SyncErrorCode.RETRYABLE,
        permanent = false,
        message = message ?: "AnkiDroid returned no cursor.",
    )
    cursor.use {
        return block(it)
    }
}

private fun ImportSettings.requiredFields(): List<String> = buildList {
    fun addField(value: String) {
        val field = value.trim()
        if (field.isNotEmpty() && field !in this) {
            add(field)
        }
    }
    addField(noteMapping.expressionField)
    addField(noteMapping.readingField)
    addField(noteMapping.meaningField)
    addField(noteMapping.sentenceField)
    addField(noteMapping.frequencyField)
    addField(noteMapping.frequencySortField)
}

private fun ImportSettings.browserQueryImportEnabled(): Boolean =
    importBrowserQueryCards && importBrowserQuery.isNotBlank()

private fun Map<String, String>.toJsonObject(): String =
    entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "\"${key.jsonEscaped()}\":\"${value.jsonEscaped()}\""
    }

private fun String.jsonEscaped(): String =
    buildString {
        for (char in this@jsonEscaped) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }
