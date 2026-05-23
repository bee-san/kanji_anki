package dev.bee.kanjianki;

import android.content.Context;
import android.graphics.Typeface;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class StudyFontVariantsTest {
    @Test
    public void fontVariantsLoadAllStudyFontsAndDefaultVariant() {
        Context context = ApplicationProvider.getApplicationContext();

        assertNotNull(StudyFontVariants.forVariant(context, 0));
        assertNotNull(StudyFontVariants.forVariant(context, 1));
        assertNotNull(StudyFontVariants.forVariant(context, 2));
        assertNotNull(StudyFontVariants.forVariant(context, 99));
    }

    @Test
    public void fontVariantFallsBackWhenContextCannotLoadResources() {
        Context context = ApplicationProvider.getApplicationContext();
        Context throwingContext = new ContextThemeWrapper(context, R.style.AppTheme) {
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
