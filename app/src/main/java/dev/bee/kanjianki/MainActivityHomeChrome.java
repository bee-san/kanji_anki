package dev.bee.kanjianki;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.bee.kanjianki.core.HomeTextCopy;

final class MainActivityHomeChrome {
    private final MainActivityHome home;

    MainActivityHomeChrome(MainActivityHome home) {
        this.home = home;
    }

    View homeActionRow() {
        return MainActivityHomeChromeCompose.homeActionRowView(home);
    }

    View homeSectionHeader(String title, String actionLabel, Runnable action) {
        return MainActivityHomeChromeCompose.homeSectionHeaderView(home, title, actionLabel, action);
    }

    View pillButton(String label, int iconRes, Runnable action) {
        LinearLayout button = new LinearLayout(home);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(home.dp(8), 0, home.dp(8), 0);
        ImageView icon = new ImageView(home);
        icon.setImageResource(iconRes);
        icon.setColorFilter(home.INK);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(home.dp(22), home.dp(22));
        iconLp.setMargins(0, 0, home.dp(7), 0);
        button.addView(icon, iconLp);
        TextView text = home.text(label, 13, home.INK, true);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(false);
        button.addView(text, new LinearLayout.LayoutParams(-2, -2));
        button.setBackground(home.panel(Color.WHITE, Color.rgb(235, 214, 228), home.dp(22)));
        button.setClickable(true);
        button.setOnClickListener(new RunnableClickListener(action));
        button.setMinimumHeight(home.dp(62));
        return button;
    }

    View fullWidthHomeButton() {
        return MainActivityHomeChromeCompose.fullWidthHomeButtonView(home);
    }
}
