package dev.bee.kanjianki;

import android.content.Context;
import android.content.res.Resources;
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
    public void fontVariantsLoadStudyFonts() {
        Context context = ApplicationProvider.getApplicationContext();

        int[] variants = {0, 1, 2, 99};
        for (int variant : variants) {
            assertNotNull(StudyFontVariants.forVariant(context, variant));
        }
    }

    @Test
    public void defaultFontVariantFallsBackWhenContextCannotLoadResources() {
        Context throwingContext = resourceThrowingContext();

        assertSame(Typeface.DEFAULT, StudyFontVariants.forVariant(throwingContext, 0));
    }

    @Test
    public void monospaceFontVariantFallsBackWhenContextCannotLoadResources() {
        Context throwingContext = resourceThrowingContext();

        assertSame(Typeface.MONOSPACE, StudyFontVariants.forVariant(throwingContext, 1));
    }

    @Test
    public void serifFontVariantFallsBackWhenContextCannotLoadResources() {
        Context throwingContext = resourceThrowingContext();

        assertSame(Typeface.SERIF, StudyFontVariants.forVariant(throwingContext, 2));
    }

    private static Context resourceThrowingContext() {
        Context context = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(context, R.style.AppTheme) {
            @Override
            public Resources getResources() {
                throw new Resources.NotFoundException("no font resources");
            }
        };
    }
}
