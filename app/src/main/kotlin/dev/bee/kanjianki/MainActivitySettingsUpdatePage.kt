package dev.bee.kanjianki

internal class MainActivitySettingsUpdatePage(private val activity: MainActivitySettings) {
    fun renderUpdate() {
        val model = settingsUpdatePageModel(activity)
        activity.renderSettingsRoute {
            SettingsUpdatePage(model)
        }
    }
}
