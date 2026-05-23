package dev.bee.kanjianki.data;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.JitenKanjiRanks;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class DictionaryStoreTest {
    @Test
    public void bundledDictionaryInstallsKanjiData() throws Exception {
        DictionaryStore store = openStore();

        assertTrue(store.kanjiCount() > 1000);
    }

    @Test
    public void lookupReturnsRequestedLiteral() throws Exception {
        DictionaryLookup.KanjiEntry entry = lookupCoreKanji();

        assertEquals("日", entry.literal);
    }

    @Test
    public void lookupReturnsMeanings() throws Exception {
        DictionaryLookup.KanjiEntry entry = lookupCoreKanji();

        assertTrue(entry.meanings.contains("day"));
    }

    @Test
    public void lookupReturnsOnReadings() throws Exception {
        DictionaryLookup.KanjiEntry entry = lookupCoreKanji();

        assertTrue(entry.onReadings.contains("ニチ"));
    }

    @Test
    public void lookupReturnsKunReadings() throws Exception {
        DictionaryLookup.KanjiEntry entry = lookupCoreKanji();

        assertTrue(entry.kunReadings.contains("ひ"));
    }

    @Test
    public void lookupReturnsStrokeCount() throws Exception {
        DictionaryLookup.KanjiEntry entry = lookupCoreKanji();

        assertTrue(entry.strokeCount > 0);
    }

    @Test
    public void bundledDictionaryExposesRanks() throws Exception {
        DictionaryStore store = openStore();

        JitenKanjiRanks ranks = store.jitenRanks();
        assertTrue(ranks.size() > 1000);
    }

    @Test
    public void bundledDictionaryRanksCoreKanji() throws Exception {
        JitenKanjiRanks ranks = openStore().jitenRanks();

        assertTrue(ranks.rankOf("日") > 0);
    }

    @Test
    public void bundledDictionaryExposesManifest() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();

        assertTrue(DictionaryStore.activeManifestText(context).contains("\"schema_version\""));
    }

    @Test
    public void lookupRejectsNullKanji() throws Exception {
        DictionaryStore store = DictionaryStore.open(ApplicationProvider.getApplicationContext());

        assertNull(store.lookupKanji(null));
    }

    @Test
    public void lookupRejectsBlankKanji() throws Exception {
        DictionaryStore store = DictionaryStore.open(ApplicationProvider.getApplicationContext());

        assertNull(store.lookupKanji(" "));
    }

    @Test
    public void lookupRejectsUnknownKanji() throws Exception {
        DictionaryStore store = DictionaryStore.open(ApplicationProvider.getApplicationContext());

        assertNull(store.lookupKanji("not-kanji"));
    }

    private static DictionaryStore openStore() throws Exception {
        return DictionaryStore.open(ApplicationProvider.getApplicationContext());
    }

    private static DictionaryLookup.KanjiEntry lookupCoreKanji() throws Exception {
        DictionaryLookup.KanjiEntry entry = openStore().lookupKanji("日");
        assertNotNull(entry);
        return entry;
    }
}
