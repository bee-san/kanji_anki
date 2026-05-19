package dev.bee.kanjianki;

import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.bee.kanjianki.core.DateTextPolicy;
import dev.bee.kanjianki.core.FocusQueueCopy;
import dev.bee.kanjianki.core.HomeTextCopy;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.TimelineCopy;

import java.util.List;

final class MainActivityHomeBrowseDetail {
    private final MainActivityHome home;

    MainActivityHomeBrowseDetail(MainActivityHome home) {
        this.home = home;
    }

    void renderBrowseKanji(String query) {
        home.activeBrowseQuery = query == null ? "" : query;
        home.base("home");
        home.content.addView(home.fullWidthHomeButton());
        home.content.addView(home.text(HomeTextCopy.browseTitle(), 34, home.INK, true));
        home.content.addView(home.text(HomeTextCopy.browseBody(), 16, home.MUTED, false));
        home.addSpace(10);

        EditText search = new EditText(home);
        search.setSingleLine(true);
        search.setText(query == null ? "" : query);
        search.setHint(HomeTextCopy.browseSearchHint());
        search.setTextSize(18);
        home.content.addView(search, new LinearLayout.LayoutParams(-1, home.dp(58)));

        Button submit = home.primaryButton(HomeTextCopy.browseSearchButtonLabel(), home.TEAL);
        submit.setOnClickListener(new RunnableClickListener(() -> renderBrowseKanji(search.getText().toString())));
        home.content.addView(submit);

        List<RecordsImportModels.KanjiInventoryItem> items = home.store.searchKanjiInventory(query);
        home.content.addView(home.sectionTitle(HomeTextCopy.browseResultHeading(items.size())));
        if (items.isEmpty()) {
            home.emptyState(HomeTextCopy.browseEmptyTitle(), HomeTextCopy.browseEmptyBody());
            return;
        }
        for (RecordsImportModels.KanjiInventoryItem item : items) {
            home.content.addView(browseKanjiRow(item));
        }
    }

    View browseKanjiRow(RecordsImportModels.KanjiInventoryItem item) {
        LinearLayout box = home.panelBox(Color.WHITE, item.suspended ? home.CORAL : home.TEAL);
        box.setOnClickListener(new RunnableClickListener(() -> renderDetail(item.kanji, true)));
        LinearLayout top = new LinearLayout(home);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView glyph = home.text(item.kanji, 44, home.INK, true);
        glyph.setGravity(android.view.Gravity.CENTER);
        top.addView(glyph, new LinearLayout.LayoutParams(home.dp(74), home.dp(74)));
        LinearLayout copy = new LinearLayout(home);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(home.text(HomeTextCopy.browseItemMeaning(item), 19, home.INK, true));
        if (!item.readings.isEmpty()) {
            copy.addView(home.text(item.readings, 14, home.TEAL, true));
        }
        copy.addView(home.text(HomeTextCopy.browseInventorySummary(item.sourceCount, item.exampleCount), 14, home.MUTED, false));
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(top);
        if (item.suspended) {
            LinearLayout chips = new LinearLayout(home);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.addView(home.chip(HomeTextCopy.suspendedChipLabel(), home.CORAL));
            box.addView(chips);
        }
        return box;
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
        home.addRecoveryTimeline(timeline);
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
            copy.setOnClickListener(new ViewClickListener(v -> home.copyAnkiSearch(browserSearch, v)));
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
            home.content.addView(home.exampleView(example));
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

}
