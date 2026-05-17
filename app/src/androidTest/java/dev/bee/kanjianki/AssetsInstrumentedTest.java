package dev.bee.kanjianki;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.data.DictionaryAssets;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class AssetsInstrumentedTest {
    @Test
    public void dictionaryAssetsLoadBundledKanjiDictionary() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        DictionaryLookup lookup = DictionaryAssets.load(context);

        assertTrue(lookup.kanjiCount() > 0);
        assertNotNull(lookup.lookupKanji("日"));
    }

    @Test
    public void dictionaryAssetsFallBackToEmptyLookupWhenStoreCannotOpen() {
        Context context = new ContextWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext()) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public java.io.File getFilesDir() {
                return new java.io.File("/dev/null/not-a-directory");
            }
        };

        DictionaryLookup lookup = DictionaryAssets.load(context);

        assertNotNull(lookup);
        assertEquals(0, lookup.kanjiCount());
    }

    @Test
    public void strokeGuideAssetsLoadBundledGuidesAndFailClosed() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Map<String, StrokeGuide> guides = StrokeGuideAssets.load(context);

        assertFalse(guides.isEmpty());
        assertTrue(guides.containsKey("日"));

        Map<String, StrokeGuide> missing = StrokeGuideAssets.load(new ContextWrapper(context) {
            @Override
            public Resources getResources() {
                throw new Resources.NotFoundException("no raw resources");
            }
        });
        assertTrue(missing.isEmpty());
    }
}
