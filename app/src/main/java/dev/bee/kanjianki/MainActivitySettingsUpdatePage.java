package dev.bee.kanjianki;

final class MainActivitySettingsUpdatePage {
    private final MainActivitySettings activity;

    MainActivitySettingsUpdatePage(MainActivitySettings activity) {
        this.activity = activity;
    }

    void renderUpdate() {
        activity.base(activity.NAV_SETTINGS_ROUTE);
        activity.content.addView(MainActivitySettingsUpdatePageCompose.settingsUpdatePageView(activity));
    }
}
