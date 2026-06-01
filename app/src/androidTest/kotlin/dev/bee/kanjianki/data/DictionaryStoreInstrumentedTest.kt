package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SuspendedKanjiImporter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.security.Security
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DictionaryStoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var privateDictionaryDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        privateDictionaryDir = File(context.filesDir, "dictionaries")
        deleteRecursively(privateDictionaryDir)
    }

    @After
    fun tearDown() {
        deleteRecursively(privateDictionaryDir)
    }

    @Test
    fun lookupReadsOneKanjiFromInstalledSQLiteDatabase() {
        val store = DictionaryStore.open(context)

        val entry = store.lookupKanji("日")

        assertEquals(13108, store.kanjiCount())
        assertNotNull(entry)
        assertEquals("日", entry!!.literal)
        assertFalse(entry.meanings.isEmpty())
        assertNotNull(entry.jitenRank)
        assertEquals(10666, store.jitenRanks().size())
        assertEquals(1, store.jitenRanks().rankOf("人") ?: 0)
    }

    @Test
    fun studyCueUsesKanjidicMeaningAndAnkiReadingAndFromWord() {
        val store = DictionaryStore.open(context)

        val cue = store.studyCue("日", "fallback from note", "ニチ", "日本", "にほん")

        assertFalse(cue.meaning.contains("fallback"))
        assertEquals("にほん", cue.reading)
        assertEquals("日本", cue.fromExpression)
        assertEquals(DictionaryLookup.SOURCE_KANJIDIC2, cue.meaningSource)
    }

    @Test
    fun jitenRankFilteringUsesDictionaryStoreRanks() {
        val store = DictionaryStore.open(context)
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(note(1L, "人")),
            listOf(RecordsSyncModels.Card(10L, 1L, 0, "Mining", -1, 0, 0, 0, 0, 0, true))
        )

        val imports = SuspendedKanjiImporter(store.jitenRanks(), 1, 1).importFrom(snapshot, settings)

        assertEquals(1, imports.size)
        assertEquals("人", imports.get(0).kanji)
        assertEquals(1, imports.get(0).jitenRank ?: 0)
    }

    @Test
    fun invalidFutureDictionaryPackageIsRejectedWithoutReplacingActiveDatabase() {
        val store = DictionaryStore.open(context)
        val before = store.lookupKanji("日")
        val packageDir = packageDir("dictionary-package-future-test")
        try {
            val database = File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME)
            val manifest = File(packageDir, "dictionary_sources.json")
            val checksum = File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME)
            copyAsset(DictionaryAssets.DATABASE_ASSET, database)
            copyAsset(DictionaryAssets.SOURCES_ASSET, manifest)
            writeText(checksum, "0000000000000000000000000000000000000000000000000000000000000000  kanji_dictionary.db\n")

            val result = store.installVerifiedDictionary(database, manifest, checksum)
            val after = store.lookupKanji("日")

            assertFalse(result.ok)
            assertNotNull(before)
            assertNotNull(after)
            assertEquals(before!!.meanings, after!!.meanings)
            assertEquals(before.jitenRank, after.jitenRank)
        } finally {
            deleteRecursively(packageDir)
        }
    }

    @Test
    fun missingManifestPackageIsRejectedWithoutReplacingActiveDatabase() {
        val store = DictionaryStore.open(context)
        val before = store.lookupKanji("日")
        val packageDir = packageDir("dictionary-package-missing-manifest-test")
        try {
            val database = File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME)
            val manifest = File(packageDir, "missing_dictionary_sources.json")
            val checksum = File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME)
            copyAsset(DictionaryAssets.DATABASE_ASSET, database)
            copyAsset(DictionaryAssets.DATABASE_SHA256_ASSET, checksum)

            val result = store.installVerifiedDictionary(database, manifest, checksum)

            assertFalse(result.ok)
            assertTrue(result.message.contains("Dictionary install failed"))
            assertActiveEntryStillMatches(store, before)
        } finally {
            deleteRecursively(packageDir)
        }
    }

    @Test
    fun missingChecksumPackageIsRejectedWithoutReplacingActiveDatabase() {
        val store = DictionaryStore.open(context)
        val before = store.lookupKanji("日")
        val packageDir = packageDir("dictionary-package-missing-checksum-test")
        try {
            val database = File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME)
            val manifest = File(packageDir, "dictionary_sources.json")
            val checksum = File(packageDir, "missing_kanji_dictionary.db.sha256")
            copyAsset(DictionaryAssets.DATABASE_ASSET, database)
            copyAsset(DictionaryAssets.SOURCES_ASSET, manifest)

            val result = store.installVerifiedDictionary(database, manifest, checksum)

            assertFalse(result.ok)
            assertTrue(result.message.contains("Dictionary install failed"))
            assertActiveEntryStillMatches(store, before)
        } finally {
            deleteRecursively(packageDir)
        }
    }

    @Test
    fun manifestWithoutDatabaseAssetIsRejectedWithoutReplacingActiveDatabase() {
        val store = DictionaryStore.open(context)
        val before = store.lookupKanji("日")
        val packageDir = packageDir("dictionary-package-missing-asset-test")
        try {
            val database = File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME)
            val manifest = File(packageDir, "dictionary_sources.json")
            val checksum = File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME)
            copyAsset(DictionaryAssets.DATABASE_ASSET, database)
            copyAsset(DictionaryAssets.DATABASE_SHA256_ASSET, checksum)
            writeText(
                manifest,
                assetText(DictionaryAssets.SOURCES_ASSET)
                    .replace("\"path\": \"kanji_dictionary.db\"", "\"path\": \"other_dictionary.db\"")
            )

            val result = store.installVerifiedDictionary(database, manifest, checksum)

            assertFalse(result.ok)
            assertEquals("Dictionary manifest does not match the database checksum.", result.message)
            assertActiveEntryStillMatches(store, before)
        } finally {
            deleteRecursively(packageDir)
        }
    }

    @Test
    fun manifestWithoutKanjidicSourceIsRejectedWithoutReplacingActiveDatabase() {
        val store = DictionaryStore.open(context)
        val before = store.lookupKanji("日")
        val packageDir = packageDir("dictionary-package-missing-source-test")
        try {
            val database = File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME)
            val manifest = File(packageDir, "dictionary_sources.json")
            val checksum = File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME)
            copyAsset(DictionaryAssets.DATABASE_ASSET, database)
            copyAsset(DictionaryAssets.DATABASE_SHA256_ASSET, checksum)
            writeText(
                manifest,
                assetText(DictionaryAssets.SOURCES_ASSET)
                    .replace("\"id\": \"kanjidic2\"", "\"id\": \"not_kanjidic2\"")
            )

            val result = store.installVerifiedDictionary(database, manifest, checksum)

            assertFalse(result.ok)
            assertEquals("Dictionary manifest is missing KANJIDIC2 metadata.", result.message)
            assertActiveEntryStillMatches(store, before)
        } finally {
            deleteRecursively(packageDir)
        }
    }

    @Test
    fun sqlitePackageMissingKanjiTableIsRejectedWithoutReplacingActiveDatabase() {
        val store = DictionaryStore.open(context)
        val before = store.lookupKanji("日")
        val packageDir = packageDir("dictionary-package-missing-table-test")
        try {
            val database = File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME)
            val manifest = File(packageDir, "dictionary_sources.json")
            val checksum = File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME)
            SQLiteDatabase.openOrCreateDatabase(database, null).close()
            val hash = sha256(database)
            writeText(checksum, "$hash  kanji_dictionary.db\n")
            writeText(
                manifest,
                assetText(DictionaryAssets.SOURCES_ASSET)
                    .replace(firstSha256(assetText(DictionaryAssets.DATABASE_SHA256_ASSET)), hash)
            )

            val result = store.installVerifiedDictionary(database, manifest, checksum)

            assertFalse(result.ok)
            assertEquals("Dictionary is missing kanji table.", result.message)
            assertActiveEntryStillMatches(store, before)
        } finally {
            deleteRecursively(packageDir)
        }
    }

    @Test
    fun sqlitePackagesMissingRequiredTablesOrMetaAreRejected() {
        val store = DictionaryStore.open(context)
        val before = store.lookupKanji("日")

        assertSyntheticPackageRejected(
            store,
            "dictionary-package-missing-jiten-table-test",
            includeJitenTable = false,
            includeMetaTable = true,
            includeRequiredMeta = true,
            schemaVersion = "1",
            includeJitenRankColumn = true,
            expectedMessage = "Dictionary is missing jiten_ranks table."
        )
        assertSyntheticPackageRejected(
            store,
            "dictionary-package-missing-meta-table-test",
            includeJitenTable = true,
            includeMetaTable = false,
            includeRequiredMeta = true,
            schemaVersion = "1",
            includeJitenRankColumn = true,
            expectedMessage = "Dictionary is missing dictionary_meta table."
        )
        assertSyntheticPackageRejected(
            store,
            "dictionary-package-missing-meta-key-test",
            includeJitenTable = true,
            includeMetaTable = true,
            includeRequiredMeta = false,
            schemaVersion = "1",
            includeJitenRankColumn = true,
            expectedMessage = "Dictionary metadata is missing schema_version."
        )
        assertSyntheticPackageRejected(
            store,
            "dictionary-package-unsupported-schema-test",
            includeJitenTable = true,
            includeMetaTable = true,
            includeRequiredMeta = true,
            schemaVersion = "99",
            includeJitenRankColumn = true,
            expectedMessage = "Dictionary schema version is unsupported."
        )
        assertSyntheticPackageRejected(
            store,
            "dictionary-package-missing-kanji-column-test",
            includeJitenTable = true,
            includeMetaTable = true,
            includeRequiredMeta = true,
            schemaVersion = "1",
            includeJitenRankColumn = false,
            expectedMessage = "Dictionary kanji table is missing jiten_rank."
        )
        assertActiveEntryStillMatches(store, before)
    }

    @Test
    fun malformedManifestAndChecksumPackagesAreRejectedWithSpecificMessages() {
        val store = DictionaryStore.open(context)
        val before = store.lookupKanji("日")
        val badManifestPackage = packageDir("dictionary-package-bad-manifest-test")
        try {
            val database = File(badManifestPackage, DictionaryAssets.DATABASE_ASSET_NAME)
            val manifest = File(badManifestPackage, "dictionary_sources.json")
            val checksum = File(badManifestPackage, DictionaryAssets.DATABASE_SHA256_ASSET_NAME)
            copyAsset(DictionaryAssets.DATABASE_ASSET, database)
            copyAsset(DictionaryAssets.DATABASE_SHA256_ASSET, checksum)
            writeText(manifest, "{not-json")

            val result = store.installVerifiedDictionary(database, manifest, checksum)

            assertFalse(result.ok)
            assertEquals("Dictionary manifest is invalid JSON.", result.message)
        } finally {
            deleteRecursively(badManifestPackage)
        }

        val badChecksumPackage = packageDir("dictionary-package-bad-checksum-test")
        try {
            val database = File(badChecksumPackage, DictionaryAssets.DATABASE_ASSET_NAME)
            val manifest = File(badChecksumPackage, "dictionary_sources.json")
            val checksum = File(badChecksumPackage, DictionaryAssets.DATABASE_SHA256_ASSET_NAME)
            copyAsset(DictionaryAssets.DATABASE_ASSET, database)
            copyAsset(DictionaryAssets.SOURCES_ASSET, manifest)
            writeText(checksum, "not-a-sha  kanji_dictionary.db\n")

            val result = store.installVerifiedDictionary(database, manifest, checksum)

            assertFalse(result.ok)
            assertEquals("Dictionary checksum is missing or invalid.", result.message)
            assertActiveEntryStillMatches(store, before)
        } finally {
            deleteRecursively(badChecksumPackage)
        }
    }

    @Test
    fun invalidSqlitePackageIsRejectedAfterChecksumAndManifestMatch() {
        val store = DictionaryStore.open(context)
        val before = store.lookupKanji("日")
        val packageDir = packageDir("dictionary-package-invalid-sqlite-test")
        try {
            val database = File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME)
            val manifest = File(packageDir, "dictionary_sources.json")
            val checksum = File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME)
            writeText(database, "not sqlite")
            val hash = sha256(database)
            writeText(checksum, "$hash  kanji_dictionary.db\n")
            writeText(
                manifest,
                assetText(DictionaryAssets.SOURCES_ASSET)
                    .replace(firstSha256(assetText(DictionaryAssets.DATABASE_SHA256_ASSET)), hash)
            )

            val result = store.installVerifiedDictionary(database, manifest, checksum)

            assertFalse(result.ok)
            assertEquals("Dictionary database is invalid.", result.message)
            assertActiveEntryStillMatches(store, before)
        } finally {
            deleteRecursively(packageDir)
        }
    }

    @Test
    fun sqlitePackageWithNoKanjiRowsIsRejectedAfterSchemaValidation() {
        val store = DictionaryStore.open(context)
        val before = store.lookupKanji("日")

        assertSyntheticPackageRejected(
            store,
            "dictionary-package-empty-kanji-test",
            includeJitenTable = true,
            includeMetaTable = true,
            includeRequiredMeta = true,
            schemaVersion = "1",
            includeJitenRankColumn = true,
            expectedMessage = "Dictionary has no kanji rows."
        )
        assertActiveEntryStillMatches(store, before)
    }

    @Test
    fun bundledDictionaryInstallRejectsInvalidDatabaseAsset() {
        val installDir = packageDir("dictionary-invalid-bundled-test")
        val database = File(installDir, DictionaryAssets.DATABASE_ASSET_NAME)
        val invalidDatabase = "not sqlite".toByteArray(StandardCharsets.UTF_8)
        val hash = sha256(invalidDatabase)
        val assets = mapOf(
            DictionaryAssets.DATABASE_ASSET to invalidDatabase,
            DictionaryAssets.DATABASE_SHA256_ASSET to "$hash  kanji_dictionary.db\n".toByteArray(StandardCharsets.UTF_8),
            DictionaryAssets.SOURCES_ASSET to assetText(DictionaryAssets.SOURCES_ASSET)
                .replace(firstSha256(assetText(DictionaryAssets.DATABASE_SHA256_ASSET)), hash)
                .toByteArray(StandardCharsets.UTF_8)
        )

        try {
            DictionaryStore.ensureBundledDictionaryInstalled(
                { asset -> assets[asset]?.inputStream() ?: throw IOException("missing fake asset $asset") },
                installDir,
                database
            )
            throw AssertionError("Expected bundled install to reject the invalid SQLite asset")
        } catch (error: IOException) {
            assertEquals("Dictionary database is invalid.", error.message)
            assertFalse(database.exists())
            assertFalse(File(installDir, "dictionary_sources.json").exists())
        } finally {
            deleteRecursively(installDir)
        }
    }

    @Test
    fun bundledDictionaryInstallFailsWhenPrivatePathCannotBeCreated() {
        val blockedParent = File(context.cacheDir, "dictionary-blocked-directory-test")
        deleteRecursively(blockedParent)
        writeText(blockedParent, "not a directory")
        val blockedDirectory = File(blockedParent, "child")

        try {
            DictionaryStore.ensureBundledDictionaryInstalled(
                { throw IOException("assets should not be opened") },
                blockedDirectory,
                File(blockedDirectory, DictionaryAssets.DATABASE_ASSET_NAME)
            )
            throw AssertionError("Expected bundled install to reject an impossible private directory")
        } catch (error: IOException) {
            assertEquals("Could not create dictionary directory.", error.message)
        } finally {
            deleteRecursively(blockedParent)
        }
    }

    @Test
    fun atomicDictionaryReplaceFallsBackWhenAtomicMoveIsUnavailable() {
        val moveDir = packageDir("dictionary-atomic-fallback-test")
        val source = File(moveDir, "source.db")
        val target = File(moveDir, "target.db")
        writeText(source, "replacement")
        writeText(target, "old")
        var attempts = 0

        DictionaryStore.atomicReplace(
            source,
            target,
            object : DictionaryStore.FileMover {
                override fun move(
                    source: java.nio.file.Path,
                    target: java.nio.file.Path,
                    vararg options: StandardCopyOption,
                ) {
                    attempts++
                    if (attempts == 1) {
                        throw AtomicMoveNotSupportedException(source.toString(), target.toString(), "test fallback")
                    }
                    assertEquals(StandardCopyOption.REPLACE_EXISTING, options[0])
                    Files.move(source, target, *options)
                }
            }
        )

        assertEquals(2, attempts)
        assertFalse(source.exists())
        assertEquals("replacement", readText(target))
    }

    @Test
    fun lookupEmptyInputAndMalformedActiveDatabaseFailClosed() {
        val store = DictionaryStore.open(context)

        assertNull(store.lookupKanji(null))
        assertNull(store.lookupKanji("   "))

        writeText(File(privateDictionaryDir, DictionaryAssets.DATABASE_ASSET_NAME), "not sqlite")

        assertNull(store.lookupKanji("日"))
        assertEquals(0, store.kanjiCount())
        assertEquals(0, store.jitenRanks().size())
    }

    @Test
    fun dictionaryPrivateFileHelpersReportFailureModes() {
        val checksum = File(context.cacheDir, "dictionary-helper-checksum.sha256")
        writeText(checksum, "ABCDEFabcdefABCDEFabcdefABCDEFabcdefABCDEFabcdefABCDEFabcdefABCD  kanji_dictionary.db\n")
        assertEquals(
            "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd",
            invokeString("readExpectedHash", arrayOf(File::class.java), checksum)
        )

        val markerDirectory = File(context.cacheDir, "dictionary-helper-marker")
        deleteRecursively(markerDirectory)
        assertTrue(markerDirectory.mkdirs())
        assertEquals("", invokeString("readMarker", arrayOf(File::class.java), markerDirectory))

        val copyError = invokeCopyWithImpossibleParent()
        assertTrue(copyError.message.orEmpty().contains("Could not create"))

        val validation = invokeStatic(
            "validateDictionary",
            arrayOf(File::class.java, String::class.java, String::class.java),
            markerDirectory,
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "{}"
        )
        assertFalse(validationOk(validation))
        assertTrue(validationMessage(validation).startsWith("Dictionary validation failed:"))

        val noAssetsManifest = invokeStatic(
            "validateManifest",
            arrayOf(String::class.java, String::class.java),
            "{\"sources\":[]}",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        )
        assertFalse(validationOk(noAssetsManifest))
        assertEquals(
            "Dictionary manifest does not match the database checksum.",
            validationMessage(noAssetsManifest)
        )

        val noSourcesManifest = invokeStatic(
            "validateManifest",
            arrayOf(String::class.java, String::class.java),
            "{\"assets\":[{\"path\":\"kanji_dictionary.db\",\"sha256\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"}]}",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        )
        assertFalse(validationOk(noSourcesManifest))
        assertEquals("Dictionary manifest is missing KANJIDIC2 metadata.", validationMessage(noSourcesManifest))

        assertEquals("IOException", invokeString("readableMessage", arrayOf(Throwable::class.java), IOException()))
    }

    @Test
    fun lookupsFailClosedWhenActiveDatabaseCannotBeOpened() {
        val store = DictionaryStore.open(context)
        assertNotNull(store.lookupKanji("日"))
        writeText(File(privateDictionaryDir, DictionaryAssets.DATABASE_ASSET_NAME), "not sqlite")

        assertNull(store.lookupKanji("日"))
        assertEquals(0, store.kanjiCount())
        assertEquals(0, store.jitenRanks().size())
    }

    @Test
    fun validFutureDictionaryPackageUpdatesActiveManifest() {
        val store = DictionaryStore.open(context)
        val packageDir = File(context.cacheDir, "dictionary-package-valid-test")
        deleteRecursively(packageDir)
        assertTrue(packageDir.mkdirs())
        val database = File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME)
        val manifest = File(packageDir, "dictionary_sources.json")
        val checksum = File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME)
        copyAsset(DictionaryAssets.DATABASE_ASSET, database)
        copyAsset(DictionaryAssets.DATABASE_SHA256_ASSET, checksum)
        val updatedManifest = assetText(DictionaryAssets.SOURCES_ASSET)
            .replace("\"generated_at\": \"2026-05-09\"", "\"generated_at\": \"2099-01-01\"")
        writeText(manifest, updatedManifest)

        val result = store.installVerifiedDictionary(database, manifest, checksum)

        assertTrue(result.ok)
        assertTrue(DictionaryStore.activeManifestText(context).contains("\"generated_at\": \"2099-01-01\""))
        deleteRecursively(packageDir)
    }

    @Test
    fun privateHashReportsMissingPlatformSha256AsIoFailure() {
        val payload = File(context.cacheDir, "dictionary-hash-provider-test.db")
        writeText(payload, "payload")
        val providers = Security.getProviders()
        assertTrue(providers.isNotEmpty())

        try {
            providers.forEach { provider -> Security.removeProvider(provider.name) }
            if (Security.getProviders().isNotEmpty()) {
                return
            }
            invokeStatic("sha256", arrayOf(File::class.java), payload)
            throw AssertionError("Expected dictionary hash to fail without SHA-256 providers")
        } catch (error: IOException) {
            assertTrue(error.cause is NoSuchAlgorithmException)
        } finally {
            restoreProviders(providers)
            payload.delete()
        }
    }

    private fun note(id: Long, expression: String): RecordsSyncModels.Note {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val fields = linkedMapOf(
            settings.expressionField to expression,
            settings.readingField to "ひと",
            settings.meaningField to "person",
            settings.sentenceField to "${expression}を見た。",
            settings.frequencyField to "1",
            settings.frequencySortField to "1"
        )
        return RecordsSyncModels.Note(id, settings.modelName, fields, listOf("tag"))
    }

    private fun packageDir(name: String): File {
        val dir = File(context.cacheDir, name)
        deleteRecursively(dir)
        assertTrue(dir.mkdirs())
        return dir
    }

    private fun assertSyntheticPackageRejected(
        store: DictionaryStore,
        packageName: String,
        includeJitenTable: Boolean,
        includeMetaTable: Boolean,
        includeRequiredMeta: Boolean,
        schemaVersion: String,
        includeJitenRankColumn: Boolean,
        expectedMessage: String,
    ) {
        val packageDir = packageDir(packageName)
        try {
            val database = File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME)
            val manifest = File(packageDir, "dictionary_sources.json")
            val checksum = File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME)
            writeSyntheticDictionaryDatabase(
                database,
                includeJitenTable,
                includeMetaTable,
                includeRequiredMeta,
                schemaVersion,
                includeJitenRankColumn
            )
            val hash = sha256(database)
            writeText(checksum, "$hash  kanji_dictionary.db\n")
            writeText(
                manifest,
                assetText(DictionaryAssets.SOURCES_ASSET)
                    .replace(firstSha256(assetText(DictionaryAssets.DATABASE_SHA256_ASSET)), hash)
            )

            val result = store.installVerifiedDictionary(database, manifest, checksum)

            assertFalse(result.ok)
            assertEquals(expectedMessage, result.message)
        } finally {
            deleteRecursively(packageDir)
        }
    }

    private fun writeSyntheticDictionaryDatabase(
        database: File,
        includeJitenTable: Boolean,
        includeMetaTable: Boolean,
        includeRequiredMeta: Boolean,
        schemaVersion: String,
        includeJitenRankColumn: Boolean,
    ) {
        val db = SQLiteDatabase.openOrCreateDatabase(database, null)
        try {
            db.execSQL(
                "CREATE TABLE kanji (literal TEXT PRIMARY KEY, meanings TEXT NOT NULL, on_readings TEXT NOT NULL, kun_readings TEXT NOT NULL, nanori_readings TEXT NOT NULL, stroke_count INTEGER NOT NULL, grade INTEGER NOT NULL, radical INTEGER NOT NULL, kanjidic_frequency INTEGER" +
                    if (includeJitenRankColumn) ", jiten_rank INTEGER" else "" +
                    ")"
            )
            if (includeJitenTable) {
                db.execSQL("CREATE TABLE jiten_ranks (literal TEXT PRIMARY KEY, rank INTEGER NOT NULL)")
            }
            if (includeMetaTable) {
                db.execSQL("CREATE TABLE dictionary_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
                if (includeRequiredMeta) {
                    db.execSQL(
                        "INSERT INTO dictionary_meta (key, value) VALUES ('schema_version', ?)",
                        arrayOf<Any>(schemaVersion)
                    )
                }
                db.execSQL("INSERT INTO dictionary_meta (key, value) VALUES ('generated_at', 'test')")
                db.execSQL("INSERT INTO dictionary_meta (key, value) VALUES ('kanjidic2_source_sha256', 'abc')")
                db.execSQL("INSERT INTO dictionary_meta (key, value) VALUES ('kanjidic2_database_version', 'test')")
                db.execSQL("INSERT INTO dictionary_meta (key, value) VALUES ('jiten_rank_source_sha256', 'abc')")
            }
        } finally {
            db.close()
        }
    }

    private fun invokeString(name: String, parameterTypes: Array<Class<*>>, vararg args: Any?): String {
        return invokeStatic(name, parameterTypes, *args) as String
    }

    private fun invokeStatic(name: String, parameterTypes: Array<Class<*>>, vararg args: Any?): Any? {
        val method = DictionaryStore::class.java.getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        return try {
            method.invoke(null, *args)
        } catch (error: InvocationTargetException) {
            val cause = error.cause
            if (cause is Exception) {
                throw cause
            }
            throw error
        }
    }

    private fun invokeCopyWithImpossibleParent(): IOException {
        val parentBlocker = File(context.cacheDir, "dictionary-copy-parent-blocker")
        deleteRecursively(parentBlocker)
        writeText(parentBlocker, "not a directory")
        val copy = DictionaryStore::class.java.getDeclaredMethod("copy", InputStream::class.java, File::class.java)
        copy.isAccessible = true
        try {
            copy.invoke(
                null,
                java.io.ByteArrayInputStream("payload".toByteArray(StandardCharsets.UTF_8)),
                File(File(parentBlocker, "child"), "target.db")
            )
            throw AssertionError("Expected copy to reject an impossible parent path")
        } catch (error: InvocationTargetException) {
            assertTrue(error.cause is IOException)
            return error.cause as IOException
        }
    }

    private fun validationOk(validation: Any?): Boolean {
        val target = requireNotNull(validation)
        val field = target.javaClass.getDeclaredField("ok")
        field.isAccessible = true
        return field.getBoolean(target)
    }

    private fun validationMessage(validation: Any?): String {
        val target = requireNotNull(validation)
        val field = target.javaClass.getDeclaredField("message")
        field.isAccessible = true
        return field.get(target) as String
    }

    private fun assertActiveEntryStillMatches(
        store: DictionaryStore,
        before: DictionaryLookup.KanjiEntry?,
    ) {
        val after = store.lookupKanji("日")
        assertNotNull(before)
        assertNotNull(after)
        assertEquals(before!!.meanings, after!!.meanings)
        assertEquals(before.jitenRank, after.jitenRank)
    }

    private fun copyAsset(asset: String, target: File) {
        context.assets.open(asset).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun assetText(asset: String): String {
        return context.assets.open(asset).use { input -> String(input.readBytes(), StandardCharsets.UTF_8) }
    }

    private fun readText(file: File): String {
        return file.readText(StandardCharsets.UTF_8)
    }

    private fun writeText(file: File, text: String) {
        file.writeText(text, StandardCharsets.UTF_8)
    }

    private fun firstSha256(text: String?): String {
        return text?.trim().orEmpty()
            .split(Regex("\\s+"))
            .firstOrNull()
            .orEmpty()
            .lowercase(Locale.ROOT)
    }

    private fun sha256(file: File): String {
        return sha256(file.readBytes())
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = digest.digest(bytes)
        return hashed.joinToString(separator = "") { byte -> String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff) }
    }

    private fun restoreProviders(providers: Array<Provider>) {
        providers.forEachIndexed { index, provider ->
            if (Security.getProvider(provider.name) == null) {
                Security.insertProviderAt(provider, index + 1)
            }
        }
    }

    private fun deleteRecursively(file: File?) {
        if (file == null || !file.exists()) {
            return
        }
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> deleteRecursively(child) }
        }
        file.delete()
    }
}
