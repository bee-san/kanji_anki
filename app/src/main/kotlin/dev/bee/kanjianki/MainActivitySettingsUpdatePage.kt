package dev.bee.kanjianki

internal class MainActivitySettingsUpdatePage(private val activity: MainActivitySettings) {
    fun renderUpdate() {
        activity.base(MainActivityBase.NAV_SETTINGS_ROUTE)
        activity.content.addView(settingsUpdatePageView(activity))
    }
}
