package dev.bee.kanjianki

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudyFontVariantsTest {
    @Test
    fun fontVariantsLoadStudyFonts() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        for (variant in intArrayOf(0, 1, 2, 99)) {
            assertNotNull(StudyFontVariants.forVariant(context, variant))
        }
    }

    @Test
    fun defaultFontVariantFallsBackWhenContextCannotLoadResources() {
        val throwingContext = resourceThrowingContext()

        assertSame(Typeface.DEFAULT, StudyFontVariants.forVariant(throwingContext, 0))
    }

    @Test
    fun monospaceFontVariantFallsBackWhenContextCannotLoadResources() {
        val throwingContext = resourceThrowingContext()

        assertSame(Typeface.MONOSPACE, StudyFontVariants.forVariant(throwingContext, 1))
    }

    @Test
    fun serifFontVariantFallsBackWhenContextCannotLoadResources() {
        val throwingContext = resourceThrowingContext()

        assertSame(Typeface.SERIF, StudyFontVariants.forVariant(throwingContext, 2))
    }

    private fun resourceThrowingContext(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return object : ContextThemeWrapper(context, R.style.AppTheme) {
            override fun getResources(): Resources {
                throw Resources.NotFoundException("no font resources")
            }
        }
    }
}
