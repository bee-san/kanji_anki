package dev.bee.kanjianki;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.core.view.WindowInsetsControllerCompat;

abstract class MainActivityUiSupport extends ComponentActivity {
    static final int BG = Color.rgb(255, 247, 251);
    static final int INK = Color.rgb(45, 22, 53);
    static final int MUTED = Color.rgb(108, 86, 116);
    static final int CORAL = Color.rgb(255, 76, 118);
    static final int TEAL = Color.rgb(0, 174, 181);
    static final int GOLD = Color.rgb(255, 214, 64);
    static final int BLUE = Color.rgb(110, 92, 230);
    static final int BLUSH = Color.rgb(255, 239, 246);
    static final int PINK_STROKE = Color.rgb(255, 174, 204);
    static final int LILAC = Color.rgb(118, 72, 255);
    static final int STUDY_BG = Color.rgb(255, 245, 250);
    static final int STUDY_CARD = Color.rgb(255, 255, 255);
    static final int STUDY_PANEL = Color.rgb(255, 236, 245);
    static final int STUDY_PLUM = Color.rgb(75, 37, 82);
    static final int STUDY_MUTED = Color.rgb(130, 96, 132);
    static final int STUDY_PINK_DARK = Color.rgb(218, 58, 122);
    static final int STUDY_BORDER = Color.rgb(255, 199, 222);
    static final int STUDY_PILL = Color.rgb(255, 239, 247);
    static final int STUDY_BG_SOFT = Color.rgb(255, 246, 251);
    static final int STUDY_HERO_PANEL = Color.rgb(253, 241, 247);
    static final int STUDY_HERO_PILL = Color.rgb(253, 239, 246);
    static final int STUDY_HERO_PINK = Color.rgb(248, 45, 114);
    static final int STUDY_HERO_PLUM = Color.rgb(33, 7, 44);
    static final int STUDY_HERO_MUTED = Color.rgb(102, 82, 110);

    void styleSystemBars() {
        getWindow().getDecorView().setBackgroundColor(BG);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
    }

    TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value == null ? "" : value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setIncludeFontPadding(true);
        text.setLineSpacing(0, 1.05f);
        text.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return text;
    }

    GradientDrawable panel(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    static final class SpaceView extends View {
        SpaceView(Context context) {
            super(context);
        }
    }

    static final class SquarePadFrame extends ViewGroup {
        private final int maxSizePx;

        SquarePadFrame(Context context) {
            this(context, null, 0, 0);
        }

        SquarePadFrame(Context context, AttributeSet attrs) {
            this(context, attrs, 0, 0);
        }

        SquarePadFrame(Context context, AttributeSet attrs, int defStyleAttr) {
            this(context, attrs, defStyleAttr, 0);
        }

        SquarePadFrame(Context context, int maxSizePx) {
            this(context, null, 0, maxSizePx);
        }

        private SquarePadFrame(Context context, AttributeSet attrs, int defStyleAttr, int maxSizePx) {
            super(context, attrs, defStyleAttr);
            this.maxSizePx = maxSizePx;
            setClipToPadding(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);
            int horizontalPadding = getPaddingLeft() + getPaddingRight();
            int verticalPadding = getPaddingTop() + getPaddingBottom();
            int effectiveMaxSize = maxSizePx <= 0 ? Integer.MAX_VALUE : maxSizePx;
            int availableWidth = widthMode == MeasureSpec.UNSPECIFIED
                    ? effectiveMaxSize
                    : Math.max(0, widthSize - horizontalPadding);
            int availableHeight = heightMode == MeasureSpec.UNSPECIFIED
                    ? effectiveMaxSize
                    : Math.max(0, heightSize - verticalPadding);
            int size = Math.max(0, Math.min(Math.min(availableWidth, availableHeight), effectiveMaxSize));
            int childSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE) {
                    child.measure(childSpec, childSpec);
                }
            }
            int measuredWidth = widthMode == MeasureSpec.EXACTLY ? widthSize : size + horizontalPadding;
            int measuredHeight = size + verticalPadding;
            if (heightMode == MeasureSpec.EXACTLY) {
                measuredHeight = heightSize;
            } else if (heightMode == MeasureSpec.AT_MOST) {
                measuredHeight = Math.min(measuredHeight, heightSize);
            }
            setMeasuredDimension(measuredWidth, measuredHeight);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int childCount = getChildCount();
            if (childCount == 0) {
                return;
            }
            int contentLeft = getPaddingLeft();
            int contentTop = getPaddingTop();
            int contentWidth = Math.max(0, right - left - getPaddingLeft() - getPaddingRight());
            int contentHeight = Math.max(0, bottom - top - getPaddingTop() - getPaddingBottom());
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                int size = Math.min(child.getMeasuredWidth(), child.getMeasuredHeight());
                int childLeft = contentLeft + Math.max(0, (contentWidth - size) / 2);
                int childTop = contentTop + Math.max(0, (contentHeight - size) / 2);
                child.layout(childLeft, childTop, childLeft + size, childTop + size);
            }
        }
    }
}
