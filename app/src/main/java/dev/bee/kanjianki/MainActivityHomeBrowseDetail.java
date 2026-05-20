package dev.bee.kanjianki;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
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
        home.content.addView(MainActivityHomeBrowseDetailCompose.browseScreenView(this, home.activeBrowseQuery, items));
    }

    View browseKanjiRow(RecordsImportModels.KanjiInventoryItem item) {
        return MainActivityHomeBrowseDetailCompose.browseKanjiRowView(this, item);
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
            home.content.addView(home.fullWidthHomeButton());
            home.emptyState(HomeTextCopy.kanjiNotFoundTitle(), HomeTextCopy.kanjiNotFoundBody());
            return;
        }
        String displayKanji = HomeTextCopy.detailDisplayKanji(kanji, row, inventory);
        addDetailHeader(displayKanji, fromBrowse, browseQuery);
        boolean suspended = inventory != null && inventory.suspended;
        addDetailIdentity(row, inventory, suspended);
        home.addSpace(10);
        home.content.addView(detailReasonPanel(row, inventory));
        if (inventory != null) {
            home.content.addView(localInventoryPanel(inventory));
        }
        addDetailActions(row, inventory, displayKanji, fromBrowse, browseQuery, suspended);
        home.addSpace(12);
        home.content.addView(MainActivityHomeBrowseDetailCompose.recoveryTimelinePanelsView(this, recoveryTimelineModel(timeline)));
        if (row != null) {
            addDetailExamples(row);
        }
    }

    void addDetailHeader(String displayKanji, boolean fromBrowse, String browseQuery) {
        if (!fromBrowse) {
            home.content.addView(home.fullWidthHomeButton());
        }
        TextView glyph = home.text(displayKanji, 92, home.INK, true);
        glyph.setGravity(android.view.Gravity.CENTER);
        home.content.addView(glyph);
        if (fromBrowse) {
            Button back = home.secondaryButton(HomeTextCopy.backToBrowseKanjiLabel());
            back.setOnClickListener(new RunnableClickListener(() -> renderBrowseKanji(browseQuery)));
            home.content.addView(back);
        }
    }

    void addDetailIdentity(RecordsImportModels.DashboardRow row, RecordsImportModels.KanjiInventoryItem inventory, boolean suspended) {
        if (suspended) {
            LinearLayout chips = new LinearLayout(home);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.addView(home.chip(HomeTextCopy.suspendedChipLabel(), home.CORAL));
            home.content.addView(chips);
        }
        if (row == null) {
            home.content.addView(home.text(HomeTextCopy.inventoryTitle(inventory), 25, home.INK, true));
            if (inventory != null && !inventory.readings.isEmpty()) {
                home.content.addView(home.text(inventory.readings, 20, home.TEAL, true));
            }
        } else {
            home.content.addView(home.text(StudyTextCopy.rowMeaning(row), 25, home.INK, true));
            if (!row.reading.isEmpty()) {
                home.content.addView(home.text(row.reading, 20, home.TEAL, true));
            }
        }
    }

    LinearLayout detailReasonPanel(RecordsImportModels.DashboardRow row, RecordsImportModels.KanjiInventoryItem inventory) {
        LinearLayout why = home.band(home.BLUE);
        why.addView(home.text(HomeTextCopy.detailReasonTitle(), 22, Color.WHITE, true));
        if (row == null) {
            why.addView(home.text(HomeTextCopy.historicalReasonText(), 17, Color.WHITE, false));
            if (inventory != null && !inventory.browserSearch.isEmpty()) {
                why.addView(home.text(HomeTextCopy.ankiBrowserLine(StudyTextCopy.compact(inventory.browserSearch, 96)), 14, Color.WHITE, false));
            }
        } else {
            why.addView(home.text(HomeTextCopy.activeReasonText(row), 17, Color.WHITE, false));
            if (!row.browserSearch.isEmpty()) {
                why.addView(home.text(HomeTextCopy.ankiBrowserLine(StudyTextCopy.compact(row.browserSearch, 96)), 14, Color.WHITE, false));
            }
        }
        return why;
    }

    void addDetailActions(RecordsImportModels.DashboardRow row, RecordsImportModels.KanjiInventoryItem inventory, String displayKanji, boolean fromBrowse, String browseQuery, boolean suspended) {
        if (row != null && !suspended) {
            Button practice = home.primaryButton(HomeTextCopy.reviewNowLabel(), home.CORAL);
            practice.setOnClickListener(new RunnableClickListener(() -> home.renderStudyForKanji(row.kanji)));
            home.content.addView(practice);
        }
        String browserSearch = HomeTextCopy.detailBrowserSearch(row, inventory);
        if (!browserSearch.isEmpty()) {
            Button copy = home.secondaryButton(HomeTextCopy.copyAnkiSearchLabel());
            copy.setOnClickListener(new ViewClickListener(v -> copyAnkiSearch(browserSearch, v)));
            home.content.addView(copy);
        }
        Button suspend = home.secondaryButton(HomeTextCopy.localSuspendButtonLabel(suspended));
        suspend.setOnClickListener(new RunnableClickListener(() -> {
            home.store.setKanjiLocallySuspended(displayKanji, !suspended, System.currentTimeMillis());
            String toast = HomeTextCopy.localSuspendToast(suspended);
            android.widget.Toast.makeText(home, toast, android.widget.Toast.LENGTH_SHORT).show();
            renderDetail(displayKanji, fromBrowse, browseQuery);
        }));
        home.content.addView(suspend);
    }

    void addDetailExamples(RecordsImportModels.DashboardRow row) {
        home.addSpace(12);
        home.content.addView(home.sectionTitle(HomeTextCopy.examplesTitle()));
        for (RecordsImportModels.Example example : row.examples) {
            home.content.addView(exampleView(example));
        }
    }

    View localInventoryPanel(RecordsImportModels.KanjiInventoryItem inventory) {
        LinearLayout box = home.panelBox(Color.WHITE, Color.rgb(201, 245, 247));
        box.addView(home.text(HomeTextCopy.localInventoryTitle(), 19, home.INK, true));
        box.addView(home.text(HomeTextCopy.localInventorySummary(inventory.sourceCount, inventory.exampleCount), 15, home.MUTED, false));
        if (!inventory.browserSearch.isEmpty()) {
            box.addView(home.text(HomeTextCopy.localInventorySearchLine(StudyTextCopy.compact(inventory.browserSearch, 96)), 14, home.MUTED, false));
        }
        if (inventory.lastSeenAtMillis > 0L) {
            box.addView(home.text(HomeTextCopy.localInventoryLastSeenLine(inventory.lastSeenAtMillis), 14, home.MUTED, false));
        }
        return box;
    }

    void copyAnkiSearch(String browserSearch, View v) {
        ClipboardManager clipboard = (ClipboardManager) home.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(HomeTextCopy.ankiSearchClipLabel(), browserSearch));
        if (v instanceof Button button) {
            button.setText(R.string.copied_anki_search);
        }
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

    View timelineStatusCard(RecordsStudyModels.KanjiRecoveryTimeline timeline) {
        int color = timelineToneColor(TimelineCopy.statusTone(timeline, System.currentTimeMillis()));
        LinearLayout box = home.panelBox(Color.WHITE, color);
        box.addView(home.text(TimelineCopy.statusText(timeline, System.currentTimeMillis()), 20, home.INK, true));
        RecordsImportModels.DashboardRow row = timeline.currentRow;
        if (row != null) {
            box.addView(home.text(HomeTextCopy.matureSupportTargetText(row.matureSupportCount, home.settings().matureSupportThreshold), 15, home.MUTED, false));
        } else {
            box.addView(home.text(HomeTextCopy.noActiveEvidenceText(), 15, home.MUTED, false));
        }
        return box;
    }

    View timelineEventView(RecordsImportModels.KanjiTimelineEvent event) {
        LinearLayout box = home.panelBox(Color.WHITE, timelineToneColor(TimelineCopy.eventTone(event.eventType)));
        box.addView(home.text(DateTextPolicy.timelineDate(event.occurredAtMillis), 13, home.MUTED, false));
        box.addView(home.text(event.title, 18, home.INK, true));
        if (!event.detail.isEmpty()) {
            box.addView(home.text(event.detail, 15, home.MUTED, false));
        }
        String source = TimelineCopy.sourceLine(event);
        if (!source.isEmpty()) {
            box.addView(home.text(source, 14, home.INK, true));
        }
        return box;
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

    View exampleView(RecordsImportModels.Example example) {
        int color = MainActivityHome.SOURCE_SUSPENDED.equals(example.sourceType) ? home.CORAL : home.TEAL;
        LinearLayout box = home.panelBox(Color.WHITE, color);
        box.addView(home.chip(HomeTextCopy.exampleSourceLabel(example), color));
        box.addView(home.text(HomeTextCopy.exampleExpressionLine(example), 22, home.INK, true));
        if (!example.sentence.isEmpty()) {
            box.addView(home.text(example.sentence, 16, home.MUTED, false));
        }
        String meaning = HomeTextCopy.exampleMeaningLine(example);
        if (!meaning.isEmpty()) {
            box.addView(home.text(meaning, 15, home.MUTED, false));
        }
        return box;
    }

}
