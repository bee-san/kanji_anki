package dev.bee.kanjianki

import android.view.View
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.SettingsTextCopy

internal class MainActivitySettingsReferenceData(private val activity: MainActivitySettings) {
    fun dataLicenseSettingsPanel(): View {
        return referenceDataLinkPanelView(activity, dataLicenseSettingsPanelModel())
    }

    fun dataLicenseSettingsPanelModel(): SettingsReferenceDataLinkModel {
        return SettingsReferenceDataLinkModel(
            title = SettingsTextCopy.offlineDataLicensesTitle(),
            body = SettingsTextCopy.offlineDataLicensesBody(),
            actionLabel = SettingsTextCopy.openDataLicensesLabel(),
            onAction = Runnable { renderDataSources() }
        )
    }

    fun renderDataSources() {
        val model = referenceDataScreenModel()
        activity.composeRoute(MainActivityBase.NAV_SETTINGS_ROUTE) {
            ReferenceDataScreen(model)
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

    private fun referenceDataScreenModel(): SettingsReferenceDataScreenModel {
        return SettingsReferenceDataScreenModel(
            homeLabel = HomeTextCopy.homeLabel(),
            onHome = Runnable { activity.renderHome() },
            intro = SettingsReferenceDataIntroModel(
                backLabel = SettingsTextCopy.backToSettingsLabel(),
                title = SettingsTextCopy.dataLicensesTitle(),
                body = SettingsTextCopy.dataLicensesBody(),
                onBack = Runnable { activity.renderSettings(false) }
            ),
            dataSources = dataSourcesModel()
        )
    }
}
