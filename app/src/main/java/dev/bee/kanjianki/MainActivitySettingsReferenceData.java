package dev.bee.kanjianki;

import android.view.View;

import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsReferenceData {
    private final MainActivitySettings activity;

    MainActivitySettingsReferenceData(MainActivitySettings activity) {
        this.activity = activity;
    }

    View dataLicenseSettingsPanel() {
        return MainActivitySettingsReferenceDataCompose.referenceDataLinkPanelView(
                activity,
                new SettingsReferenceDataLinkModel(
                        SettingsTextCopy.offlineDataLicensesTitle(),
                        SettingsTextCopy.offlineDataLicensesBody(),
                        SettingsTextCopy.openDataLicensesLabel(),
                        this::renderDataSources
                )
        );
    }

    void renderDataSources() {
        activity.base(activity.NAV_SETTINGS_ROUTE);
        activity.content.addView(activity.fullWidthHomeButton());
        activity.content.addView(MainActivitySettingsReferenceDataCompose.dataSourcesIntroView(
                activity,
                new SettingsReferenceDataIntroModel(
                        SettingsTextCopy.backToSettingsLabel(),
                        SettingsTextCopy.dataLicensesTitle(),
                        SettingsTextCopy.dataLicensesBody(),
                        () -> activity.renderSettings(false)
                )
        ));
        activity.content.addView(MainActivitySettingsReferenceDataCompose.dataSourcesPanelsView(activity, dataSourcesModel()));
    }

    SettingsReferenceDataModel dataSourcesModel() {
        return new SettingsReferenceDataModel(
                SettingsTextCopy.dictionaryDataTitle(),
                AttributionTexts.dictionarySources(activity),
                SettingsTextCopy.strokeDataTitle(),
                AttributionTexts.kanjiVg(activity),
                SettingsTextCopy.fontsTitle(),
                AttributionTexts.rawResourceText(activity, R.raw.font_attribution)
        );
    }
}
