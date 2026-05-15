package dev.bee.kanjianki;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public final class StudyProgressPillViewInstrumentedTest {
    private static final int TRACK_COLOR = 0xFF123456;
    private static final int FILL_COLOR = 0xFFABCDEF;

    @Test
    public void progressPillDefaultConstructorsRenderTrackSafely() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        StudyProgressPillView defaultPill = new StudyProgressPillView(context);
        StudyProgressPillView attrsPill = new StudyProgressPillView(context, null);

        measureAndLayout(defaultPill);
        measureAndLayout(attrsPill);

        Bitmap first = drawToBitmap(defaultPill);
        Bitmap second = drawToBitmap(attrsPill);
        try {
            assertEquals(0xFFFBDDEC, first.getPixel(100, 20));
            assertEquals(0xFFFBDDEC, second.getPixel(100, 20));
        } finally {
            first.recycle();
            second.recycle();
        }
    }

    @Test
    public void progressPillClampsNegativeFractionToTrackOnly() {
        StudyProgressPillView pill = laidOutPill();

        pill.setFraction(-0.5f);

        Bitmap bitmap = drawToBitmap(pill);
        try {
            assertEquals(TRACK_COLOR, bitmap.getPixel(20, 20));
            assertEquals(TRACK_COLOR, bitmap.getPixel(180, 20));
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void progressPillClampsOverfullFractionToFillOnly() {
        StudyProgressPillView pill = laidOutPill();

        pill.setFraction(1.5f);

        Bitmap bitmap = drawToBitmap(pill);
        try {
            assertEquals(FILL_COLOR, bitmap.getPixel(20, 20));
            assertEquals(FILL_COLOR, bitmap.getPixel(180, 20));
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void progressPillHalfFillDrawsFillBeforeTrack() {
        StudyProgressPillView pill = laidOutPill();

        pill.setFraction(0.5f);

        Bitmap bitmap = drawToBitmap(pill);
        try {
            assertEquals(FILL_COLOR, bitmap.getPixel(50, 20));
            assertEquals(TRACK_COLOR, bitmap.getPixel(150, 20));
        } finally {
            bitmap.recycle();
        }
    }

    private static StudyProgressPillView laidOutPill() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        StudyProgressPillView pill = new StudyProgressPillView(context, TRACK_COLOR, FILL_COLOR);
        measureAndLayout(pill);
        return pill;
    }

    private static void measureAndLayout(StudyProgressPillView pill) {
        pill.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(40, android.view.View.MeasureSpec.EXACTLY)
        );
        pill.layout(0, 0, 200, 40);
    }

    private static Bitmap drawToBitmap(StudyProgressPillView pill) {
        Bitmap bitmap = Bitmap.createBitmap(200, 40, Bitmap.Config.ARGB_8888);
        pill.draw(new Canvas(bitmap));
        return bitmap;
    }
}
