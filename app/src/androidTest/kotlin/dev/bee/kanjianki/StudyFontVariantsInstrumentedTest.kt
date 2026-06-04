package dev.bee.kanjianki

import android.content.res.Resources
import android.graphics.Typeface
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyFontVariantsInstrumentedTest {
    @Test
    fun fontVariantsLoadAllStudyFontsAndDefaultVariant() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertNotNull(StudyFontVariants.forVariant(context, 0))
        assertNotNull(StudyFontVariants.forVariant(context, 1))
        assertNotNull(StudyFontVariants.forVariant(context, 2))
        assertNotNull(StudyFontVariants.forVariant(context, 99))
    }

    @Test
    fun fontVariantFallsBackWhenContextCannotLoadResources() {
        val throwingContext = object : ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
            R.style.AppTheme,
        ) {
            override fun getResources(): Resources {
                throw RuntimeException("no font resources")
            }
        }

        assertSame(Typeface.DEFAULT, StudyFontVariants.forVariant(throwingContext, 0))
        assertSame(Typeface.MONOSPACE, StudyFontVariants.forVariant(throwingContext, 1))
        assertSame(Typeface.SERIF, StudyFontVariants.forVariant(throwingContext, 2))
    }
}
