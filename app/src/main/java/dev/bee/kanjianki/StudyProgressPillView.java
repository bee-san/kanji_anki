package dev.bee.kanjianki;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

final class StudyProgressPillView extends View {
    private static final int DEFAULT_TRACK_COLOR = 0xFFFBDDEC;
    private static final int DEFAULT_FILL_COLOR = 0xFFF82D72;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int trackColor;
    private final int fillColor;
    private float fraction;

    StudyProgressPillView(Context context) {
        this(context, null);
    }

    StudyProgressPillView(Context context, AttributeSet attrs) {
        this(context, attrs, DEFAULT_TRACK_COLOR, DEFAULT_FILL_COLOR);
    }

    StudyProgressPillView(Context context, int trackColor, int fillColor) {
        this(context, null, trackColor, fillColor);
    }

    private StudyProgressPillView(Context context, AttributeSet attrs, int trackColor, int fillColor) {
        super(context);
        this.trackColor = trackColor;
        this.fillColor = fillColor;
    }

    void setFraction(float value) {
        fraction = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = getHeight() / 2f;
        paint.setColor(trackColor);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);
        paint.setColor(fillColor);
        canvas.drawRoundRect(0, 0, getWidth() * fraction, getHeight(), radius, radius, paint);
    }
}
