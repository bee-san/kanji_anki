package dev.bee.kanjianki;

import android.content.Context;
import android.graphics.Typeface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

@RunWith(AndroidJUnit4.class)
public final class StudyFontVariantsInstrumentedTest {
    @Test
    public void fontVariantsLoadAllStudyFontsAndDefaultVariant() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        assertNotNull(StudyFontVariants.forVariant(context, 0));
        assertNotNull(StudyFontVariants.forVariant(context, 1));
        assertNotNull(StudyFontVariants.forVariant(context, 2));
        assertNotNull(StudyFontVariants.forVariant(context, 99));
    }

    @Test
    public void fontVariantFallsBackWhenContextCannotLoadResources() {
        Context throwingContext = new android.view.ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                R.style.AppTheme
        ) {
            @Override
            public android.content.res.Resources getResources() {
                throw new RuntimeException("no font resources");
            }
        };

        assertSame(Typeface.DEFAULT, StudyFontVariants.forVariant(throwingContext, 0));
        assertSame(Typeface.MONOSPACE, StudyFontVariants.forVariant(throwingContext, 1));
        assertSame(Typeface.SERIF, StudyFontVariants.forVariant(throwingContext, 2));
    }
}
