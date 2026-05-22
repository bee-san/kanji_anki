package dev.bee.kanjianki

internal class MainActivitySettingsUpdatePage(private val activity: MainActivitySettings) {
    fun renderUpdate() {
        val model = settingsUpdatePageModel(activity)
        activity.composeRoute(MainActivityBase.NAV_SETTINGS_ROUTE) {
            SettingsUpdatePage(model)
        }
    }
}
