package dev.bee.kanjianki;

import android.view.View;

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

    View fullWidthHomeButton() {
        return MainActivityHomeChromeCompose.fullWidthHomeButtonView(home);
    }
}
