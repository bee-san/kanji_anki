package dev.bee.kanjianki

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.data.DictionaryAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AssetsInstrumentedTest {
    @Test
    fun dictionaryAssetsLoadBundledKanjiDictionary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val lookup = DictionaryAssets.load(context)

        assertTrue(lookup.kanjiCount() > 0)
        assertNotNull(lookup.lookupKanji("日"))
    }

    @Test
    fun dictionaryAssetsFallBackToEmptyLookupWhenStoreCannotOpen() {
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this

            override fun getFilesDir(): File {
                return File("/dev/null/not-a-directory")
            }
        }

        val lookup = DictionaryAssets.load(context)

        assertNotNull(lookup)
        assertEquals(0, lookup.kanjiCount())
    }

    @Test
    fun strokeGuideAssetsLoadBundledGuidesAndFailClosed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val guides = StrokeGuideAssets.load(context)

        assertFalse(guides.isEmpty())
        assertTrue(guides.containsKey("日"))

        val missing = StrokeGuideAssets.load(object : ContextWrapper(context) {
            override fun getResources(): Resources {
                throw Resources.NotFoundException("no raw resources")
            }
        })
        assertTrue(missing.isEmpty())
    }
}
