@file:JvmName("MainActivitySettingsInstrumentedTestModels")

package dev.bee.kanjianki

import android.app.Activity
import dev.bee.kanjianki.core.SettingsTextCopy

fun settingsUpdatePanelForTest(activity: Activity): SettingsUpdatePanelModel {
    val settingsActivity = activity as MainActivitySettings
    return settingsUpdatePanelModel(settingsActivity, SettingsTextCopy.automaticUpdatesTitle())
}
