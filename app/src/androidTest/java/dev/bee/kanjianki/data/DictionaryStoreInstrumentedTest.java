package dev.bee.kanjianki.data;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.StudyCue;
import dev.bee.kanjianki.core.SuspendedKanjiImporter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class DictionaryStoreInstrumentedTest {
    private Context context;
    private File privateDictionaryDir;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        privateDictionaryDir = new File(context.getFilesDir(), "dictionaries");
        deleteRecursively(privateDictionaryDir);
    }

    @After
    public void tearDown() {
        deleteRecursively(privateDictionaryDir);
    }

    @Test
    public void lookupReadsOneKanjiFromInstalledSQLiteDatabase() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);

        DictionaryLookup.KanjiEntry entry = store.lookupKanji("日");

        assertEquals(13108, store.kanjiCount());
        assertNotNull(entry);
        assertEquals("日", entry.literal);
        assertFalse(entry.meanings.isEmpty());
        assertNotNull(entry.jitenRank);
        assertEquals(10666, store.jitenRanks().size());
        assertEquals(Integer.valueOf(1), store.jitenRanks().rankOf("人"));
    }

    @Test
    public void studyCueUsesKanjidicMeaningAndAnkiReadingAndFromWord() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);

        StudyCue cue = store.studyCue("日", "fallback from note", "ニチ", "日本", "にほん");

        assertFalse(cue.meaning.contains("fallback"));
        assertEquals("にほん", cue.reading);
        assertEquals("日本", cue.fromExpression);
        assertEquals(DictionaryLookup.SOURCE_KANJIDIC2, cue.meaningSource);
    }

    @Test
    public void jitenRankFilteringUsesDictionaryStoreRanks() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Collections.singletonList(note(1L, "人")),
                Collections.singletonList(new RecordsSyncModels.Card(10L, 1L, 0, "Mining", -1, 0, 0, 0, 0, 0, true))
        );

        List<RecordsImportModels.SuspendedImport> imports = new SuspendedKanjiImporter(store.jitenRanks(), 1, 1)
                .importFrom(snapshot, settings);

        assertEquals(1, imports.size());
        assertEquals("人", imports.get(0).kanji);
        assertEquals(Integer.valueOf(1), imports.get(0).jitenRank);
    }

    @Test
    public void invalidFutureDictionaryPackageIsRejectedWithoutReplacingActiveDatabase() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);
        DictionaryLookup.KanjiEntry before = store.lookupKanji("日");
        File packageDir = new File(context.getCacheDir(), "dictionary-package-test");
        deleteRecursively(packageDir);
        assertTrue(packageDir.mkdirs());
        File database = new File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME);
        File manifest = new File(packageDir, "dictionary_sources.json");
        File checksum = new File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME);
        copyAsset(DictionaryAssets.DATABASE_ASSET, database);
        copyAsset(DictionaryAssets.SOURCES_ASSET, manifest);
        writeText(checksum, "0000000000000000000000000000000000000000000000000000000000000000  kanji_dictionary.db\n");

        DictionaryStore.InstallResult result = store.installVerifiedDictionary(database, manifest, checksum);
        DictionaryLookup.KanjiEntry after = store.lookupKanji("日");

        assertFalse(result.ok);
        assertNotNull(before);
        assertNotNull(after);
        assertEquals(before.meanings, after.meanings);
        assertEquals(before.jitenRank, after.jitenRank);
        deleteRecursively(packageDir);
    }

    @Test
    public void missingManifestPackageIsRejectedWithoutReplacingActiveDatabase() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);
        DictionaryLookup.KanjiEntry before = store.lookupKanji("日");
        File packageDir = packageDir("dictionary-package-missing-manifest-test");
        try {
            File database = new File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME);
            File manifest = new File(packageDir, "missing_dictionary_sources.json");
            File checksum = new File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME);
            copyAsset(DictionaryAssets.DATABASE_ASSET, database);
            copyAsset(DictionaryAssets.DATABASE_SHA256_ASSET, checksum);

            DictionaryStore.InstallResult result = store.installVerifiedDictionary(database, manifest, checksum);

            assertFalse(result.ok);
            assertTrue(result.message.contains("Dictionary install failed"));
            assertActiveEntryStillMatches(store, before);
        } finally {
            deleteRecursively(packageDir);
        }
    }

    @Test
    public void missingChecksumPackageIsRejectedWithoutReplacingActiveDatabase() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);
        DictionaryLookup.KanjiEntry before = store.lookupKanji("日");
        File packageDir = packageDir("dictionary-package-missing-checksum-test");
        try {
            File database = new File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME);
            File manifest = new File(packageDir, "dictionary_sources.json");
            File checksum = new File(packageDir, "missing_kanji_dictionary.db.sha256");
            copyAsset(DictionaryAssets.DATABASE_ASSET, database);
            copyAsset(DictionaryAssets.SOURCES_ASSET, manifest);

            DictionaryStore.InstallResult result = store.installVerifiedDictionary(database, manifest, checksum);

            assertFalse(result.ok);
            assertTrue(result.message.contains("Dictionary install failed"));
            assertActiveEntryStillMatches(store, before);
        } finally {
            deleteRecursively(packageDir);
        }
    }

    @Test
    public void manifestWithoutDatabaseAssetIsRejectedWithoutReplacingActiveDatabase() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);
        DictionaryLookup.KanjiEntry before = store.lookupKanji("日");
        File packageDir = packageDir("dictionary-package-missing-asset-test");
        try {
            File database = new File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME);
            File manifest = new File(packageDir, "dictionary_sources.json");
            File checksum = new File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME);
            copyAsset(DictionaryAssets.DATABASE_ASSET, database);
            copyAsset(DictionaryAssets.DATABASE_SHA256_ASSET, checksum);
            writeText(manifest, assetText(DictionaryAssets.SOURCES_ASSET)
                    .replace("\"path\": \"kanji_dictionary.db\"", "\"path\": \"other_dictionary.db\""));

            DictionaryStore.InstallResult result = store.installVerifiedDictionary(database, manifest, checksum);

            assertFalse(result.ok);
            assertEquals("Dictionary manifest does not match the database checksum.", result.message);
            assertActiveEntryStillMatches(store, before);
        } finally {
            deleteRecursively(packageDir);
        }
    }

    @Test
    public void manifestWithoutKanjidicSourceIsRejectedWithoutReplacingActiveDatabase() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);
        DictionaryLookup.KanjiEntry before = store.lookupKanji("日");
        File packageDir = packageDir("dictionary-package-missing-source-test");
        try {
            File database = new File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME);
            File manifest = new File(packageDir, "dictionary_sources.json");
            File checksum = new File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME);
            copyAsset(DictionaryAssets.DATABASE_ASSET, database);
            copyAsset(DictionaryAssets.DATABASE_SHA256_ASSET, checksum);
            writeText(manifest, assetText(DictionaryAssets.SOURCES_ASSET)
                    .replace("\"id\": \"kanjidic2\"", "\"id\": \"not_kanjidic2\""));

            DictionaryStore.InstallResult result = store.installVerifiedDictionary(database, manifest, checksum);

            assertFalse(result.ok);
            assertEquals("Dictionary manifest is missing KANJIDIC2 metadata.", result.message);
            assertActiveEntryStillMatches(store, before);
        } finally {
            deleteRecursively(packageDir);
        }
    }

    @Test
    public void sqlitePackageMissingKanjiTableIsRejectedWithoutReplacingActiveDatabase() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);
        DictionaryLookup.KanjiEntry before = store.lookupKanji("日");
        File packageDir = packageDir("dictionary-package-missing-table-test");
        try {
            File database = new File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME);
            File manifest = new File(packageDir, "dictionary_sources.json");
            File checksum = new File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME);
            SQLiteDatabase.openOrCreateDatabase(database, null).close();
            String hash = sha256(database);
            writeText(checksum, hash + "  kanji_dictionary.db\n");
            writeText(manifest, assetText(DictionaryAssets.SOURCES_ASSET)
                    .replace(firstSha256(assetText(DictionaryAssets.DATABASE_SHA256_ASSET)), hash));

            DictionaryStore.InstallResult result = store.installVerifiedDictionary(database, manifest, checksum);

            assertFalse(result.ok);
            assertEquals("Dictionary is missing kanji table.", result.message);
            assertActiveEntryStillMatches(store, before);
        } finally {
            deleteRecursively(packageDir);
        }
    }

    @Test
    public void sqlitePackagesMissingRequiredTablesOrMetaAreRejected() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);
        DictionaryLookup.KanjiEntry before = store.lookupKanji("日");

        assertSyntheticPackageRejected(
                store,
                "dictionary-package-missing-jiten-table-test",
                false,
                true,
                true,
                "1",
                true,
                "Dictionary is missing jiten_ranks table."
        );
        assertSyntheticPackageRejected(
                store,
                "dictionary-package-missing-meta-table-test",
                true,
                false,
                true,
                "1",
                true,
                "Dictionary is missing dictionary_meta table."
        );
        assertSyntheticPackageRejected(
                store,
                "dictionary-package-missing-meta-key-test",
                true,
                true,
                false,
                "1",
                true,
                "Dictionary metadata is missing schema_version."
        );
        assertSyntheticPackageRejected(
                store,
                "dictionary-package-unsupported-schema-test",
                true,
                true,
                true,
                "99",
                true,
                "Dictionary schema version is unsupported."
        );
        assertSyntheticPackageRejected(
                store,
                "dictionary-package-missing-kanji-column-test",
                true,
                true,
                true,
                "1",
                false,
                "Dictionary kanji table is missing jiten_rank."
        );
        assertActiveEntryStillMatches(store, before);
    }

    @Test
    public void malformedManifestAndChecksumPackagesAreRejectedWithSpecificMessages() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);
        DictionaryLookup.KanjiEntry before = store.lookupKanji("日");
        File badManifestPackage = packageDir("dictionary-package-bad-manifest-test");
        try {
            File database = new File(badManifestPackage, DictionaryAssets.DATABASE_ASSET_NAME);
            File manifest = new File(badManifestPackage, "dictionary_sources.json");
            File checksum = new File(badManifestPackage, DictionaryAssets.DATABASE_SHA256_ASSET_NAME);
            copyAsset(DictionaryAssets.DATABASE_ASSET, database);
            copyAsset(DictionaryAssets.DATABASE_SHA256_ASSET, checksum);
            writeText(manifest, "{not-json");

            DictionaryStore.InstallResult result = store.installVerifiedDictionary(database, manifest, checksum);

            assertFalse(result.ok);
            assertEquals("Dictionary manifest is invalid JSON.", result.message);
        } finally {
            deleteRecursively(badManifestPackage);
        }

        File badChecksumPackage = packageDir("dictionary-package-bad-checksum-test");
        try {
            File database = new File(badChecksumPackage, DictionaryAssets.DATABASE_ASSET_NAME);
            File manifest = new File(badChecksumPackage, "dictionary_sources.json");
            File checksum = new File(badChecksumPackage, DictionaryAssets.DATABASE_SHA256_ASSET_NAME);
            copyAsset(DictionaryAssets.DATABASE_ASSET, database);
            copyAsset(DictionaryAssets.SOURCES_ASSET, manifest);
            writeText(checksum, "not-a-sha  kanji_dictionary.db\n");

            DictionaryStore.InstallResult result = store.installVerifiedDictionary(database, manifest, checksum);

            assertFalse(result.ok);
            assertEquals("Dictionary checksum is missing or invalid.", result.message);
            assertActiveEntryStillMatches(store, before);
        } finally {
            deleteRecursively(badChecksumPackage);
        }
    }

    @Test
    public void invalidSqlitePackageIsRejectedAfterChecksumAndManifestMatch() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);
        DictionaryLookup.KanjiEntry before = store.lookupKanji("日");
        File packageDir = packageDir("dictionary-package-invalid-sqlite-test");
        try {
            File database = new File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME);
            File manifest = new File(packageDir, "dictionary_sources.json");
            File checksum = new File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME);
            writeText(database, "not sqlite");
            String hash = sha256(database);
            writeText(checksum, hash + "  kanji_dictionary.db\n");
            writeText(manifest, assetText(DictionaryAssets.SOURCES_ASSET)
                    .replace(firstSha256(assetText(DictionaryAssets.DATABASE_SHA256_ASSET)), hash));

            DictionaryStore.InstallResult result = store.installVerifiedDictionary(database, manifest, checksum);

            assertFalse(result.ok);
            assertEquals("Dictionary database is invalid.", result.message);
            assertActiveEntryStillMatches(store, before);
        } finally {
            deleteRecursively(packageDir);
        }
    }

    @Test
    public void sqlitePackageWithNoKanjiRowsIsRejectedAfterSchemaValidation() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);
        DictionaryLookup.KanjiEntry before = store.lookupKanji("日");

        assertSyntheticPackageRejected(
                store,
                "dictionary-package-empty-kanji-test",
                true,
                true,
                true,
                "1",
                true,
                "Dictionary has no kanji rows."
        );
        assertActiveEntryStillMatches(store, before);
    }

    @Test
    public void bundledDictionaryInstallRejectsInvalidDatabaseAsset() throws Exception {
        File installDir = packageDir("dictionary-invalid-bundled-test");
        File database = new File(installDir, DictionaryAssets.DATABASE_ASSET_NAME);
        byte[] invalidDatabase = "not sqlite".getBytes(StandardCharsets.UTF_8);
        String hash = sha256(invalidDatabase);
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put(DictionaryAssets.DATABASE_ASSET, invalidDatabase);
        assets.put(DictionaryAssets.DATABASE_SHA256_ASSET, (hash + "  kanji_dictionary.db\n").getBytes(StandardCharsets.UTF_8));
        assets.put(DictionaryAssets.SOURCES_ASSET, assetText(DictionaryAssets.SOURCES_ASSET)
                .replace(firstSha256(assetText(DictionaryAssets.DATABASE_SHA256_ASSET)), hash)
                .getBytes(StandardCharsets.UTF_8));

        try {
            DictionaryStore.ensureBundledDictionaryInstalled(asset -> {
                byte[] value = assets.get(asset);
                if (value == null) {
                    throw new IOException("missing fake asset " + asset);
                }
                return new java.io.ByteArrayInputStream(value);
            }, installDir, database);
            throw new AssertionError("Expected bundled install to reject the invalid SQLite asset");
        } catch (IOException error) {
            assertEquals("Dictionary database is invalid.", error.getMessage());
            assertFalse(database.exists());
            assertFalse(new File(installDir, "dictionary_sources.json").exists());
        } finally {
            deleteRecursively(installDir);
        }
    }

    @Test
    public void bundledDictionaryInstallFailsWhenPrivatePathCannotBeCreated() throws Exception {
        File blockedParent = new File(context.getCacheDir(), "dictionary-blocked-directory-test");
        deleteRecursively(blockedParent);
        writeText(blockedParent, "not a directory");
        File blockedDirectory = new File(blockedParent, "child");

        try {
            DictionaryStore.ensureBundledDictionaryInstalled(asset -> {
                throw new IOException("assets should not be opened");
            }, blockedDirectory, new File(blockedDirectory, DictionaryAssets.DATABASE_ASSET_NAME));
            throw new AssertionError("Expected bundled install to reject an impossible private directory");
        } catch (IOException error) {
            assertEquals("Could not create dictionary directory.", error.getMessage());
        } finally {
            deleteRecursively(blockedParent);
        }
    }

    @Test
    public void atomicDictionaryReplaceFallsBackWhenAtomicMoveIsUnavailable() throws Exception {
        File moveDir = packageDir("dictionary-atomic-fallback-test");
        File source = new File(moveDir, "source.db");
        File target = new File(moveDir, "target.db");
        writeText(source, "replacement");
        writeText(target, "old");
        final int[] attempts = {0};

        DictionaryStore.atomicReplace(source, target, (sourcePath, targetPath, options) -> {
            attempts[0]++;
            if (attempts[0] == 1) {
                throw new AtomicMoveNotSupportedException(sourcePath.toString(), targetPath.toString(), "test fallback");
            }
            assertEquals(StandardCopyOption.REPLACE_EXISTING, options[0]);
            java.nio.file.Files.move(sourcePath, targetPath, options);
        });

        assertEquals(2, attempts[0]);
        assertFalse(source.exists());
        assertEquals("replacement", readText(target));
    }

    @Test
    public void lookupEmptyInputAndMalformedActiveDatabaseFailClosed() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);

        assertNull(store.lookupKanji(null));
        assertNull(store.lookupKanji("   "));

        writeText(new File(privateDictionaryDir, DictionaryAssets.DATABASE_ASSET_NAME), "not sqlite");

        assertNull(store.lookupKanji("日"));
        assertEquals(0, store.kanjiCount());
        assertEquals(0, store.jitenRanks().size());
    }

    @Test
    public void dictionaryPrivateFileHelpersReportFailureModes() throws Exception {
        File checksum = new File(context.getCacheDir(), "dictionary-helper-checksum.sha256");
        writeText(checksum, "ABCDEFabcdefABCDEFabcdefABCDEFabcdefABCDEFabcdefABCDEFabcdefABCD  kanji_dictionary.db\n");
        assertEquals(
                "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd",
                invokeString("readExpectedHash", new Class<?>[]{File.class}, checksum)
        );

        File markerDirectory = new File(context.getCacheDir(), "dictionary-helper-marker");
        deleteRecursively(markerDirectory);
        assertTrue(markerDirectory.mkdirs());
        assertEquals("", invokeString("readMarker", new Class<?>[]{File.class}, markerDirectory));

        IOException copyError = invokeCopyWithImpossibleParent();
        assertTrue(copyError.getMessage().contains("Could not create"));

        Object validation = invokeStatic(
                "validateDictionary",
                new Class<?>[]{File.class, String.class, String.class},
                markerDirectory,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "{}"
        );
        assertFalse(validationOk(validation));
        assertTrue(validationMessage(validation).startsWith("Dictionary validation failed:"));

        Object noAssetsManifest = invokeStatic(
                "validateManifest",
                new Class<?>[]{String.class, String.class},
                "{\"sources\":[]}",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );
        assertFalse(validationOk(noAssetsManifest));
        assertEquals("Dictionary manifest does not match the database checksum.", validationMessage(noAssetsManifest));

        Object noSourcesManifest = invokeStatic(
                "validateManifest",
                new Class<?>[]{String.class, String.class},
                "{\"assets\":[{\"path\":\"kanji_dictionary.db\",\"sha256\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\"}]}",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );
        assertFalse(validationOk(noSourcesManifest));
        assertEquals("Dictionary manifest is missing KANJIDIC2 metadata.", validationMessage(noSourcesManifest));

        assertEquals("IOException", invokeString("readableMessage", new Class<?>[]{Throwable.class}, new IOException()));
    }

    @Test
    public void lookupsFailClosedWhenActiveDatabaseCannotBeOpened() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);
        assertNotNull(store.lookupKanji("日"));
        writeText(new File(privateDictionaryDir, DictionaryAssets.DATABASE_ASSET_NAME), "not sqlite");

        assertNull(store.lookupKanji("日"));
        assertEquals(0, store.kanjiCount());
        assertEquals(0, store.jitenRanks().size());
    }

    @Test
    public void validFutureDictionaryPackageUpdatesActiveManifest() throws Exception {
        DictionaryStore store = DictionaryStore.open(context);
        File packageDir = new File(context.getCacheDir(), "dictionary-package-valid-test");
        deleteRecursively(packageDir);
        assertTrue(packageDir.mkdirs());
        File database = new File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME);
        File manifest = new File(packageDir, "dictionary_sources.json");
        File checksum = new File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME);
        copyAsset(DictionaryAssets.DATABASE_ASSET, database);
        copyAsset(DictionaryAssets.DATABASE_SHA256_ASSET, checksum);
        String updatedManifest = assetText(DictionaryAssets.SOURCES_ASSET)
                .replace("\"generated_at\": \"2026-05-09\"", "\"generated_at\": \"2099-01-01\"");
        writeText(manifest, updatedManifest);

        DictionaryStore.InstallResult result = store.installVerifiedDictionary(database, manifest, checksum);

        assertTrue(result.ok);
        assertTrue(DictionaryStore.activeManifestText(context).contains("\"generated_at\": \"2099-01-01\""));
        deleteRecursively(packageDir);
    }

    @Test
    public void privateHashReportsMissingPlatformSha256AsIoFailure() throws Exception {
        File payload = new File(context.getCacheDir(), "dictionary-hash-provider-test.db");
        writeText(payload, "payload");
        Provider[] providers = Security.getProviders();
        assertTrue(providers.length > 0);

        try {
            for (Provider provider : providers) {
                Security.removeProvider(provider.getName());
            }
            if (Security.getProviders().length != 0) {
                return;
            }
            invokeStatic("sha256", new Class<?>[]{File.class}, payload);
            throw new AssertionError("Expected dictionary hash to fail without SHA-256 providers");
        } catch (IOException error) {
            assertTrue(error.getCause() instanceof NoSuchAlgorithmException);
        } finally {
            restoreProviders(providers);
            payload.delete();
        }
    }

    private RecordsSyncModels.Note note(long id, String expression) {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(settings.expressionField, expression);
        fields.put(settings.readingField, "ひと");
        fields.put(settings.meaningField, "person");
        fields.put(settings.sentenceField, expression + "を見た。");
        fields.put(settings.frequencyField, "1");
        fields.put(settings.frequencySortField, "1");
        return new RecordsSyncModels.Note(id, settings.modelName, fields, Arrays.asList("tag"));
    }

    private File packageDir(String name) {
        File packageDir = new File(context.getCacheDir(), name);
        deleteRecursively(packageDir);
        assertTrue(packageDir.mkdirs());
        return packageDir;
    }

    private void assertSyntheticPackageRejected(
            DictionaryStore store,
            String packageName,
            boolean includeJitenTable,
            boolean includeMetaTable,
            boolean includeRequiredMeta,
            String schemaVersion,
            boolean includeJitenRankColumn,
            String expectedMessage
    ) throws Exception {
        File packageDir = packageDir(packageName);
        try {
            File database = new File(packageDir, DictionaryAssets.DATABASE_ASSET_NAME);
            File manifest = new File(packageDir, "dictionary_sources.json");
            File checksum = new File(packageDir, DictionaryAssets.DATABASE_SHA256_ASSET_NAME);
            writeSyntheticDictionaryDatabase(
                    database,
                    includeJitenTable,
                    includeMetaTable,
                    includeRequiredMeta,
                    schemaVersion,
                    includeJitenRankColumn
            );
            String hash = sha256(database);
            writeText(checksum, hash + "  kanji_dictionary.db\n");
            writeText(manifest, assetText(DictionaryAssets.SOURCES_ASSET)
                    .replace(firstSha256(assetText(DictionaryAssets.DATABASE_SHA256_ASSET)), hash));

            DictionaryStore.InstallResult result = store.installVerifiedDictionary(database, manifest, checksum);

            assertFalse(result.ok);
            assertEquals(expectedMessage, result.message);
        } finally {
            deleteRecursively(packageDir);
        }
    }

    private static void writeSyntheticDictionaryDatabase(
            File database,
            boolean includeJitenTable,
            boolean includeMetaTable,
            boolean includeRequiredMeta,
            String schemaVersion,
            boolean includeJitenRankColumn
    ) {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(database, null);
        try {
            db.execSQL("CREATE TABLE kanji (literal TEXT PRIMARY KEY, meanings TEXT NOT NULL, on_readings TEXT NOT NULL, kun_readings TEXT NOT NULL, nanori_readings TEXT NOT NULL, stroke_count INTEGER NOT NULL, grade INTEGER NOT NULL, radical INTEGER NOT NULL, kanjidic_frequency INTEGER"
                    + (includeJitenRankColumn ? ", jiten_rank INTEGER" : "")
                    + ")");
            if (includeJitenTable) {
                db.execSQL("CREATE TABLE jiten_ranks (literal TEXT PRIMARY KEY, rank INTEGER NOT NULL)");
            }
            if (includeMetaTable) {
                db.execSQL("CREATE TABLE dictionary_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
                if (includeRequiredMeta) {
                    db.execSQL("INSERT INTO dictionary_meta (key, value) VALUES ('schema_version', ?)", new Object[]{schemaVersion});
                }
                db.execSQL("INSERT INTO dictionary_meta (key, value) VALUES ('generated_at', 'test')");
                db.execSQL("INSERT INTO dictionary_meta (key, value) VALUES ('kanjidic2_source_sha256', 'abc')");
                db.execSQL("INSERT INTO dictionary_meta (key, value) VALUES ('kanjidic2_database_version', 'test')");
                db.execSQL("INSERT INTO dictionary_meta (key, value) VALUES ('jiten_rank_source_sha256', 'abc')");
            }
        } finally {
            db.close();
        }
    }

    private String invokeString(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        return (String) invokeStatic(name, parameterTypes, args);
    }

    private Object invokeStatic(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = DictionaryStore.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw error;
        }
    }

    private IOException invokeCopyWithImpossibleParent() throws Exception {
        File parentBlocker = new File(context.getCacheDir(), "dictionary-copy-parent-blocker");
        deleteRecursively(parentBlocker);
        writeText(parentBlocker, "not a directory");
        Method copy = DictionaryStore.class.getDeclaredMethod("copy", InputStream.class, File.class);
        copy.setAccessible(true);
        try (InputStream input = new java.io.ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8))) {
            copy.invoke(null, input, new File(new File(parentBlocker, "child"), "target.db"));
            throw new AssertionError("Expected copy to reject an impossible parent path");
        } catch (InvocationTargetException error) {
            assertTrue(error.getCause() instanceof IOException);
            return (IOException) error.getCause();
        }
    }

    private static boolean validationOk(Object validation) throws Exception {
        java.lang.reflect.Field field = validation.getClass().getDeclaredField("ok");
        field.setAccessible(true);
        return field.getBoolean(validation);
    }

    private static String validationMessage(Object validation) throws Exception {
        java.lang.reflect.Field field = validation.getClass().getDeclaredField("message");
        field.setAccessible(true);
        return (String) field.get(validation);
    }

    private static void assertActiveEntryStillMatches(
            DictionaryStore store,
            DictionaryLookup.KanjiEntry before
    ) {
        DictionaryLookup.KanjiEntry after = store.lookupKanji("日");
        assertNotNull(before);
        assertNotNull(after);
        assertEquals(before.meanings, after.meanings);
        assertEquals(before.jitenRank, after.jitenRank);
    }

    private void copyAsset(String asset, File target) throws IOException {
        try (InputStream input = context.getAssets().open(asset);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private String assetText(String asset) throws IOException {
        try (InputStream input = context.getAssets().open(asset)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String readText(File file) throws IOException {
        try (InputStream input = new java.io.FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeText(File file, String text) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String firstSha256(String text) {
        String value = text == null ? "" : text.trim();
        String[] parts = value.split("\\s+");
        return parts.length == 0 ? "" : parts[0].toLowerCase(Locale.ROOT);
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream source = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = source.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder out = new StringBuilder();
            for (byte b : digest.digest()) {
                out.append(String.format(Locale.ROOT, "%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes);
            StringBuilder out = new StringBuilder();
            for (byte b : hashed) {
                out.append(String.format(Locale.ROOT, "%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException(error);
        }
    }

    private static void restoreProviders(Provider[] providers) {
        for (int i = 0; i < providers.length; i++) {
            Provider provider = providers[i];
            if (Security.getProvider(provider.getName()) == null) {
                Security.insertProviderAt(provider, i + 1);
            }
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
