package dev.bee.kanjianki.anki

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.OperationCanceledException
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SyncValidator
import dev.bee.kanjianki.sync.SyncCancellation
import dev.bee.kanjianki.sync.SyncProgress
import dev.bee.kanjianki.syncdomain.ProviderNotePolicy
import java.util.Collections
import java.util.Locale
import java.util.regex.Pattern

class AnkiDroidGateway private constructor(
    context: Context,
    private val providerTargets: List<ProviderTarget>,
    cancellation: SyncCancellation,
) : CollectionGateway {
    private val packageManager: PackageManager
    private val resolver: ContentResolver
    private val cardReader: AnkiDroidCardReader
    private val archiveCleanup: AnkiDroidArchiveCleanup
    private val repairedTagging: AnkiDroidRepairedTagging
    private val permissionChecker: PermissionChecker

    constructor(context: Context) : this(context, ProviderTarget.TARGETS, SyncCancellation.NONE)

    constructor(context: Context, cancellation: SyncCancellation) :
        this(context, ProviderTarget.TARGETS, cancellation)

    // Retained (Context, List) signature so existing reflective test constructions keep
    // working; defaults cancellation to none.
    private constructor(context: Context, providerTargets: List<ProviderTarget>) :
        this(context, providerTargets, SyncCancellation.NONE)

    init {
        val appContext = context.applicationContext
        packageManager = appContext.packageManager
        resolver = appContext.contentResolver
        cardReader = AnkiDroidCardReader(resolver, cancellation)
        archiveCleanup = AnkiDroidArchiveCleanup(resolver)
        repairedTagging = AnkiDroidRepairedTagging(resolver)
        permissionChecker = PermissionChecker { permission -> appContext.checkSelfPermission(permission) }
    }

    fun status(): ProviderStatus {
        val target = resolveProviderTarget()
            ?: return ProviderStatus.create(
                false,
                false,
                false,
                null,
                null,
                "Install AnkiDroid and enable its API/database permission.",
            )
        val granted = hasPermission(target.permission)
        return ProviderStatus.create(
            true,
            granted,
            granted,
            target.authority,
            target.permission,
            if (granted) {
                "AnkiDroid is ready for live note sync."
            } else {
                "Allow AnkiDroid access so Kani can read your live collection."
            },
        )
    }

    @Throws(SyncFailure::class)
    fun noteTypes(): List<NoteType> {
        val target = requireProvider()
        if (!hasPermission(target.permission)) {
            throw SyncFailure.permanent("AnkiDroid permission is missing: ${target.permission}")
        }
        return queryNoteTypes(target)
    }

    @Throws(SyncFailure::class)
    override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
        return readCollection(settings, SyncProgress.NONE)
    }

    @Throws(SyncFailure::class)
    override fun readCollection(
        settings: RecordsSyncModels.Settings,
        progress: SyncProgress.Listener?,
    ): RecordsSyncModels.CollectionSnapshot {
        val reporter = progress ?: SyncProgress.NONE
        val target = requireProvider()
        if (!hasPermission(target.permission)) {
            throw SyncFailure.permanent("AnkiDroid permission is missing: ${target.permission}")
        }
        try {
            reporter.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.FINDING_NOTE_TYPE))
            val mapping = findConfiguredModel(target, settings)
            reporter.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.READING_NOTES))
            val notes = queryNotes(target, mapping, settings)
            val browserQueryNoteIds = queryBrowserQueryNoteIds(target, mapping, settings)
            mergeMissingBrowserQueryNotes(target, mapping, settings, notes, browserQueryNoteIds)
            var cards = cardReader.queryCardsByNote(target.authority, settings, notes.keys, reporter)
            validateTemplateCards(cards, settings)
            cards = cardsWithNotes(cards, notes.keys)
            cards = markBrowserQueryMatchedCards(cards, browserQueryNoteIds)
            return RecordsSyncModels.CollectionSnapshot(ArrayList(notes.values), cards)
        } catch (error: SyncFailure) {
            throw error
        } catch (error: OperationCanceledException) {
            throw SyncFailure.retryable("Timed out while reading AnkiDroid.", error)
        } catch (error: SecurityException) {
            throw SyncFailure.permanent("AnkiDroid denied database access.", error)
        } catch (error: Exception) {
            val kind = SyncValidator.classifyProviderFailure(error)
            if (kind.startsWith("permanent")) {
                throw SyncFailure.permanent(error.message, error)
            }
            throw SyncFailure.retryable("AnkiDroid provider read failed: ${error.message}", error)
        }
    }

    @Throws(SyncFailure::class)
    private fun mergeMissingBrowserQueryNotes(
        target: ProviderTarget,
        mapping: ModelMapping,
        settings: RecordsSyncModels.Settings,
        notes: MutableMap<Long, RecordsSyncModels.Note>,
        browserQueryNoteIds: Set<Long>,
    ) {
        for (noteId in browserQueryNoteIds) {
            if (!notes.containsKey(noteId)) {
                rereadBrowserQueryNotes(target, mapping, settings, notes)
                return
            }
        }
    }

    @Throws(SyncFailure::class)
    private fun rereadBrowserQueryNotes(
        target: ProviderTarget,
        mapping: ModelMapping,
        settings: RecordsSyncModels.Settings,
        notes: MutableMap<Long, RecordsSyncModels.Note>,
    ) {
        try {
            val extraNotes = queryNotesBySearch(
                target,
                mapping,
                settings,
                ProviderNotePolicy.browserQuerySearch(settings.normalizedBrowserQuery()),
            )
            for ((key, value) in extraNotes) {
                notes.putIfAbsent(key, value)
            }
        } catch (error: Exception) {
            throw browserQueryFailure(error)
        }
    }

    override fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot): RemovalSummary {
        return removeArchivedSuspendedCards(snapshot, SyncProgress.NONE)
    }

    override fun removeArchivedSuspendedCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        progress: SyncProgress.Listener?,
    ): RemovalSummary {
        return removeArchivedSuspendedCards(snapshot, null, progress)
    }

    override fun removeArchivedSuspendedCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        selectedSuspendedImports: List<RecordsImportModels.SuspendedImport>?,
        progress: SyncProgress.Listener?,
    ): RemovalSummary {
        val reporter = progress ?: SyncProgress.NONE
        reporter.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS))
        val target = resolveProviderTarget()
        if (target == null || snapshot.cards.isEmpty()) {
            return RemovalSummary(0, 0, 0, "No provider removal attempted.")
        }
        return archiveCleanup.removeArchivedSuspendedCards(target.authority, snapshot, selectedSuspendedImports)
    }

    override fun tagRepairedNotes(
        noteIds: Set<Long>,
        progress: SyncProgress.Listener?,
    ): RepairedTagSummary {
        val reporter = progress ?: SyncProgress.NONE
        reporter.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.TAGGING_REPAIRED))
        if (noteIds.isEmpty()) return RepairedTagSummary.noOp()
        val target = resolveProviderTarget()
            ?: return RepairedTagSummary(
                noteIds,
                emptySet(),
                noteIds,
                "Repaired-note tagging could not reach AnkiDroid and will retry next sync.",
            )
        return repairedTagging.tagRepairedNotes(target.authority, noteIds)
    }

    @Throws(SyncFailure::class)
    private fun requireProvider(): ProviderTarget {
        val target = resolveProviderTarget()
            ?: throw SyncFailure.permanent("AnkiDroid's flashcard provider is not installed.")
        return target
    }

    private fun resolveProviderTarget(): ProviderTarget? {
        for (target in providerTargets) {
            if (providerInstalled(packageManager, target.authority)) {
                return target
            }
        }
        return null
    }

    private fun hasPermission(permission: String?): Boolean {
        if (permission.isNullOrEmpty()) {
            return true
        }
        return permissionChecker.check(permission) == PackageManager.PERMISSION_GRANTED
    }

    @Throws(SyncFailure::class)
    private fun findConfiguredModel(target: ProviderTarget, settings: RecordsSyncModels.Settings): ModelMapping {
        for (noteType in queryNoteTypes(target)) {
            if (!noteType.name.equals(settings.modelName, ignoreCase = true)) {
                continue
            }
            val errors = SyncValidator.validateModelFields(noteType.name, noteType.fields, settings)
            if (errors.isNotEmpty()) {
                throw SyncFailure.permanent(errors.joinToString("\n"))
            }
            return ModelMapping(noteType.modelId, noteType.name, noteType.fields)
        }
        throw SyncFailure.permanent("${settings.modelName} note type was not found in AnkiDroid.")
    }

    @Throws(SyncFailure::class)
    private fun queryNoteTypes(target: ProviderTarget): List<NoteType> {
        val cursor = resolver.query(uriFor(target.authority, "models"), null, null, null, null)
            ?: throw SyncFailure.retryable("AnkiDroid returned no note model cursor.")
        val noteTypes = ArrayList<NoteType>()
        cursor.use { noteTypeCursor ->
            while (noteTypeCursor.moveToNext()) {
                val name = value(noteTypeCursor, COLUMN_NAME)
                val id = longValue(noteTypeCursor, COLUMN_ID, 0)
                val fields = splitFields(value(noteTypeCursor, COLUMN_FIELD_NAMES))
                noteTypes.add(NoteType.create(id, name, fields))
            }
        }
        noteTypes.sortWith(
            compareBy<NoteType> { !it.name.equals(RecordsSyncModels.Settings.kikuDefaults().modelName, ignoreCase = true) }
                .thenBy { it.name.lowercase(Locale.ROOT) },
        )
        return noteTypes
    }

    @Throws(SyncFailure::class)
    private fun queryNotes(
        target: ProviderTarget,
        mapping: ModelMapping,
        settings: RecordsSyncModels.Settings,
    ): MutableMap<Long, RecordsSyncModels.Note> {
        val searchFailure = try {
            val notes = queryNotesBySearch(target, mapping, settings, ProviderNotePolicy.modelSearch(settings.modelName))
            mergeSuspendedSearchNotes(target, mapping, settings, notes)
            return notes
        } catch (error: Exception) {
            error
        }
        try {
            return queryNotesBySql(target, mapping, settings)
        } catch (sqlFailure: SyncFailure) {
            sqlFailure.addSuppressed(searchFailure)
            throw sqlFailure
        }
    }

    @Throws(SyncFailure::class)
    private fun mergeSuspendedSearchNotes(
        target: ProviderTarget,
        mapping: ModelMapping,
        settings: RecordsSyncModels.Settings,
        notes: MutableMap<Long, RecordsSyncModels.Note>,
    ) {
        if (!settings.importSuspendedCards) {
            return
        }
        val suspendedNotes: Map<Long, RecordsSyncModels.Note> = queryNotesBySearch(
            target,
            mapping,
            settings,
            ProviderNotePolicy.modelSearch(settings.modelName) + " is:suspended",
        )
        for ((key, value) in suspendedNotes) {
            notes.putIfAbsent(key, value)
        }
    }

    @Throws(SyncFailure::class)
    private fun queryNotesBySearch(
        target: ProviderTarget,
        mapping: ModelMapping,
        settings: RecordsSyncModels.Settings,
        search: String,
    ): MutableMap<Long, RecordsSyncModels.Note> {
        val notes = LinkedHashMap<Long, RecordsSyncModels.Note>()
        val cursor = resolver.query(uriFor(target.authority, URI_SEGMENT_NOTES), null, search, null, null)
            ?: throw SyncFailure.retryable("AnkiDroid returned no configured note cursor.")
        cursor.use { noteCursor ->
            while (noteCursor.moveToNext()) {
                val noteId = longValue(noteCursor, COLUMN_ID, 0)
                val modelId = longValue(noteCursor, COLUMN_MODEL_ID, mapping.modelId)
                if (modelId != mapping.modelId) {
                    continue
                }
                addNoteFromCursor(notes, noteId, noteCursor, mapping, settings)
            }
        }
        return notes
    }

    @Throws(SyncFailure::class)
    private fun queryNotesBySql(
        target: ProviderTarget,
        mapping: ModelMapping,
        settings: RecordsSyncModels.Settings,
    ): MutableMap<Long, RecordsSyncModels.Note> {
        val notes = LinkedHashMap<Long, RecordsSyncModels.Note>()
        val cursor = resolver.query(
            uriFor(target.authority, "notes_v2"),
            null,
            "mid=?",
            arrayOf(mapping.modelId.toString()),
            null,
        ) ?: throw SyncFailure.retryable("AnkiDroid returned no configured note cursor.")
        cursor.use { noteCursor ->
            while (noteCursor.moveToNext()) {
                addNoteFromCursor(notes, longValue(noteCursor, COLUMN_ID, 0), noteCursor, mapping, settings)
            }
        }
        return notes
    }

    private fun addNoteFromCursor(
        notes: MutableMap<Long, RecordsSyncModels.Note>,
        noteId: Long,
        cursor: Cursor,
        mapping: ModelMapping,
        settings: RecordsSyncModels.Settings,
    ) {
        val values = splitFields(value(cursor, COLUMN_FIELDS))
        val fieldMap = ProviderNotePolicy.selectRequiredFields(mapping.fields, values, settings.requiredFields())
        val tags = splitTags(value(cursor, COLUMN_TAGS))
        if (!ProviderNotePolicy.isArchivedTagPresent(tags)) {
            notes[noteId] = RecordsSyncModels.Note(noteId, mapping.modelId, mapping.name, fieldMap, tags)
        }
    }

    private fun cardsWithNotes(cards: List<RecordsSyncModels.Card>, noteIds: Set<Long>): List<RecordsSyncModels.Card> {
        val out = ArrayList<RecordsSyncModels.Card>()
        for (card in cards) {
            if (noteIds.contains(card.noteId)) {
                out.add(card)
            }
        }
        return out
    }

    @Throws(SyncFailure::class)
    private fun validateTemplateCards(cards: List<RecordsSyncModels.Card>, settings: RecordsSyncModels.Settings) {
        for (card in cards) {
            if (card.ord != 0) {
                throw SyncFailure.permanent("${settings.modelName} has card template ord ${card.ord}. This app supports only the first card template at ord 0.")
            }
        }
    }

    @Throws(SyncFailure::class)
    private fun queryBrowserQueryNoteIds(
        target: ProviderTarget,
        mapping: ModelMapping,
        settings: RecordsSyncModels.Settings,
    ): Set<Long> {
        if (!settings.browserQueryImportEnabled()) {
            return emptySet()
        }
        val ids = LinkedHashSet<Long>()
        val search = ProviderNotePolicy.browserQuerySearch(settings.normalizedBrowserQuery())
        val cursor = try {
            resolver.query(uriFor(target.authority, URI_SEGMENT_NOTES), null, search, null, null)
        } catch (error: Exception) {
            throw browserQueryFailure(error)
        }
            // Real AnkiDroid returns a null cursor when a valid browser query
            // matches zero notes; treat that as an empty result, not an error.
            ?: return ids
        try {
            cursor.use { queryCursor ->
                while (queryCursor.moveToNext()) {
                    val modelId = longValue(queryCursor, COLUMN_MODEL_ID, mapping.modelId)
                    if (modelId == mapping.modelId) {
                        ids.add(longValue(queryCursor, COLUMN_ID, 0))
                    }
                }
            }
        } catch (error: Exception) {
            throw browserQueryFailure(error)
        }
        return ids
    }

    private fun browserQueryFailure(cause: Throwable?): SyncFailure {
        return SyncFailure.permanent("AnkiDroid could not run the browser query. Check the query in Import filters.", cause)
    }

    private class ModelMapping(
        val modelId: Long,
        val name: String,
        val fields: List<String>,
    )

    private class ProviderTarget(
        val authority: String,
        val permission: String?,
    ) {
        companion object {
            val TARGETS: List<ProviderTarget> = listOf(
                ProviderTarget("com.ichi2.anki.api.provider", READ_WRITE_DATABASE_PERMISSION),
                ProviderTarget("com.ichi2.anki.flashcards", READ_WRITE_DATABASE_PERMISSION),
                ProviderTarget("com.ichi2.anki.debug.api.provider", DEBUG_READ_WRITE_DATABASE_PERMISSION),
                ProviderTarget("com.ichi2.anki.debug.flashcards", DEBUG_READ_WRITE_DATABASE_PERMISSION),
            )
        }
    }

    private fun interface PermissionChecker {
        fun check(permission: String): Int
    }

    class ProviderStatus private constructor(
        @JvmField val installed: Boolean,
        @JvmField val permissionGranted: Boolean,
        @JvmField val canSync: Boolean,
        @JvmField val authority: String?,
        @JvmField val permission: String?,
        @JvmField val message: String,
    ) {
        companion object {
            fun create(
                installed: Boolean,
                permissionGranted: Boolean,
                canSync: Boolean,
                authority: String?,
                permission: String?,
                message: String,
            ): ProviderStatus {
                return ProviderStatus(installed, permissionGranted, canSync, authority, permission, message)
            }
        }
    }

    class NoteType private constructor(modelId: Long, name: String?, fields: List<String>?) {
        @JvmField
        val modelId: Long = modelId

        @JvmField
        val name: String = name ?: ""

        @JvmField
        val fields: List<String> = Collections.unmodifiableList(ArrayList(fields ?: emptyList()))

        companion object {
            fun create(modelId: Long, name: String?, fields: List<String>?): NoteType {
                return NoteType(modelId, name, fields)
            }
        }
    }

    class RemovalSummary(
        @JvmField val sourceCards: Int,
        // Always 0: Kani archives suspended cards by tagging their notes (see
        // taggedNotes), never by deleting notes from the AnkiDroid collection. The
        // field is retained for wire/format stability; if note deletion is ever added,
        // populate it in AnkiDroidArchiveCleanup instead of hardcoding 0.
        @JvmField val deletedNotes: Int,
        @JvmField val taggedNotes: Int,
        message: String?,
    ) {
        @JvmField
        val message: String = message ?: ""
    }

    class SyncFailure private constructor(
        message: String?,
        @JvmField val permanentFailure: Boolean,
        cause: Throwable?,
    ) : Exception(message, cause) {
        companion object {
            private const val serialVersionUID = 1L

            @JvmStatic
            fun permanent(message: String?): SyncFailure {
                return SyncFailure(message, true, null)
            }

            @JvmStatic
            fun permanent(message: String?, cause: Throwable?): SyncFailure {
                return SyncFailure(message, true, cause)
            }

            @JvmStatic
            fun retryable(message: String?, cause: Throwable?): SyncFailure {
                return SyncFailure(message, false, cause)
            }

            @JvmStatic
            fun retryable(message: String?): SyncFailure {
                return SyncFailure(message, false, null)
            }
        }
    }

    companion object {
        private const val TAG = "AnkiDroidGateway"
        private const val FIELD_SEPARATOR = '\u001f'
        private const val CONTENT_SCHEME = "content"
        private const val READ_WRITE_DATABASE_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
        private const val DEBUG_READ_WRITE_DATABASE_PERMISSION = "com.ichi2.anki.debug.permission.READ_WRITE_DATABASE"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_FIELD_NAMES = "field_names"
        private const val COLUMN_FIELDS = "flds"
        private const val COLUMN_TAGS = "tags"
        private const val COLUMN_MODEL_ID = "mid"
        private const val URI_SEGMENT_NOTES = "notes"
        private val FIELD_PART_SEPARATOR: Pattern = Pattern.compile(FIELD_SEPARATOR.toString())
        private val NOTES_WHITESPACE_SEPARATOR: Pattern = Pattern.compile("\\s+")

        @JvmStatic
        fun testProvider(context: Context, authority: String): AnkiDroidGateway {
            return AnkiDroidGateway(context, Collections.singletonList(ProviderTarget(authority, null)))
        }

        @JvmStatic
        fun providerInstalled(packageManager: PackageManager, authority: String): Boolean {
            return providerInstalled(packageManager, authority, Build.VERSION.SDK_INT)
        }

        @JvmStatic
        fun providerInstalled(packageManager: PackageManager, authority: String, sdkInt: Int): Boolean {
            return if (isAtLeastTiramisu(sdkInt)) {
                providerInstalledOnTiramisuAndAbove(packageManager, authority)
            } else {
                providerInstalledBeforeTiramisu(packageManager, authority)
            }
        }

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU, parameter = 0)
        private fun isAtLeastTiramisu(sdkInt: Int): Boolean {
            return sdkInt >= Build.VERSION_CODES.TIRAMISU
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun providerInstalledOnTiramisuAndAbove(packageManager: PackageManager, authority: String): Boolean {
            return packageManager.resolveContentProvider(authority, PackageManager.ComponentInfoFlags.of(0)) != null
        }

        @JvmStatic
        fun providerInstalledBeforeTiramisu(packageManager: PackageManager, authority: String): Boolean {
            return packageManager.resolveContentProvider(authority, 0) != null
        }

        @JvmStatic
        private fun markBrowserQueryMatchedCards(
            cards: List<RecordsSyncModels.Card>,
            browserQueryNoteIds: Set<Long>,
        ): List<RecordsSyncModels.Card> {
            if (browserQueryNoteIds.isEmpty()) {
                return cards
            }
            val result = ArrayList<RecordsSyncModels.Card>(cards.size)
            for (card in cards) {
                if (browserQueryNoteIds.contains(card.noteId)) {
                    result.add(card.withBrowserQueryMatched(true))
                } else {
                    result.add(card)
                }
            }
            return result
        }

        @JvmStatic
        private fun splitFields(value: String): List<String> {
            return FIELD_PART_SEPARATOR.split(value, -1).asList()
        }

        @JvmStatic
        private fun splitTags(value: String): List<String> {
            val tags = ArrayList<String>()
            for (tag in NOTES_WHITESPACE_SEPARATOR.split(value)) {
                val trimmed = tag.trim()
                if (trimmed.isNotEmpty()) {
                    tags.add(trimmed)
                }
            }
            return tags
        }

        @JvmStatic
        private fun uriFor(authority: String, vararg segments: String): Uri {
            val builder = Uri.Builder().scheme(CONTENT_SCHEME).authority(authority)
            for (segment in segments) {
                builder.appendPath(segment)
            }
            return builder.build()
        }

        @JvmStatic
        private fun value(cursor: Cursor, column: String): String {
            val index = cursor.getColumnIndex(column)
            if (index < 0 || cursor.isNull(index)) {
                return ""
            }
            return cursor.getString(index)
        }

        @JvmStatic
        private fun longValue(cursor: Cursor, column: String, fallback: Long): Long {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) fallback else cursor.getLong(index)
        }
    }
}
