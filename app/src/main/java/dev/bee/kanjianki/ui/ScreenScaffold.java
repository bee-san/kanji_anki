package dev.bee.kanjianki.ui;

import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

public final class ScreenScaffold {
    private ScreenScaffold() {
    }

    @SuppressWarnings({"deprecation", "java:S1874"})
    public static void styleLightBars(Window window, int color) {
        window.setStatusBarColor(color);
        window.setNavigationBarColor(color);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
    }

    @SuppressWarnings("deprecation")
    public static void applySystemBarPadding(
            View root,
            View content,
            int left,
            int top,
            int right,
            int bottom
    ) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int systemTop;
            int systemBottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                systemTop = bars.top;
                systemBottom = bars.bottom;
            } else {
                systemTop = insets.getSystemWindowInsetTop();
                systemBottom = insets.getSystemWindowInsetBottom();
            }
            content.setPadding(left, top + systemTop, right, bottom + systemBottom);
            return insets;
        });
        root.requestApplyInsets();
    }
}
