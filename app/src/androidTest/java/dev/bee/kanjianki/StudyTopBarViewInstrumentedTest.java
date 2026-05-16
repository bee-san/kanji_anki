package dev.bee.kanjianki;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public final class StudyTopBarViewInstrumentedTest {
    @Test
    public void studyTopBarDefaultConstructorsRenderNoopToolbar() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        StudyTopBarView topBar = new StudyTopBarView(context);
        StudyTopBarView topBarWithAttrs = new StudyTopBarView(context, null);

        measureLayoutAndDraw(topBar);
        measureLayoutAndDraw(topBarWithAttrs);

        assertNotNull(findText(topBar, "0 / 0"));
        assertNotNull(findContentDescription(topBar, "Close study"));
        assertNotNull(findContentDescription(topBarWithAttrs, "Settings"));
    }

    @Test
    public void studyTopBarShowsProgressAndRunsActions() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AtomicInteger closeClicks = new AtomicInteger();
        AtomicInteger settingsClicks = new AtomicInteger();
        StudyTopBarView topBar = new StudyTopBarView(context, 2, 5, 0.4f, closeClicks::incrementAndGet, settingsClicks::incrementAndGet);
        measureLayoutAndDraw(topBar);

        assertNotNull(findText(topBar, "2 / 5"));
        View close = findContentDescription(topBar, "Close study");
        View settings = findContentDescription(topBar, "Settings");
        assertNotNull(close);
        assertNotNull(settings);

        close.performClick();
        settings.performClick();

        assertEquals(1, closeClicks.get());
        assertEquals(1, settingsClicks.get());
    }

    @Test
    public void studyTopBarMeasuresAtCompactWidth() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        StudyTopBarView topBar = new StudyTopBarView(context, 12, 24, 0.5f, () -> { }, () -> { });

        topBar.measure(
                View.MeasureSpec.makeMeasureSpec(260, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.AT_MOST)
        );
        topBar.layout(0, 0, 260, topBar.getMeasuredHeight());

        assertEquals(260, topBar.getMeasuredWidth());
        assertNotNull(findText(topBar, "12 / 24"));
    }

    private static void measureLayoutAndDraw(View view) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(220, View.MeasureSpec.AT_MOST)
        );
        view.layout(0, 0, 1080, view.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(1080, Math.max(1, view.getMeasuredHeight()), Bitmap.Config.ARGB_8888);
        try {
            view.draw(new Canvas(bitmap));
        } finally {
            bitmap.recycle();
        }
    }

    private static TextView findText(View root, String text) {
        if (root instanceof TextView) {
            CharSequence value = ((TextView) root).getText();
            if (value != null && value.toString().contains(text)) {
                return (TextView) root;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static View findContentDescription(View root, String description) {
        CharSequence value = root.getContentDescription();
        if (value != null && description.contentEquals(value)) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findContentDescription(group.getChildAt(i), description);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
