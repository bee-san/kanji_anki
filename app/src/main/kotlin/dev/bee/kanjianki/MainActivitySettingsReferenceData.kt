package dev.bee.kanjianki

import android.content.Intent
import android.widget.Toast
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.SettingsTextCopy

internal class MainActivitySettingsReferenceData(private val activity: MainActivitySettings) {
    fun dataLicenseSettingsPanelModel(): SettingsReferenceDataLinkModel {
        return SettingsReferenceDataLinkModel(
            title = SettingsTextCopy.offlineDataLicensesTitle(),
            body = SettingsTextCopy.offlineDataLicensesBody(),
            actionLabel = SettingsTextCopy.openDataLicensesLabel(),
            onAction = Runnable {
                activity.renderReferenceDataDetails()
            }
        )
    }

    /**
     * Debug-only panel that shares the study-load debug log via the Android share sheet, so the
     * log can be sent without digging through the Files app. Null in release builds.
     */
    fun shareDebugLogPanelModelOrNull(): SettingsReferenceDataLinkModel? {
        if (!BuildConfig.DEBUG) {
            return null
        }
        return SettingsReferenceDataLinkModel(
            title = "Study debug log",
            body = "Share the study-load timing log (kani-study-debug.log) so it can be diagnosed.",
            actionLabel = "Share debug log",
            onAction = Runnable { shareDebugLog() },
        )
    }

    private fun shareDebugLog() {
        StudyLoadDebugLog.prepareShareIntent(activity) { intent ->
            activity.postToMainIfActive {
                if (intent == null) {
                    Toast.makeText(
                        activity,
                        "No debug log yet — open Study first, then try again.",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@postToMainIfActive
                }
                activity.startActivity(Intent.createChooser(intent, "Share debug log"))
            }
        }
    }

    fun dataSourcesModel(): SettingsReferenceDataModel {
        return SettingsReferenceDataModel(
            dictionaryTitle = SettingsTextCopy.dictionaryDataTitle(),
            dictionaryBody = AttributionTexts.dictionarySources(activity),
            strokeTitle = SettingsTextCopy.strokeDataTitle(),
            strokeBody = AttributionTexts.kanjiVg(activity),
            fontsTitle = SettingsTextCopy.fontsTitle(),
            fontsBody = AttributionTexts.rawResourceText(activity, R.raw.font_attribution)
        )
    }

    internal fun referenceDataScreenModel(): SettingsReferenceDataScreenModel {
        return SettingsReferenceDataScreenModel(
            homeLabel = HomeTextCopy.homeLabel(),
            onHome = Runnable { activity.renderHome() },
            intro = SettingsReferenceDataIntroModel(
                backLabel = SettingsTextCopy.backToSettingsLabel(),
                title = SettingsTextCopy.dataLicensesTitle(),
                body = SettingsTextCopy.dataLicensesBody(),
                onBack = Runnable {
                    activity.renderSettingsDisplayData(true)
                }
            ),
            dataSources = dataSourcesModel()
        )
    }
}
