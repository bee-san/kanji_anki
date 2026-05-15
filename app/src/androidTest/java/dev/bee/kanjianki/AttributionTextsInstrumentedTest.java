package dev.bee.kanjianki;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class AttributionTextsInstrumentedTest {
    @Test
    public void kanjiVgUsesBundledAttributionWhenRawResourceIsPresent() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String rawText = AttributionTexts.rawResourceText(context, R.raw.kanjivg_attribution);

        assertTrue(rawText.contains("KanjiVG stroke data"));
        assertEquals(rawText, AttributionTexts.kanjiVg(context));
    }
}
