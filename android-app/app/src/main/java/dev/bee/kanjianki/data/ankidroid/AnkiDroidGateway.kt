package dev.bee.kanjianki.data.ankidroid

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import dev.bee.kanjianki.data.sync.PermanentCollectionSyncException
import dev.bee.kanjianki.data.sync.TransientCollectionSyncException
import dev.bee.kanjianki.domain.SettingsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AnkiDroidStatus(
    val installed: Boolean,
    val permissionGranted: Boolean,
    val canReadCollection: Boolean,
    val message: String,
    val authority: String? = null,
    val permissionName: String? = null,
    val packageName: String? = null,
)

data class AnkiDroidNoteSnapshot(
    val noteId: Long,
    val modelName: String,
    val expression: String,
    val reading: String,
    val meaning: String,
    val fields: Map<String, String>,
    val tags: List<String>,
)

data class AnkiDroidCardSnapshot(
    val cardId: Long,
    val noteId: Long,
    val deckName: String,
    val intervalDays: Int,
    val modifiedTs: Long,
    val dueValue: Int,
    val cardOrd: Int,
    val queueValue: Int,
    val cardType: Int,
    val reps: Int,
    val lapses: Int,
    val isSuspended: Boolean,
    val isActive: Boolean,
    val isMature: Boolean,
)

data class AnkiDroidCollectionSnapshot(
    val notes: List<AnkiDroidNoteSnapshot>,
    val cards: List<AnkiDroidCardSnapshot>,
)

interface AnkiDroidGateway {
    suspend fun getStatus(): AnkiDroidStatus

    suspend fun readCollectionSnapshot(settings: SettingsSnapshot): AnkiDroidCollectionSnapshot
}

class ContentProviderAnkiDroidGateway(
    context: Context,
) : AnkiDroidGateway {
    private val context = context.applicationContext
    private val resolver = this.context.contentResolver

    override suspend fun getStatus(): AnkiDroidStatus =
        withContext(Dispatchers.IO) {
            val target = resolveProviderTarget()
                ?: return@withContext AnkiDroidStatus(
                    installed = false,
                    permissionGranted = false,
                    canReadCollection = false,
                    message = "AnkiDroid is not installed, so live collection sync is unavailable.",
                )
            val permissionGranted = hasPermission(target.permissionName)
            AnkiDroidStatus(
                installed = true,
                permissionGranted = permissionGranted,
                canReadCollection = permissionGranted,
                message = if (permissionGranted) {
                    "Using AnkiDroid's exported flashcard provider for live collection reads."
                } else {
                    "AnkiDroid is installed, but its READ_WRITE_DATABASE permission has not been granted."
                },
                authority = target.authority,
                permissionName = target.permissionName,
                packageName = target.packageName,
            )
        }

    override suspend fun readCollectionSnapshot(settings: SettingsSnapshot): AnkiDroidCollectionSnapshot =
        withContext(Dispatchers.IO) {
            try {
                val target = resolveProviderTarget()
                    ?: throw PermanentCollectionSyncException(
                        "AnkiDroid's flashcard provider is not installed.",
                    )
                if (!hasPermission(target.permissionName)) {
                    throw PermanentCollectionSyncException(
                        "AnkiDroid permission ${target.permissionName} has not been granted.",
                    )
                }

                val deckNames = queryDeckNames(target)
                val modelMappings = queryModelMappings(target, settings)
                if (settings.noteModels.isNotEmpty()) {
                    val missingModels = settings.noteModels.filterNot { requested ->
                        modelMappings.any { it.modelName == requested }
                    }
                    if (missingModels.isNotEmpty()) {
                        throw PermanentCollectionSyncException(
                            "Configured note models were not found in AnkiDroid: ${missingModels.joinToString()}.",
                        )
                    }
                }

                val notes = modelMappings.flatMap { mapping ->
                    queryNotesForModel(
                        target = target,
                        mapping = mapping,
                    )
                }
                val cards = queryCardsForModels(
                    target = target,
                    modelNames = modelMappings.map(ModelMapping::modelName),
                    deckNames = deckNames,
                    matureDays = settings.matureDays,
                )
                AnkiDroidCollectionSnapshot(
                    notes = notes,
                    cards = cards,
                )
            } catch (error: PermanentCollectionSyncException) {
                throw error
            } catch (error: SecurityException) {
                throw PermanentCollectionSyncException(
                    "AnkiDroid denied collection access. Re-grant its runtime permission and sync again.",
                    error,
                )
            } catch (error: Throwable) {
                throw TransientCollectionSyncException(
                    "Failed to read the AnkiDroid collection snapshot.",
                    error,
                )
            }
        }

    private fun queryModelMappings(
        target: ProviderTarget,
        settings: SettingsSnapshot,
    ): List<ModelMapping> {
        val requestedModels = settings.noteModels.toSet()
        val rows = mutableListOf<ModelMapping>()
        val cursor = resolver.query(
            uriFor(target.authority, "models"),
            arrayOf(
                ModelColumns.ID,
                ModelColumns.NAME,
                ModelColumns.FIELD_NAMES,
            ),
            null,
            null,
            null,
        ) ?: throw TransientCollectionSyncException(
            "AnkiDroid returned no cursor for note models.",
        )
        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                val modelName = cursor.getString(cursor.getColumnIndexOrThrow(ModelColumns.NAME))
                if (requestedModels.isNotEmpty() && modelName !in requestedModels) {
                    continue
                }
                val fieldNames = splitFields(cursor.getString(cursor.getColumnIndexOrThrow(ModelColumns.FIELD_NAMES)))
                val expressionIndex = fieldNames.indexOf(settings.expressionField)
                val readingIndex = fieldNames.indexOf(settings.readingField)
                val meaningIndex = fieldNames.indexOf(settings.meaningField)
                if (expressionIndex < 0) {
                    throw PermanentCollectionSyncException(
                        "AnkiDroid note model $modelName is missing field ${settings.expressionField}.",
                    )
                }
                if (readingIndex < 0) {
                    throw PermanentCollectionSyncException(
                        "AnkiDroid note model $modelName is missing field ${settings.readingField}.",
                    )
                }
                if (meaningIndex < 0) {
                    throw PermanentCollectionSyncException(
                        "AnkiDroid note model $modelName is missing field ${settings.meaningField}.",
                    )
                }
                rows += ModelMapping(
                    modelId = cursor.getLong(cursor.getColumnIndexOrThrow(ModelColumns.ID)),
                    modelName = modelName,
                    fieldNames = fieldNames,
                    expressionIndex = expressionIndex,
                    readingIndex = readingIndex,
                    meaningIndex = meaningIndex,
                )
            }
        }
        if (requestedModels.isEmpty() && rows.isEmpty()) {
            throw TransientCollectionSyncException("AnkiDroid returned no note models to sync.")
        }
        return rows
    }

    private fun queryNotesForModel(
        target: ProviderTarget,
        mapping: ModelMapping,
    ): List<AnkiDroidNoteSnapshot> {
        val notes = mutableListOf<AnkiDroidNoteSnapshot>()
        val cursor = resolver.query(
            uriFor(target.authority, "notes_v2"),
            arrayOf(
                NoteColumns.ID,
                NoteColumns.FIELDS,
                NoteColumns.TAGS,
            ),
            "${NoteColumns.MODEL_ID}=${mapping.modelId}",
            null,
            null,
        ) ?: throw TransientCollectionSyncException(
            "AnkiDroid returned no cursor for note model ${mapping.modelName}.",
        )
        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                val fieldValues = splitFields(cursor.getString(cursor.getColumnIndexOrThrow(NoteColumns.FIELDS)))
                val fieldMap = mapping.fieldNames.mapIndexed { index, fieldName ->
                    fieldName to fieldValues.getOrElse(index) { "" }
                }.toMap()
                notes += AnkiDroidNoteSnapshot(
                    noteId = cursor.getLong(cursor.getColumnIndexOrThrow(NoteColumns.ID)),
                    modelName = mapping.modelName,
                    expression = fieldValues.getOrElse(mapping.expressionIndex) { "" },
                    reading = fieldValues.getOrElse(mapping.readingIndex) { "" },
                    meaning = fieldValues.getOrElse(mapping.meaningIndex) { "" },
                    fields = fieldMap,
                    tags = splitTags(cursor.getString(cursor.getColumnIndexOrThrow(NoteColumns.TAGS))),
                )
            }
        }
        return notes
    }

    private fun queryDeckNames(target: ProviderTarget): Map<Long, String> {
        val rows = linkedMapOf<Long, String>()
        val cursor = resolver.query(
            uriFor(target.authority, "decks"),
            arrayOf(
                DeckColumns.ID,
                DeckColumns.NAME,
            ),
            null,
            null,
            null,
        ) ?: throw TransientCollectionSyncException(
            "AnkiDroid returned no cursor for deck names.",
        )
        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                rows[cursor.getLong(cursor.getColumnIndexOrThrow(DeckColumns.ID))] =
                    cursor.getString(cursor.getColumnIndexOrThrow(DeckColumns.NAME))
            }
        }
        return rows
    }

    private fun queryCardsForModels(
        target: ProviderTarget,
        modelNames: List<String>,
        deckNames: Map<Long, String>,
        matureDays: Int,
    ): List<AnkiDroidCardSnapshot> {
        val cards = mutableListOf<AnkiDroidCardSnapshot>()
        val selection = buildModelSearchQuery(modelNames)
        val cursor = resolver.query(
            uriFor(target.authority, "cards"),
            arrayOf(
                CardColumns.ID,
                CardColumns.NOTE_ID,
                CardColumns.CARD_ORD,
                CardColumns.DECK_ID,
                CardColumns.REPS,
                CardColumns.LAPSES,
                CardColumns.TYPE,
                CardColumns.RAW_QUEUE,
                CardColumns.RAW_DUE,
                CardColumns.INTERVAL,
            ),
            selection,
            null,
            null,
        ) ?: throw TransientCollectionSyncException(
            "AnkiDroid returned no cursor for card rows.",
        )
        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                val deckId = cursor.getLong(cursor.getColumnIndexOrThrow(CardColumns.DECK_ID))
                val queueValue = cursor.getInt(cursor.getColumnIndexOrThrow(CardColumns.RAW_QUEUE))
                val cardType = cursor.getInt(cursor.getColumnIndexOrThrow(CardColumns.TYPE))
                val intervalDays = cursor.getInt(cursor.getColumnIndexOrThrow(CardColumns.INTERVAL))
                val isSuspended = queueValue == QUEUE_SUSPENDED
                cards += AnkiDroidCardSnapshot(
                    cardId = cursor.getLong(cursor.getColumnIndexOrThrow(CardColumns.ID)),
                    noteId = cursor.getLong(cursor.getColumnIndexOrThrow(CardColumns.NOTE_ID)),
                    deckName = deckNames[deckId] ?: deckId.toString(),
                    intervalDays = intervalDays,
                    modifiedTs = 0,
                    dueValue = cursor.getInt(cursor.getColumnIndexOrThrow(CardColumns.RAW_DUE)),
                    cardOrd = cursor.getInt(cursor.getColumnIndexOrThrow(CardColumns.CARD_ORD)),
                    queueValue = queueValue,
                    cardType = cardType,
                    reps = cursor.getInt(cursor.getColumnIndexOrThrow(CardColumns.REPS)),
                    lapses = cursor.getInt(cursor.getColumnIndexOrThrow(CardColumns.LAPSES)),
                    isSuspended = isSuspended,
                    isActive = !isSuspended && cardType > 0,
                    isMature = intervalDays >= matureDays,
                )
            }
        }
        return cards
    }

    private fun resolveProviderTarget(): ProviderTarget? =
        PROVIDER_TARGETS.firstNotNullOfOrNull { candidate ->
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveContentProvider(
                    candidate.authority,
                    PackageManager.ComponentInfoFlags.of(0L),
                )
            } else {
                context.packageManager.resolveContentProvider(candidate.authority, 0)
            }
            if (info == null) {
                null
            } else {
                candidate.copy(packageName = info.packageName)
            }
        }

    private fun hasPermission(permissionName: String): Boolean =
        ContextCompat.checkSelfPermission(context, permissionName) == PackageManager.PERMISSION_GRANTED

    private fun splitFields(raw: String): List<String> =
        raw.split(FIELD_SEPARATOR)

    private fun splitTags(raw: String): List<String> =
        raw
            .split(TAG_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotBlank)

    private fun buildModelSearchQuery(modelNames: List<String>): String? {
        val filters = modelNames
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .map { modelName ->
                "note:\"${modelName.replace("\"", "\\\"")}\""
            }
        return filters.takeIf { it.isNotEmpty() }?.joinToString(" or ")
    }

    private fun uriFor(
        authority: String,
        vararg pathSegments: String,
    ): Uri =
        Uri.Builder()
            .scheme("content")
            .authority(authority)
            .apply {
                pathSegments.forEach(::appendPath)
            }
            .build()
}

private data class ProviderTarget(
    val authority: String,
    val permissionName: String,
    val packageName: String? = null,
)

private data class ModelMapping(
    val modelId: Long,
    val modelName: String,
    val fieldNames: List<String>,
    val expressionIndex: Int,
    val readingIndex: Int,
    val meaningIndex: Int,
)

private object NoteColumns {
    const val ID = "_id"
    const val MODEL_ID = "mid"
    const val FIELDS = "flds"
    const val TAGS = "tags"
}

private object ModelColumns {
    const val ID = "_id"
    const val NAME = "name"
    const val FIELD_NAMES = "field_names"
}

private object DeckColumns {
    const val ID = "deck_id"
    const val NAME = "deck_name"
}

private object CardColumns {
    const val ID = "_id"
    const val NOTE_ID = "note_id"
    const val CARD_ORD = "ord"
    const val DECK_ID = "deck_id"
    const val REPS = "reps"
    const val LAPSES = "lapses"
    const val TYPE = "type"
    const val RAW_QUEUE = "queue"
    const val RAW_DUE = "due"
    const val INTERVAL = "ivl"
}

private const val FIELD_SEPARATOR = '\u001f'
private const val TAG_SEPARATOR = " "
private const val QUEUE_SUSPENDED = -1

private val PROVIDER_TARGETS = listOf(
    ProviderTarget(
        authority = "com.ichi2.anki.flashcards",
        permissionName = "com.ichi2.anki.permission.READ_WRITE_DATABASE",
    ),
    ProviderTarget(
        authority = "com.ichi2.anki.debug.flashcards",
        permissionName = "com.ichi2.anki.debug.permission.READ_WRITE_DATABASE",
    ),
)
