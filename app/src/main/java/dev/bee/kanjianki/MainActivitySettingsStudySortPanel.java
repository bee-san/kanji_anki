package dev.bee.kanjianki;

import android.view.View;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SettingsTextCopy;

import java.util.Arrays;

final class MainActivitySettingsStudySortPanel {
    private final MainActivitySettings activity;
    private final MainActivitySettingsStudySortActions actions;

    MainActivitySettingsStudySortPanel(MainActivitySettings activity) {
        this.activity = activity;
        this.actions = new MainActivitySettingsStudySortActions(activity);
    }

    View newCardSortSettingsPanel(RecordsSyncModels.Settings current) {
        return MainActivitySettingsStudySortCompose.newCardSortSettingsPanelView(
                activity,
                new SettingsNewCardSortPanelModel(
                        SettingsTextCopy.newCardSortTitle(),
                        SettingsTextCopy.newCardSortBody(),
                        current.newCardSortMode,
                        Arrays.asList(
                                new SettingsNewCardSortOptionModel(
                                        SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FREQUENCY),
                                        RecordsBase.NEW_CARD_SORT_FREQUENCY
                                ),
                                new SettingsNewCardSortOptionModel(
                                        SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY),
                                        RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY
                                ),
                                new SettingsNewCardSortOptionModel(
                                        SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
                                        RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK
                                ),
                                new SettingsNewCardSortOptionModel(
                                        SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS),
                                        RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS
                                )
                        ),
                        SettingsTextCopy.saveNewCardSortLabel(),
                        actions::saveNewCardSort
                )
        );
    }
}
