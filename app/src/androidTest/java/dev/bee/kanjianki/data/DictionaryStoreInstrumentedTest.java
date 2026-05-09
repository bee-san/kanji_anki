package dev.bee.kanjianki.data;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.StudyCue;
import dev.bee.kanjianki.core.SuspendedKanjiImporter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
        Records.Settings settings = Records.Settings.kikuDefaults();
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Collections.singletonList(note(1L, "人")),
                Collections.singletonList(new Records.Card(10L, 1L, 0, "Mining", -1, 0, 0, 0, 0, 0, true))
        );

        List<Records.SuspendedImport> imports = new SuspendedKanjiImporter(store.jitenRanks(), 1, 1)
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

    private Records.Note note(long id, String expression) {
        Records.Settings settings = Records.Settings.kikuDefaults();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(settings.expressionField, expression);
        fields.put(settings.readingField, "ひと");
        fields.put(settings.meaningField, "person");
        fields.put(settings.sentenceField, expression + "を見た。");
        fields.put(settings.frequencyField, "1");
        fields.put(settings.frequencySortField, "1");
        return new Records.Note(id, settings.modelName, fields, Arrays.asList("tag"));
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

    private static void writeText(File file, String text) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
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
