package dev.bee.kanjianki;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.LinearLayout;

import androidx.compose.ui.platform.ComposeView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class StudyTopBarComposeInstrumentedTest {
    @Test
    public void studyTopBarFactoryReturnsComposeContentWithLegacySpacing() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        View topBar = StudyTopBarCompose.studyTopBarView(context, 12, 24, 0.5f, () -> { }, () -> { });

        measureLayoutAndDraw(topBar);

        assertTrue(topBar instanceof ComposeView);
        assertTrue(topBar.getLayoutParams() instanceof LinearLayout.LayoutParams);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) topBar.getLayoutParams();
        assertEquals(dp(context, 18), params.bottomMargin);
    }

    @Test
    public void studyTopBarMeasuresAtCompactWidth() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        View topBar = StudyTopBarCompose.studyTopBarView(context, 12, 24, 0.5f, () -> { }, () -> { });

        topBar.measure(
                View.MeasureSpec.makeMeasureSpec(260, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.AT_MOST)
        );
        topBar.layout(0, 0, 260, topBar.getMeasuredHeight());

        assertEquals(260, topBar.getMeasuredWidth());
        assertTrue(topBar instanceof ComposeView);
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

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
