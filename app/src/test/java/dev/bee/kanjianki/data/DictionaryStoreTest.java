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
    public void bundledDictionaryInstallsAndLooksUpCoreKanjiData() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DictionaryStore store = DictionaryStore.open(context);

        assertTrue(store.kanjiCount() > 1000);

        DictionaryLookup.KanjiEntry entry = store.lookupKanji("日");
        assertNotNull(entry);
        assertEquals("日", entry.literal);
        assertTrue(entry.meanings.contains("day"));
        assertTrue(entry.onReadings.contains("ニチ"));
        assertTrue(entry.kunReadings.contains("ひ"));
        assertTrue(entry.strokeCount > 0);
    }

    @Test
    public void bundledDictionaryExposesRanksAndManifest() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DictionaryStore store = DictionaryStore.open(context);

        JitenKanjiRanks ranks = store.jitenRanks();
        assertTrue(ranks.size() > 1000);
        assertTrue(ranks.rankOf("日") > 0);
        assertTrue(DictionaryStore.activeManifestText(context).contains("\"schema_version\""));
    }

    @Test
    public void lookupRejectsBlankAndUnknownKanji() throws Exception {
        DictionaryStore store = DictionaryStore.open(ApplicationProvider.getApplicationContext());

        assertNull(store.lookupKanji(null));
        assertNull(store.lookupKanji(" "));
        assertNull(store.lookupKanji("not-kanji"));
    }
}
