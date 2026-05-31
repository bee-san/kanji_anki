package dev.bee.kanjianki

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttributionTextsInstrumentedTest {
    @Test
    fun kanjiVgUsesBundledAttributionWhenRawResourceIsPresent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rawText = AttributionTexts.rawResourceText(context, R.raw.kanjivg_attribution)

        assertTrue(rawText.contains("KanjiVG stroke data"))
        assertEquals(rawText, AttributionTexts.kanjiVg(context))
    }
}
