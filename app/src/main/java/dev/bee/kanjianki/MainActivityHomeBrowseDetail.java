package dev.bee.kanjianki;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.widget.Toast;

import dev.bee.kanjianki.core.DateTextPolicy;
import dev.bee.kanjianki.core.FocusQueueCopy;
import dev.bee.kanjianki.core.HomeTextCopy;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.TimelineCopy;

import java.util.List;

public final class MainActivityHomeBrowseDetail {
    private final MainActivityHome home;

    MainActivityHomeBrowseDetail(MainActivityHome home) {
        this.home = home;
    }

    MainActivityHome home() {
        return home;
    }

    void renderBrowseKanji(String query) {
        home.activeBrowseQuery = query == null ? "" : query;
        home.base("home");
        List<RecordsImportModels.KanjiInventoryItem> items = home.store.searchKanjiInventory(query);
        home.content.addView(MainActivityHomeBrowseSearchCompose.browseScreenView(this, home.activeBrowseQuery, items));
    }

    void renderDetail(String kanji) {
        renderDetail(kanji, false);
    }

    void renderDetail(String kanji, boolean fromBrowse) {
        renderDetail(kanji, fromBrowse, fromBrowse ? home.activeBrowseQuery : "");
    }

    void renderDetail(String kanji, boolean fromBrowse, String browseQuery) {
        home.base("home");
        RecordsStudyModels.KanjiRecoveryTimeline timeline = home.store.timelineForKanji(kanji);
        RecordsImportModels.DashboardRow row = timeline.currentRow;
        RecordsImportModels.KanjiInventoryItem inventory = timeline.inventoryItem;
        if (inventory == null && row == null && timeline.currentStudyItem == null && timeline.events.isEmpty()) {
            home.content.addView(MainActivityHomeBrowseDetailCompose.browseDetailMissingView(
                    this,
                    new BrowseDetailMissingModel(
                            HomeTextCopy.homeLabel(),
                            home::renderHome,
                            HomeTextCopy.kanjiNotFoundTitle(),
                            HomeTextCopy.kanjiNotFoundBody()
                    )
            ));
            return;
        }
        String displayKanji = HomeTextCopy.detailDisplayKanji(kanji, row, inventory);
        boolean suspended = inventory != null && inventory.suspended;
        home.content.addView(MainActivityHomeBrowseDetailCompose.browseDetailScreenView(
                this,
                detailScreenModel(timeline, row, inventory, displayKanji, fromBrowse, browseQuery, suspended)
        ));
    }

    BrowseDetailScreenModel detailScreenModel(
            RecordsStudyModels.KanjiRecoveryTimeline timeline,
            RecordsImportModels.DashboardRow row,
            RecordsImportModels.KanjiInventoryItem inventory,
            String displayKanji,
            boolean fromBrowse,
            String browseQuery,
            boolean suspended
    ) {
        return new BrowseDetailScreenModel(
                detailHeroModel(displayKanji, fromBrowse, browseQuery),
                detailIdentityModel(row, inventory, suspended),
                detailReasonPanelModel(row, inventory),
                inventory == null ? null : localInventoryPanelModel(inventory),
                detailActionsModel(row, inventory, displayKanji, fromBrowse, browseQuery, suspended),
                recoveryTimelineModel(timeline),
                HomeTextCopy.examplesTitle(),
                row == null ? java.util.Collections.emptyList() : exampleModels(row.examples)
        );
    }

    BrowseDetailHeroModel detailHeroModel(String displayKanji, boolean fromBrowse, String browseQuery) {
        return new BrowseDetailHeroModel(
                displayKanji,
                fromBrowse ? HomeTextCopy.backToBrowseKanjiLabel() : HomeTextCopy.homeLabel(),
                fromBrowse ? () -> renderBrowseKanji(browseQuery) : home::renderHome
        );
    }

    BrowseDetailIdentityModel detailIdentityModel(RecordsImportModels.DashboardRow row, RecordsImportModels.KanjiInventoryItem inventory, boolean suspended) {
        String title = row == null ? HomeTextCopy.inventoryTitle(inventory) : StudyTextCopy.rowMeaning(row);
        String reading = row == null ? (inventory == null ? "" : inventory.readings) : row.reading;
        return new BrowseDetailIdentityModel(title, reading, suspended);
    }

    BrowseDetailPanelModel detailReasonPanelModel(RecordsImportModels.DashboardRow row, RecordsImportModels.KanjiInventoryItem inventory) {
        List<String> lines = new java.util.ArrayList<>();
        if (row == null) {
            lines.add(HomeTextCopy.historicalReasonText());
            if (inventory != null && !inventory.browserSearch.isEmpty()) {
                lines.add(HomeTextCopy.ankiBrowserLine(StudyTextCopy.compact(inventory.browserSearch, 96)));
            }
        } else {
            lines.add(HomeTextCopy.activeReasonText(row));
            if (!row.browserSearch.isEmpty()) {
                lines.add(HomeTextCopy.ankiBrowserLine(StudyTextCopy.compact(row.browserSearch, 96)));
            }
        }
        return new BrowseDetailPanelModel(HomeTextCopy.detailReasonTitle(), lines, home.BLUE, BrowseDetailPanelStyle.BAND);
    }

    BrowseDetailActionsModel detailActionsModel(RecordsImportModels.DashboardRow row, RecordsImportModels.KanjiInventoryItem inventory, String displayKanji, boolean fromBrowse, String browseQuery, boolean suspended) {
        String browserSearch = HomeTextCopy.detailBrowserSearch(row, inventory);
        return new BrowseDetailActionsModel(
                row != null && !suspended ? HomeTextCopy.reviewNowLabel() : null,
                row != null && !suspended ? () -> home.renderStudyForKanji(row.kanji) : null,
                browserSearch.isEmpty() ? null : HomeTextCopy.copyAnkiSearchLabel(),
                home.getString(R.string.copied_anki_search),
                browserSearch.isEmpty() ? null : () -> copyAnkiSearch(browserSearch),
                HomeTextCopy.localSuspendButtonLabel(suspended),
                () -> {
                    home.store.setKanjiLocallySuspended(displayKanji, !suspended, System.currentTimeMillis());
                    String toast = HomeTextCopy.localSuspendToast(suspended);
                    android.widget.Toast.makeText(home, toast, android.widget.Toast.LENGTH_SHORT).show();
                    renderDetail(displayKanji, fromBrowse, browseQuery);
                }
        );
    }

    BrowseDetailPanelModel localInventoryPanelModel(RecordsImportModels.KanjiInventoryItem inventory) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add(HomeTextCopy.localInventorySummary(inventory.sourceCount, inventory.exampleCount));
        if (!inventory.browserSearch.isEmpty()) {
            lines.add(HomeTextCopy.localInventorySearchLine(StudyTextCopy.compact(inventory.browserSearch, 96)));
        }
        if (inventory.lastSeenAtMillis > 0L) {
            lines.add(HomeTextCopy.localInventoryLastSeenLine(inventory.lastSeenAtMillis));
        }
        return new BrowseDetailPanelModel(HomeTextCopy.localInventoryTitle(), lines, Color.rgb(201, 245, 247), BrowseDetailPanelStyle.CARD);
    }

    private void copyAnkiSearch(String browserSearch) {
        ClipboardManager clipboard = (ClipboardManager) home.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(HomeTextCopy.ankiSearchClipLabel(), browserSearch));
        Toast.makeText(home, HomeTextCopy.ankiSearchCopiedToast(), Toast.LENGTH_SHORT).show();
    }

    BrowseTimelinePanelsModel recoveryTimelineModel(RecordsStudyModels.KanjiRecoveryTimeline timeline) {
        RecordsImportModels.DashboardRow row = timeline.currentRow;
        long now = System.currentTimeMillis();
        List<BrowseTimelineEventModel> events = new java.util.ArrayList<>();
        for (RecordsImportModels.KanjiTimelineEvent event : timeline.events) {
            events.add(new BrowseTimelineEventModel(
                    DateTextPolicy.timelineDate(event.occurredAtMillis),
                    event.title,
                    event.detail,
                    TimelineCopy.sourceLine(event),
                    timelineToneColor(TimelineCopy.eventTone(event.eventType))
            ));
        }
        return new BrowseTimelinePanelsModel(
                HomeTextCopy.recoveryTimelineTitle(),
                TimelineCopy.statusText(timeline, now),
                timelineToneColor(TimelineCopy.statusTone(timeline, now)),
                row != null
                        ? HomeTextCopy.matureSupportTargetText(row.matureSupportCount, home.settings().matureSupportThreshold)
                        : HomeTextCopy.noActiveEvidenceText(),
                events,
                timeline.events.isEmpty() ? HomeTextCopy.timelineEmptyText() : null
        );
    }

    int timelineToneColor(TimelineCopy.Tone tone) {
        if (tone == TimelineCopy.Tone.POSITIVE) {
            return home.TEAL;
        }
        if (tone == TimelineCopy.Tone.WARNING) {
            return home.CORAL;
        }
        return home.BLUE;
    }

    List<BrowseExampleCardModel> exampleModels(List<RecordsImportModels.Example> examples) {
        List<BrowseExampleCardModel> models = new java.util.ArrayList<>();
        for (RecordsImportModels.Example example : examples) {
            models.add(exampleModel(example));
        }
        return models;
    }

    BrowseExampleCardModel exampleModel(RecordsImportModels.Example example) {
        int color = MainActivityHome.SOURCE_SUSPENDED.equals(example.sourceType) ? home.CORAL : home.TEAL;
        return new BrowseExampleCardModel(
                HomeTextCopy.exampleSourceLabel(example),
                HomeTextCopy.exampleExpressionLine(example),
                example.sentence,
                HomeTextCopy.exampleMeaningLine(example),
                color
        );
    }

    public static final class BrowseTimelinePanelsModel {
        final String title;
        final String statusText;
        final int statusColor;
        final String supportText;
        final List<BrowseTimelineEventModel> events;
        final String emptyText;

        BrowseTimelinePanelsModel(String title, String statusText, int statusColor, String supportText, List<BrowseTimelineEventModel> events, String emptyText) {
            this.title = title;
            this.statusText = statusText;
            this.statusColor = statusColor;
            this.supportText = supportText;
            this.events = events == null ? new java.util.ArrayList<>() : events;
            this.emptyText = emptyText;
        }
    }

    public static final class BrowseTimelineEventModel {
        final String dateText;
        final String title;
        final String detail;
        final String sourceLine;
        final int color;

        BrowseTimelineEventModel(String dateText, String title, String detail, String sourceLine, int color) {
            this.dateText = dateText == null ? "" : dateText;
            this.title = title == null ? "" : title;
            this.detail = detail == null ? "" : detail;
            this.sourceLine = sourceLine == null ? "" : sourceLine;
            this.color = color;
        }
    }

}
