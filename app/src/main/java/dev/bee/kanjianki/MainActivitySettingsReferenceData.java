package dev.bee.kanjianki;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.SettingsTextCopy;

final class MainActivitySettingsReferenceData {
    private final MainActivitySettings activity;

    MainActivitySettingsReferenceData(MainActivitySettings activity) {
        this.activity = activity;
    }

    LinearLayout dataLicenseSettingsPanel() {
        LinearLayout box = activity.settingsPanelBox();
        box.addView(activity.text(SettingsTextCopy.offlineDataLicensesTitle(), 23, activity.INK, true));
        box.addView(activity.text(SettingsTextCopy.offlineDataLicensesBody(), 15, activity.MUTED, false));
        Button open = activity.secondaryButton(SettingsTextCopy.openDataLicensesLabel());
        open.setOnClickListener(new RunnableClickListener(this::renderDataSources));
        box.addView(open);
        return box;
    }

    void renderDataSources() {
        activity.base(activity.NAV_SETTINGS_ROUTE);
        activity.content.addView(activity.fullWidthHomeButton());
        Button backButton = activity.secondaryButton(SettingsTextCopy.backToSettingsLabel());
        backButton.setOnClickListener(new RunnableClickListener(() -> activity.renderSettings(false)));
        activity.content.addView(backButton);
        activity.content.addView(activity.text(SettingsTextCopy.dataLicensesTitle(), 34, activity.INK, true));
        activity.content.addView(activity.text(SettingsTextCopy.dataLicensesBody(), 16, activity.MUTED, false));
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

    private static final class RunnableClickListener implements View.OnClickListener {
        private final Runnable action;

        RunnableClickListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void onClick(View v) {
            action.run();
        }
    }
}
