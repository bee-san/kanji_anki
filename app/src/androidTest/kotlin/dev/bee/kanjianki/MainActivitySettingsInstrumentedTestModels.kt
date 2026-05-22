@file:JvmName("MainActivitySettingsInstrumentedTestModels")

package dev.bee.kanjianki

fun settingsUpdatePanelForTest(activity: Any): SettingsUpdatePanelModel {
    val settingsActivity = activity as MainActivitySettings
    return settingsUpdatePageModel(settingsActivity).panel
}
