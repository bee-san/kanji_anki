package dev.bee.kanjianki;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.List;

abstract class MainActivityUiSupport extends Activity {
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

    LinearLayout twoColumnGrid(List<View> items) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setBaselineAligned(false);
        for (int i = 0; i < items.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBaselineAligned(false);
            addGridCell(row, items.get(i), true);
            if (i + 1 < items.size()) {
                addGridCell(row, items.get(i + 1), false);
            } else {
                SpaceView spacer = new SpaceView(this);
                addGridCell(row, spacer, false);
            }
            grid.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        return grid;
    }

    private void addGridCell(LinearLayout row, View child, boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(first ? 0 : dp(4), dp(4), first ? dp(4) : 0, dp(4));
        row.addView(child, lp);
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

    Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(INK);
        button.setBackground(panel(Color.WHITE, Color.rgb(238, 189, 218), dp(12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.setMargins(dp(3), dp(6), dp(3), dp(6));
        button.setLayoutParams(lp);
        return button;
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

    static final class EqualHeightRow extends LinearLayout {
        EqualHeightRow(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            int maxOuterHeight = 0;
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                maxOuterHeight = Math.max(maxOuterHeight, measuredOuterHeight(child));
            }
            if (maxOuterHeight <= 0) {
                return;
            }

            int childAreaHeight = maxOuterHeight;
            if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) {
                childAreaHeight = Math.max(0, View.MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom());
            }

            for (int i = 0; i < childCount; i++) {
                measureVisibleChild(getChildAt(i), childAreaHeight);
            }

            if (View.MeasureSpec.getMode(heightMeasureSpec) != View.MeasureSpec.EXACTLY) {
                setMeasuredDimension(getMeasuredWidth(), getPaddingTop() + getPaddingBottom() + maxOuterHeight);
            }
        }

        static int measuredOuterHeight(View child) {
            int outerHeight = child.getMeasuredHeight();
            ViewGroup.LayoutParams rawLp = child.getLayoutParams();
            if (rawLp instanceof ViewGroup.MarginLayoutParams marginLp) {
                outerHeight += marginLp.topMargin + marginLp.bottomMargin;
            }
            return outerHeight;
        }

        static void measureVisibleChild(View child, int childAreaHeight) {
            if (child.getVisibility() == GONE) {
                return;
            }
            int childHeight = childAreaHeight;
            ViewGroup.LayoutParams rawLp = child.getLayoutParams();
            if (rawLp instanceof ViewGroup.MarginLayoutParams marginLp) {
                childHeight -= marginLp.topMargin + marginLp.bottomMargin;
            }
            if (childHeight <= 0) {
                return;
            }
            child.measure(
                    View.MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(childHeight, View.MeasureSpec.EXACTLY)
            );
        }
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
