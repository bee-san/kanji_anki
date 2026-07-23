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

/**
 * Collection-wide note reader kept separate from configured-note-type sync.
 *
 * Raw fields are handed to [NoteConsumer] one row at a time. The gateway never
 * retains or logs note text.
 */
class AnkiDroidCollectionInventoryGateway private constructor(
    context: Context,
    private val targets: List<ProviderTarget>,
    private val cancellation: Cancellation,
) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val packageManager: PackageManager = appContext.packageManager

    constructor(context: Context) : this(context, ProviderTarget.defaults(), Cancellation.NONE)

    constructor(context: Context, cancellation: Cancellation) :
        this(context, ProviderTarget.defaults(), cancellation)

    fun status(): CapabilityStatus {
        val target = resolveTarget()
            ?: return CapabilityStatus(
                installed = false,
                permissionGranted = false,
                canReadCollection = false,
                canWriteCollection = false,
                authority = null,
                permission = null,
                providerSpecVersion = -1,
            )
        val granted = hasPermission(target.permission)
        val specVersion = target.specVersionOverride ?: providerSpecVersion(target.authority)
        return CapabilityStatus(
            installed = true,
            permissionGranted = granted,
            canReadCollection = granted,
            canWriteCollection = granted && specVersion >= WRITE_PROVIDER_SPEC,
            authority = target.authority,
            permission = target.permission,
            providerSpecVersion = specVersion,
        )
    }

    @Throws(Failure::class)
    fun scan(
        consumer: NoteConsumer,
        progress: ProgressListener = ProgressListener.NONE,
    ): ScanResult {
        ensureNotCancelled()
        val target = requireTarget()
        if (!hasPermission(target.permission)) {
            throw Failure(
                FailureKind.PERMISSION_MISSING,
                "AnkiDroid permission is missing: ${target.permission}",
            )
        }
        return try {
            val models = queryModels(target)
            scanDirectSql(target, models, consumer, progress)
        } catch (error: DirectSqlUnsupported) {
            scanLegacySearch(target, error.models, consumer, progress)
        } catch (error: Failure) {
            throw error
        } catch (error: OperationCanceledException) {
            throw Failure(FailureKind.CANCELLED, "AnkiDroid inventory scan was cancelled.", error)
        } catch (error: SecurityException) {
            throw Failure(FailureKind.PERMISSION_MISSING, "AnkiDroid denied collection access.", error)
        } catch (error: RuntimeException) {
            throw Failure(
                FailureKind.PROVIDER_UNAVAILABLE,
                "AnkiDroid could not provide the collection inventory.",
                error,
            )
        }
    }

    private fun queryModels(target: ProviderTarget): Map<Long, ModelInfo> {
        ensureNotCancelled()
        val cursor = resolver.query(uri(target.authority, MODELS_PATH), null, null, null, null)
            ?: throw Failure(
                FailureKind.PROVIDER_UNAVAILABLE,
                "AnkiDroid returned no note-model cursor.",
            )
        val models = LinkedHashMap<Long, ModelInfo>()
        cursor.use {
            while (it.moveToNext()) {
                ensureNotCancelled()
                val id = longValue(it, COLUMN_ID, INVALID_ID)
                val name = stringValue(it, COLUMN_NAME)
                if (id <= 0L || name.isBlank()) {
                    continue
                }
                models[id] = ModelInfo(
                    id = id,
                    name = name,
                    fieldNames = splitFields(stringValue(it, COLUMN_FIELD_NAMES)),
                )
            }
        }
        return models
    }

    private fun scanDirectSql(
        target: ProviderTarget,
        models: Map<Long, ModelInfo>,
        consumer: NoteConsumer,
        progress: ProgressListener,
    ): ScanResult {
        var lastId = 0L
        var notesRead = 0
        var skippedNotes = 0
        while (true) {
            ensureNotCancelled()
            val pageStartId = lastId
            val cursor = try {
                resolver.query(
                    uri(target.authority, NOTES_V2_PATH),
                    null,
                    "$DATABASE_ID > ?",
                    arrayOf(lastId.toString()),
                    "$DATABASE_ID ASC LIMIT $PAGE_SIZE",
                )
            } catch (error: IllegalArgumentException) {
                if (lastId == 0L) {
                    throw DirectSqlUnsupported(models, error)
                }
                throw error
            } catch (error: UnsupportedOperationException) {
                if (lastId == 0L) {
                    throw DirectSqlUnsupported(models, error)
                }
                throw error
            } ?: throw Failure(
                FailureKind.PROVIDER_UNAVAILABLE,
                "AnkiDroid returned no collection-note cursor.",
            )

            var rowsInPage = 0
            cursor.use {
                while (it.moveToNext()) {
                    ensureNotCancelled()
                    rowsInPage += 1
                    val noteId = longValue(it, COLUMN_ID, INVALID_ID)
                    if (noteId <= lastId) {
                        skippedNotes += 1
                        continue
                    }
                    lastId = noteId
                    if (emitNote(it, models, consumer)) {
                        notesRead += 1
                    } else {
                        skippedNotes += 1
                    }
                    progress.onProgress(ScanProgress(notesRead, skippedNotes))
                }
            }
            if (rowsInPage >= PAGE_SIZE && lastId <= pageStartId) {
                throw Failure(
                    FailureKind.PROVIDER_UNAVAILABLE,
                    "AnkiDroid returned a non-advancing collection-note page.",
                )
            }
            if (rowsInPage < PAGE_SIZE) {
                return ScanResult(
                    notesRead = notesRead,
                    skippedNotes = skippedNotes,
                    modelCount = models.size,
                    queryMode = QueryMode.DIRECT_SQL_PAGED,
                )
            }
        }
    }

    private fun scanLegacySearch(
        target: ProviderTarget,
        models: Map<Long, ModelInfo>,
        consumer: NoteConsumer,
        progress: ProgressListener,
    ): ScanResult {
        ensureNotCancelled()
        val cursor = resolver.query(uri(target.authority, NOTES_PATH), null, "", null, null)
            ?: return ScanResult(0, 0, models.size, QueryMode.LEGACY_SEARCH)
        var notesRead = 0
        var skippedNotes = 0
        cursor.use {
            while (it.moveToNext()) {
                ensureNotCancelled()
                if (emitNote(it, models, consumer)) {
                    notesRead += 1
                } else {
                    skippedNotes += 1
                }
                progress.onProgress(ScanProgress(notesRead, skippedNotes))
            }
        }
        return ScanResult(notesRead, skippedNotes, models.size, QueryMode.LEGACY_SEARCH)
    }

    private fun emitNote(
        cursor: Cursor,
        models: Map<Long, ModelInfo>,
        consumer: NoteConsumer,
    ): Boolean {
        val noteId = longValue(cursor, COLUMN_ID, INVALID_ID)
        val modelId = longValue(cursor, COLUMN_MODEL_ID, INVALID_ID)
        val model = models[modelId] ?: return false
        if (noteId <= 0L) {
            return false
        }
        consumer.onNote(
            CollectionNote(
                noteId = noteId,
                modelId = modelId,
                modelName = model.name,
                fieldNames = model.fieldNames,
                fields = splitFields(stringValue(cursor, COLUMN_FIELDS)),
            ),
        )
        return true
    }

    private fun requireTarget(): ProviderTarget {
        return resolveTarget()
            ?: throw Failure(
                FailureKind.NOT_INSTALLED,
                "AnkiDroid's flashcard provider is not installed.",
            )
    }

    private fun resolveTarget(): ProviderTarget? {
        return targets.firstOrNull { providerInstalled(packageManager, it.authority) }
    }

    private fun hasPermission(permission: String?): Boolean {
        return permission.isNullOrBlank() ||
            appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun providerSpecVersion(authority: String): Int {
        val info = if (isAtLeastTiramisu(Build.VERSION.SDK_INT)) {
            resolveProviderWithMetadata(packageManager, authority)
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)
        }
        return info?.metaData?.getInt(PROVIDER_SPEC_METADATA, LEGACY_PROVIDER_SPEC)
            ?: LEGACY_PROVIDER_SPEC
    }

    private fun ensureNotCancelled() {
        if (cancellation.isCancelled()) {
            throw Failure(FailureKind.CANCELLED, "AnkiDroid inventory scan was cancelled.")
        }
    }

    fun interface NoteConsumer {
        fun onNote(note: CollectionNote)
    }

    fun interface ProgressListener {
        fun onProgress(progress: ScanProgress)

        companion object {
            val NONE = ProgressListener { }
        }
    }

    fun interface Cancellation {
        fun isCancelled(): Boolean

        companion object {
            val NONE = Cancellation { false }
        }
    }

    data class CollectionNote(
        val noteId: Long,
        val modelId: Long,
        val modelName: String,
        val fieldNames: List<String>,
        val fields: List<String>,
    )

    data class ScanProgress(
        val notesRead: Int,
        val skippedNotes: Int,
    )

    data class ScanResult(
        val notesRead: Int,
        val skippedNotes: Int,
        val modelCount: Int,
        val queryMode: QueryMode,
    )

    enum class QueryMode {
        DIRECT_SQL_PAGED,
        LEGACY_SEARCH,
    }

    data class CapabilityStatus(
        val installed: Boolean,
        val permissionGranted: Boolean,
        val canReadCollection: Boolean,
        val canWriteCollection: Boolean,
        val authority: String?,
        val permission: String?,
        val providerSpecVersion: Int,
    )

    enum class FailureKind {
        NOT_INSTALLED,
        PERMISSION_MISSING,
        PROVIDER_UNAVAILABLE,
        CANCELLED,
    }

    class Failure(
        val kind: FailureKind,
        message: String,
        cause: Throwable? = null,
    ) : Exception(message, cause) {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    private data class ModelInfo(
        val id: Long,
        val name: String,
        val fieldNames: List<String>,
    )

    private data class ProviderTarget(
        val authority: String,
        val permission: String?,
        val specVersionOverride: Int? = null,
    ) {
        companion object {
            fun defaults(): List<ProviderTarget> = listOf(
                ProviderTarget(PRODUCTION_API_AUTHORITY, PRODUCTION_PERMISSION),
                ProviderTarget(PRODUCTION_LEGACY_AUTHORITY, PRODUCTION_PERMISSION),
                ProviderTarget(DEBUG_API_AUTHORITY, DEBUG_PERMISSION),
                ProviderTarget(DEBUG_LEGACY_AUTHORITY, DEBUG_PERMISSION),
            )
        }
    }

    private class DirectSqlUnsupported(
        val models: Map<Long, ModelInfo>,
        cause: Throwable,
    ) : RuntimeException(cause)

    companion object {
        private const val PAGE_SIZE = 500
        private const val INVALID_ID = -1L
        private const val WRITE_PROVIDER_SPEC = 2
        private const val LEGACY_PROVIDER_SPEC = 1
        private const val FIELD_SEPARATOR = '\u001f'
        private const val CONTENT_SCHEME = "content"
        private const val NOTES_PATH = "notes"
        private const val NOTES_V2_PATH = "notes_v2"
        private const val MODELS_PATH = "models"
        private const val DATABASE_ID = "id"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_MODEL_ID = "mid"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_FIELD_NAMES = "field_names"
        private const val COLUMN_FIELDS = "flds"
        private const val PROVIDER_SPEC_METADATA = "com.ichi2.anki.provider.spec"
        private const val PRODUCTION_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
        private const val DEBUG_PERMISSION = "com.ichi2.anki.debug.permission.READ_WRITE_DATABASE"
        private const val PRODUCTION_API_AUTHORITY = "com.ichi2.anki.api.provider"
        private const val PRODUCTION_LEGACY_AUTHORITY = "com.ichi2.anki.flashcards"
        private const val DEBUG_API_AUTHORITY = "com.ichi2.anki.debug.api.provider"
        private const val DEBUG_LEGACY_AUTHORITY = "com.ichi2.anki.debug.flashcards"

        fun testProvider(
            context: Context,
            authority: String,
            cancellation: Cancellation = Cancellation.NONE,
        ): AnkiDroidCollectionInventoryGateway {
            return AnkiDroidCollectionInventoryGateway(
                context,
                listOf(ProviderTarget(authority, null, WRITE_PROVIDER_SPEC)),
                cancellation,
            )
        }

        @JvmStatic
        fun providerInstalled(packageManager: PackageManager, authority: String): Boolean {
            return if (isAtLeastTiramisu(Build.VERSION.SDK_INT)) {
                providerInstalledOnTiramisuAndAbove(packageManager, authority)
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveContentProvider(authority, 0) != null
            }
        }

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU, parameter = 0)
        private fun isAtLeastTiramisu(sdkInt: Int): Boolean {
            return sdkInt >= Build.VERSION_CODES.TIRAMISU
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun providerInstalledOnTiramisuAndAbove(
            packageManager: PackageManager,
            authority: String,
        ): Boolean {
            return packageManager.resolveContentProvider(
                authority,
                PackageManager.ComponentInfoFlags.of(0),
            ) != null
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun resolveProviderWithMetadata(
            packageManager: PackageManager,
            authority: String,
        ) = packageManager.resolveContentProvider(
            authority,
            PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
        )

        private fun uri(authority: String, path: String): Uri {
            return Uri.Builder()
                .scheme(CONTENT_SCHEME)
                .authority(authority)
                .appendPath(path)
                .build()
        }

        private fun splitFields(value: String): List<String> {
            return value.split(FIELD_SEPARATOR)
        }

        private fun stringValue(cursor: Cursor, column: String): String {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) "" else cursor.getString(index)
        }

        private fun longValue(cursor: Cursor, column: String, fallback: Long): Long {
            val index = cursor.getColumnIndex(column)
            return if (index < 0 || cursor.isNull(index)) fallback else cursor.getLong(index)
        }

    }
}
