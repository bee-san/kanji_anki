package dev.bee.kanjianki.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.JitenKanjiRanks
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale
import java.util.regex.Pattern

class DictionaryStore private constructor(private val databaseFile: File) : DictionaryLookup() {
    private val manifestFile = File(databaseFile.parentFile, PRIVATE_MANIFEST)
    private val checksumFile = File(databaseFile.parentFile, PRIVATE_CHECKSUM)

    // The dictionary is queried from the main-thread study render path (answer
    // panels, choice questions). Opening a fresh SQLiteDatabase per lookup was the
    // dominant cost of those queries, so keep one shared read-only connection open
    // for the lifetime of this store and cache recent per-kanji entries in memory.
    // SQLiteDatabase is internally synchronized, so concurrent reads are safe.
    private val connectionLock = Any()

    @Volatile
    private var readDatabase: SQLiteDatabase? = null

    private val entryCache = object : LinkedHashMap<String, KanjiEntry?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, KanjiEntry?>): Boolean {
            return size > ENTRY_CACHE_MAX
        }
    }

    fun installVerifiedDictionary(database: File, manifest: File, checksum: File): InstallResult {
        val temp = File(databaseFile.parentFile, PRIVATE_DB + INSTALLING_SUFFIX)
        val tempManifest = File(databaseFile.parentFile, PRIVATE_MANIFEST + INSTALLING_SUFFIX)
        val tempChecksum = File(databaseFile.parentFile, PRIVATE_CHECKSUM + INSTALLING_SUFFIX)
        return try {
            copy(database, temp)
            val manifestText = readText(manifest)
            val checksumText = readText(checksum)
            val expectedHash = firstSha256(checksumText)
            val validation = validateDictionary(temp, expectedHash, manifestText)
            if (!validation.ok) {
                deleteQuietly(temp)
                return InstallResult.rejected(validation.message)
            }
            writeText(tempManifest, manifestText)
            writeText(tempChecksum, checksumText)
            atomicReplace(temp, databaseFile)
            atomicReplace(tempManifest, manifestFile)
            atomicReplace(tempChecksum, checksumFile)
            invalidateCaches()
            InstallResult.installed("Dictionary installed.")
        } catch (error: IOException) {
            deleteQuietly(temp)
            deleteQuietly(tempManifest)
            deleteQuietly(tempChecksum)
            InstallResult.rejected("Dictionary install failed: " + readableMessage(error))
        }
    }

    override fun lookupKanji(literal: String?): KanjiEntry? {
        val normalized = normalize(literal)
        if (normalized.isEmpty()) {
            return null
        }
        synchronized(entryCache) {
            if (entryCache.containsKey(normalized)) {
                return entryCache[normalized]
            }
        }
        return try {
            val entry = queryKanji(normalized)
            synchronized(entryCache) {
                entryCache[normalized] = entry
            }
            entry
        } catch (_: SQLiteException) {
            // The connection may be stale (e.g. the database file was replaced by an
            // install); drop it so the next lookup reopens, and do not cache the miss.
            invalidateConnection()
            null
        }
    }

    private fun queryKanji(normalized: String): KanjiEntry? {
        return readableDatabase().query(
            "kanji",
            null,
            "literal=?",
            arrayOf(normalized),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                KanjiEntry(
                    KanjiEntryFields(
                        string(cursor, COLUMN_LITERAL),
                        splitList(string(cursor, "meanings")),
                        splitList(string(cursor, "on_readings")),
                        splitList(string(cursor, "kun_readings")),
                        splitList(string(cursor, "nanori_readings")),
                        integer(cursor, "stroke_count"),
                        integer(cursor, "grade"),
                        integer(cursor, "radical"),
                        integer(cursor, "kanjidic_frequency"),
                        nullableInteger(cursor, "jiten_rank")
                    )
                )
            }
        }
    }

    override fun kanjiCount(): Int {
        return try {
            readableDatabase().rawQuery("SELECT COUNT(*) FROM kanji", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        } catch (_: SQLiteException) {
            invalidateConnection()
            0
        }
    }

    override fun jitenRanks(): JitenKanjiRanks {
        val ranks = LinkedHashMap<String, Int>()
        return try {
            readableDatabase().rawQuery("SELECT literal, rank FROM jiten_ranks ORDER BY rank ASC, literal ASC", null).use { cursor ->
                while (cursor.moveToNext()) {
                    ranks[cursor.getString(0)] = cursor.getInt(1)
                }
            }
            JitenKanjiRanks(ranks)
        } catch (_: SQLiteException) {
            invalidateConnection()
            JitenKanjiRanks.empty()
        }
    }

    private fun readableDatabase(): SQLiteDatabase {
        readDatabase?.let { if (it.isOpen) return it }
        synchronized(connectionLock) {
            readDatabase?.let { if (it.isOpen) return it }
            val db = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            readDatabase = db
            return db
        }
    }

    private fun invalidateConnection() {
        synchronized(connectionLock) {
            readDatabase?.let { db -> runCatching { db.close() } }
            readDatabase = null
        }
    }

    private fun invalidateCaches() {
        invalidateConnection()
        synchronized(entryCache) {
            entryCache.clear()
        }
    }

    fun interface AssetOpener {
        @Throws(IOException::class)
        fun open(asset: String): InputStream
    }

    fun interface FileMover {
        @Throws(IOException::class)
        fun move(source: java.nio.file.Path, target: java.nio.file.Path, vararg options: StandardCopyOption)
    }

    class InstallResult private constructor(
        @JvmField val ok: Boolean,
        @JvmField val message: String,
    ) {
        companion object {
            @JvmStatic
            fun installed(message: String?): InstallResult {
                return InstallResult(true, message ?: "")
            }

            @JvmStatic
            fun rejected(message: String?): InstallResult {
                return InstallResult(false, message ?: "")
            }
        }
    }

    private class ValidationResult private constructor(
        val ok: Boolean,
        val message: String,
    ) {
        companion object {
            @JvmStatic
            fun ok(): ValidationResult {
                return ValidationResult(true, "")
            }

            @JvmStatic
            fun rejected(message: String?): ValidationResult {
                return ValidationResult(false, message ?: "")
            }
        }
    }

    companion object {
        private const val ENTRY_CACHE_MAX = 512
        private val SHA256_HEX_PATTERN: Pattern = Pattern.compile("[0-9a-fA-F]{64}")
        private val CHECKSUM_PART_SEPARATOR: Pattern = Pattern.compile("\\s+")
        private const val COLUMN_LITERAL = "literal"
        private const val INSTALLING_SUFFIX = ".installing"
        private const val BUNDLED_SUFFIX = ".bundled"
        private const val PRIVATE_DIR = "dictionaries"
        private const val PRIVATE_DB = "kanji_dictionary.db"
        private const val PRIVATE_MANIFEST = "dictionary_sources.json"
        private const val PRIVATE_CHECKSUM = "kanji_dictionary.db.sha256"
        private const val BUNDLE_MARKER = "kanji_dictionary.bundle.sha256"
        private const val SUPPORTED_SCHEMA_VERSION = "1"
        private const val LIST_SEPARATOR = "\u001f"
        private val LIST_PART_SEPARATOR: Pattern = Pattern.compile(LIST_SEPARATOR)
        private val REQUIRED_KANJI_COLUMNS: Set<String> = setOf(
            COLUMN_LITERAL,
            "meanings",
            "on_readings",
            "kun_readings",
            "nanori_readings",
            "stroke_count",
            "grade",
            "radical",
            "kanjidic_frequency",
            "jiten_rank"
        )
        private val REQUIRED_JITEN_RANK_COLUMNS: Set<String> = setOf(COLUMN_LITERAL, "rank")
        private val REQUIRED_META_KEYS: Set<String> = setOf(
            "schema_version",
            "generated_at",
            "kanjidic2_source_sha256",
            "kanjidic2_database_version",
            "jiten_rank_source_sha256"
        )

        @JvmStatic
        @Throws(IOException::class)
        fun open(context: Context): DictionaryStore {
            val appContext = context.applicationContext
            val directory = File(appContext.filesDir, PRIVATE_DIR)
            val database = File(directory, PRIVATE_DB)
            ensureBundledDictionaryInstalled(appContext, directory, database)
            return DictionaryStore(database)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun activeManifestText(context: Context): String {
            val appContext = context.applicationContext
            val directory = File(appContext.filesDir, PRIVATE_DIR)
            val database = File(directory, PRIVATE_DB)
            ensureBundledDictionaryInstalled(appContext, directory, database)
            return readText(File(directory, PRIVATE_MANIFEST))
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun ensureBundledDictionaryInstalled(context: Context, directory: File, database: File) {
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Could not create dictionary directory.")
            }
            ensureBundledDictionaryInstalled(
                AssetOpener { asset -> context.assets.open(asset) },
                directory,
                database
            )
        }

        @JvmStatic
        @Throws(IOException::class)
        fun ensureBundledDictionaryInstalled(assets: AssetOpener, directory: File, database: File) {
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Could not create dictionary directory.")
            }
            val bundledHash = readExpectedHash(assets.open(DictionaryAssets.DATABASE_SHA256_ASSET))
            val bundledManifest = readAssetText(assets, DictionaryAssets.SOURCES_ASSET)
            val bundledChecksum = readAssetText(assets, DictionaryAssets.DATABASE_SHA256_ASSET)
            val marker = File(directory, BUNDLE_MARKER)
            val manifest = File(directory, PRIVATE_MANIFEST)
            val checksum = File(directory, PRIVATE_CHECKSUM)
            if (database.exists() &&
                manifest.exists() &&
                checksum.exists() &&
                bundledHash == readMarker(marker)
            ) {
                return
            }
            val temp = File(directory, PRIVATE_DB + BUNDLED_SUFFIX)
            val tempManifest = File(directory, PRIVATE_MANIFEST + BUNDLED_SUFFIX)
            val tempChecksum = File(directory, PRIVATE_CHECKSUM + BUNDLED_SUFFIX)
            assets.open(DictionaryAssets.DATABASE_ASSET).use { source -> copy(source, temp) }
            val validation = validateDictionary(temp, bundledHash, bundledManifest)
            if (!validation.ok) {
                deleteQuietly(temp)
                throw IOException(validation.message)
            }
            writeText(tempManifest, bundledManifest)
            writeText(tempChecksum, bundledChecksum)
            atomicReplace(temp, database)
            atomicReplace(tempManifest, manifest)
            atomicReplace(tempChecksum, checksum)
            writeMarker(marker, bundledHash)
        }

        @Throws(IOException::class)
        private fun readAssetText(assets: AssetOpener, asset: String): String {
            assets.open(asset).use { input -> return readText(input) }
        }

        @JvmStatic
        private fun validateDictionary(database: File, expectedHash: String?, manifest: String?): ValidationResult {
            if (expectedHash == null || !SHA256_HEX_PATTERN.matcher(expectedHash).matches()) {
                return ValidationResult.rejected("Dictionary checksum is missing or invalid.")
            }
            return try {
                val actualHash = sha256(database)
                if (!expectedHash.equals(actualHash, ignoreCase = true)) {
                    return ValidationResult.rejected("Dictionary checksum mismatch.")
                }
                val manifestResult = validateManifest(manifest, expectedHash)
                if (!manifestResult.ok) {
                    return manifestResult
                }
                validateSqlite(database)
            } catch (error: IOException) {
                ValidationResult.rejected("Dictionary validation failed: " + readableMessage(error))
            }
        }

        @JvmStatic
        private fun validateManifest(manifest: String?, expectedHash: String): ValidationResult {
            return try {
                val root = JSONObject(manifest ?: "")
                if (!hasMatchingDatabaseAsset(root.optJSONArray("assets"), expectedHash)) {
                    return ValidationResult.rejected("Dictionary manifest does not match the database checksum.")
                }
                if (hasKanjidic2Source(root.optJSONArray("sources"))) {
                    ValidationResult.ok()
                } else {
                    ValidationResult.rejected("Dictionary manifest is missing KANJIDIC2 metadata.")
                }
            } catch (_: JSONException) {
                ValidationResult.rejected("Dictionary manifest is invalid JSON.")
            }
        }

        @Throws(JSONException::class)
        private fun hasMatchingDatabaseAsset(assets: JSONArray?, expectedHash: String): Boolean {
            if (assets == null) {
                return false
            }
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (DictionaryAssets.DATABASE_ASSET_NAME == asset.optString("path") &&
                    expectedHash.equals(asset.optString("sha256"), ignoreCase = true)
                ) {
                    return true
                }
            }
            return false
        }

        @Throws(JSONException::class)
        private fun hasKanjidic2Source(sources: JSONArray?): Boolean {
            if (sources == null) {
                return false
            }
            for (i in 0 until sources.length()) {
                val source = sources.getJSONObject(i)
                if ("kanjidic2" == source.optString("id") &&
                    source.optString("source_sha256").isNotEmpty() &&
                    source.optString("database_version").isNotEmpty()
                ) {
                    return true
                }
            }
            return false
        }

        private fun validateSqlite(database: File): ValidationResult {
            return try {
                SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    validateSqlite(db)
                }
            } catch (_: SQLiteException) {
                ValidationResult.rejected("Dictionary database is invalid.")
            }
        }

        private fun validateSqlite(db: SQLiteDatabase): ValidationResult {
            validateColumnsOrReject(db, "kanji", REQUIRED_KANJI_COLUMNS)?.let { return it }
            validateColumnsOrReject(db, "jiten_ranks", REQUIRED_JITEN_RANK_COLUMNS)?.let { return it }
            validateColumnsOrReject(db, "dictionary_meta", setOf("key", "value"))?.let { return it }
            validateMeta(readMeta(db))?.let { return it }
            return validateHasKanjiRows(db)
        }

        private fun validateColumnsOrReject(
            db: SQLiteDatabase,
            table: String,
            requiredColumns: Set<String>,
        ): ValidationResult? {
            val validation = validateColumns(db, table, requiredColumns)
            return if (validation.ok) null else validation
        }

        private fun validateMeta(meta: Map<String, String>): ValidationResult? {
            for (key in REQUIRED_META_KEYS) {
                if (!meta.containsKey(key) || meta[key].orEmpty().isEmpty()) {
                    return ValidationResult.rejected("Dictionary metadata is missing $key.")
                }
            }
            if (SUPPORTED_SCHEMA_VERSION != meta["schema_version"]) {
                return ValidationResult.rejected("Dictionary schema version is unsupported.")
            }
            return null
        }

        private fun validateHasKanjiRows(db: SQLiteDatabase): ValidationResult {
            db.rawQuery("SELECT literal FROM kanji LIMIT 1", null).use { cursor ->
                return if (cursor.moveToFirst()) {
                    ValidationResult.ok()
                } else {
                    ValidationResult.rejected("Dictionary has no kanji rows.")
                }
            }
        }

        private fun validateColumns(db: SQLiteDatabase, table: String, requiredColumns: Set<String>): ValidationResult {
            val columns = HashSet<String>()
            db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                while (cursor.moveToNext()) {
                    columns.add(string(cursor, "name"))
                }
            }
            if (columns.isEmpty()) {
                return ValidationResult.rejected("Dictionary is missing $table table.")
            }
            for (required in requiredColumns) {
                if (!columns.contains(required)) {
                    return ValidationResult.rejected("Dictionary $table table is missing $required.")
                }
            }
            return ValidationResult.ok()
        }

        private fun readMeta(db: SQLiteDatabase): Map<String, String> {
            val meta = LinkedHashMap<String, String>()
            db.query("dictionary_meta", arrayOf("key", "value"), null, null, null, null, "key ASC").use { cursor ->
                while (cursor.moveToNext()) {
                    meta[cursor.getString(0)] = cursor.getString(1)
                }
            }
            return meta
        }

        private fun splitList(value: String?): List<String> {
            if (value.isNullOrEmpty()) {
                return emptyList()
            }
            val out: MutableList<String> = ArrayList()
            for (cell in LIST_PART_SEPARATOR.split(value, -1)) {
                val trimmed = cell.trim()
                if (trimmed.isNotEmpty()) {
                    out.add(trimmed)
                }
            }
            return out
        }

        private fun string(cursor: Cursor, column: String): String {
            val index = cursor.getColumnIndexOrThrow(column)
            return if (cursor.isNull(index)) "" else cursor.getString(index)
        }

        private fun integer(cursor: Cursor, column: String): Int {
            val index = cursor.getColumnIndexOrThrow(column)
            return if (cursor.isNull(index)) 0 else cursor.getInt(index)
        }

        private fun nullableInteger(cursor: Cursor, column: String): Int? {
            val index = cursor.getColumnIndexOrThrow(column)
            return if (cursor.isNull(index)) null else cursor.getInt(index)
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun copy(source: File, target: File) {
            FileInputStream(source).use { input -> copy(input, target) }
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun copy(source: InputStream, target: File) {
            val parent = target.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw IOException("Could not create $parent.")
            }
            BufferedInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) {
                            break
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun atomicReplace(source: File, target: File) {
            atomicReplace(
                source,
                target,
                FileMover { sourcePath, targetPath, options ->
                    Files.move(sourcePath, targetPath, *options)
                }
            )
        }

        @JvmStatic
        @Throws(IOException::class)
        fun atomicReplace(source: File, target: File, mover: FileMover) {
            try {
                mover.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                mover.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun readExpectedHash(checksum: File): String {
            return firstSha256(readText(checksum))
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun readExpectedHash(checksum: InputStream): String {
            checksum.use { input -> return firstSha256(readText(input)) }
        }

        private fun firstSha256(text: String?): String {
            val value = text?.trim() ?: ""
            val parts = CHECKSUM_PART_SEPARATOR.split(value)
            return if (parts.isEmpty()) "" else parts[0].lowercase(Locale.ROOT)
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun readText(file: File): String {
            FileInputStream(file).use { input -> return readText(input) }
        }

        @Throws(IOException::class)
        private fun writeText(file: File, value: String?) {
            FileOutputStream(file).use { output ->
                output.write((value ?: "").toByteArray(StandardCharsets.UTF_8))
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun readText(input: InputStream): String {
            val out = StringBuilder()
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    out.append(line).append('\n')
                }
            }
            return out.toString()
        }

        @JvmStatic
        private fun readMarker(marker: File): String {
            return try {
                if (marker.exists()) readText(marker).trim() else ""
            } catch (_: IOException) {
                ""
            }
        }

        @Throws(IOException::class)
        private fun writeMarker(marker: File, value: String) {
            FileOutputStream(marker).use { output ->
                output.write((value + "\n").toByteArray(StandardCharsets.UTF_8))
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        private fun sha256(file: File): String {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                FileInputStream(file).use { source ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = source.read(buffer)
                        if (read == -1) {
                            break
                        }
                        digest.update(buffer, 0, read)
                    }
                }
                val out = StringBuilder()
                for (byte in digest.digest()) {
                    out.append(String.format(Locale.ROOT, "%02x", byte))
                }
                out.toString()
            } catch (error: NoSuchAlgorithmException) {
                throw IOException(error)
            }
        }

        @JvmStatic
        private fun readableMessage(error: Throwable): String {
            val message = error.message
            return if (message == null || message.trim().isEmpty()) {
                error.javaClass.simpleName
            } else {
                message
            }
        }

        private fun deleteQuietly(file: File?) {
            if (file != null && file.exists()) {
                val deleted = file.delete()
                if (!deleted && file.exists()) {
                    return
                }
            }
        }
    }
}
