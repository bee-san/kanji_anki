@file:JvmName("MainActivitySettingsInstrumentedTestModels")

package dev.bee.kanjianki

import android.app.Activity

fun settingsUpdatePanelForTest(activity: Activity): SettingsUpdatePanelModel {
    val settingsActivity = activity as MainActivitySettings
    return settingsUpdatePageModel(settingsActivity).panel
}
