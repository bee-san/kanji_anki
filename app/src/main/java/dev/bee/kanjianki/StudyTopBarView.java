package dev.bee.kanjianki;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

final class StudyTopBarView extends FrameLayout {
    StudyTopBarView(Context context) {
        this(context, null);
    }

    StudyTopBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0, 0f, () -> { }, () -> { });
    }

    StudyTopBarView(Context context, int completed, int target, float fraction, Runnable closeAction, Runnable settingsAction) {
        this(context, null, completed, target, fraction, closeAction, settingsAction);
    }

    private StudyTopBarView(
            Context context,
            AttributeSet attrs,
            int completed,
            int target,
            float fraction,
            Runnable closeAction,
            Runnable settingsAction
    ) {
        super(context, attrs);
        setPadding(0, 0, 0, dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(18));
        setLayoutParams(lp);

        View composeView = StudyTopBarCompose.studyTopBarView(
                context,
                completed,
                target,
                fraction,
                closeAction,
                settingsAction
        );
        addView(composeView, new FrameLayout.LayoutParams(-1, -2));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
