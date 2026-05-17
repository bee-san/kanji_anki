package dev.bee.kanjianki;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.TextViewCompat;

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
    static final int STUDY_BG_SOFT = Color.rgb(255, 246, 251);
    static final int STUDY_HERO_PANEL = Color.rgb(253, 241, 247);
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

    LinearLayout softStudyCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        card.setBackground(panel(STUDY_CARD, STUDY_BORDER, dp(26)));
        card.setElevation(dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(12));
        card.setLayoutParams(lp);
        return card;
    }

    TextView modePill(String value) {
        TextView pill = text(value, 13, STUDY_PINK_DARK, true);
        pill.setGravity(Gravity.CENTER);
        pill.setIncludeFontPadding(false);
        pill.setPadding(dp(12), dp(7), dp(12), dp(7));
        pill.setBackground(panel(Color.rgb(255, 239, 247), STUDY_BORDER, dp(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, 0, dp(14));
        pill.setLayoutParams(lp);
        return pill;
    }

    LinearLayout softInsetPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setBackground(panel(STUDY_PANEL, STUDY_BORDER, dp(22)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, dp(10));
        panel.setLayoutParams(lp);
        return panel;
    }

    Button pinkPrimaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(19);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if ("Reveal".equals(label)) {
            button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_eye_24, 0, 0, 0);
            button.setCompoundDrawablePadding(dp(8));
            TextViewCompat.setCompoundDrawableTintList(button, ColorStateList.valueOf(Color.WHITE));
        }
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Color.rgb(255, 139, 182), STUDY_PINK_DARK }
        );
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), Color.rgb(255, 173, 205));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(20));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(42, 255, 255, 255)),
                background,
                mask
        ));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(62));
        lp.setMargins(dp(3), dp(8), dp(3), dp(8));
        button.setLayoutParams(lp);
        return button;
    }

    Button studySecondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(STUDY_PLUM);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(panel(Color.WHITE, STUDY_BORDER, dp(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
        lp.setMargins(dp(3), dp(6), dp(3), dp(6));
        button.setLayoutParams(lp);
        return button;
    }

    Button studyFailButton(String label) {
        Button button = studySecondaryButton(label);
        button.setTextColor(STUDY_PINK_DARK);
        button.setBackground(panel(Color.rgb(255, 245, 250), STUDY_BORDER, dp(18)));
        return button;
    }

    LinearLayout band(int color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), dp(20));
        box.setBackground(panel(color, color, dp(8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        box.setLayoutParams(lp);
        return box;
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

    LinearLayout panelBox(int fill, int stroke) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(panel(fill, stroke, dp(8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(7), 0, dp(7));
        box.setLayoutParams(lp);
        return box;
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

    TextView sectionTitle(String value) {
        TextView title = text(value, 22, INK, true);
        title.setPadding(0, dp(12), 0, dp(6));
        return title;
    }

    TextView chip(String value, int color) {
        TextView chip = text(value, 13, color, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setBackground(panel(softened(color), color, dp(7)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(7), dp(7), dp(2));
        chip.setLayoutParams(lp);
        return chip;
    }

    Button primaryButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(19);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(panel(color, color, dp(12)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(62));
        lp.setMargins(0, dp(8), 0, dp(8));
        button.setLayoutParams(lp);
        return button;
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

    int softened(int color) {
        if (color == CORAL) {
            return Color.rgb(255, 235, 243);
        }
        if (color == TEAL) {
            return Color.rgb(230, 250, 251);
        }
        if (color == GOLD || color == Color.rgb(247, 159, 0)) {
            return Color.rgb(255, 247, 220);
        }
        if (color == BLUE || color == LILAC) {
            return Color.rgb(242, 238, 255);
        }
        return Color.rgb(248, 238, 245);
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
