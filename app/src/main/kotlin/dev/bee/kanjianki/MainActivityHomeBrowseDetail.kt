package dev.bee.kanjianki

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.widget.Toast
import dev.bee.kanjianki.core.DateTextPolicy
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.TimelineCopy

internal class MainActivityHomeBrowseDetail(private val home: MainActivityHome) {
    fun home(): MainActivityHome {
        return home
    }

    fun renderBrowseKanji(query: String?) {
        home.activeBrowseQuery = query ?: ""
        val items = home.store.searchKanjiInventory(query)
        val model = browseScreenModel(this, home.activeBrowseQuery, items)
        home.renderHomeRoute {
            BrowseScreen(model)
        }
    }

    fun renderDetail(kanji: String) {
        renderDetail(kanji, false)
    }

    fun renderDetail(kanji: String, fromBrowse: Boolean) {
        renderDetail(kanji, fromBrowse, if (fromBrowse) home.activeBrowseQuery else "")
    }

    fun renderDetail(kanji: String, fromBrowse: Boolean, browseQuery: String?) {
        val timeline = home.store.timelineForKanji(kanji)
        val row = timeline.currentRow
        val inventory = timeline.inventoryItem
        if (inventory == null && row == null && timeline.currentStudyItem == null && timeline.events.isEmpty()) {
            val model = BrowseDetailMissingModel(
                        HomeTextCopy.homeLabel(),
                        Runnable { home.renderHome() },
                        HomeTextCopy.kanjiNotFoundTitle(),
                        HomeTextCopy.kanjiNotFoundBody()
            )
            home.renderHomeRoute {
                BrowseDetailMissing(model)
            }
            return
        }
        val displayKanji = HomeTextCopy.detailDisplayKanji(kanji, row, inventory)
        val suspended = inventory != null && inventory.suspended
        val model = detailScreenModel(timeline, row, inventory, displayKanji, fromBrowse, browseQuery ?: "", suspended)
        home.renderHomeRoute {
            BrowseDetailScreen(model)
        }
    }

    fun detailScreenModel(
        timeline: RecordsStudyModels.KanjiRecoveryTimeline,
        row: RecordsImportModels.DashboardRow?,
        inventory: RecordsImportModels.KanjiInventoryItem?,
        displayKanji: String,
        fromBrowse: Boolean,
        browseQuery: String?,
        suspended: Boolean,
    ): BrowseDetailScreenModel {
        return BrowseDetailScreenModel(
            detailHeroModel(displayKanji, fromBrowse, browseQuery ?: ""),
            detailIdentityModel(row, inventory, suspended),
            detailReasonPanelModel(row, inventory),
            inventory?.let(::localInventoryPanelModel),
            detailActionsModel(row, inventory, displayKanji, fromBrowse, browseQuery ?: "", suspended),
            recoveryTimelineModel(timeline),
            HomeTextCopy.examplesTitle(),
            row?.examples?.let(::exampleModels).orEmpty()
        )
    }

    fun detailHeroModel(displayKanji: String, fromBrowse: Boolean, browseQuery: String?): BrowseDetailHeroModel {
        return BrowseDetailHeroModel(
            displayKanji,
            if (fromBrowse) HomeTextCopy.backToBrowseKanjiLabel() else HomeTextCopy.homeLabel(),
            if (fromBrowse) Runnable { renderBrowseKanji(browseQuery) } else Runnable { home.renderHome() }
        )
    }

    fun detailIdentityModel(
        row: RecordsImportModels.DashboardRow?,
        inventory: RecordsImportModels.KanjiInventoryItem?,
        suspended: Boolean,
    ): BrowseDetailIdentityModel {
        val title = if (row == null) HomeTextCopy.inventoryTitle(inventory) else StudyTextCopy.rowMeaning(row)
        val reading = if (row == null) inventory?.readings.orEmpty() else row.reading
        return BrowseDetailIdentityModel(title, reading, suspended)
    }

    fun detailReasonPanelModel(
        row: RecordsImportModels.DashboardRow?,
        inventory: RecordsImportModels.KanjiInventoryItem?,
    ): BrowseDetailPanelModel {
        val lines = mutableListOf<String>()
        if (row == null) {
            lines.add(HomeTextCopy.historicalReasonText())
            if (inventory != null && inventory.browserSearch.isNotEmpty()) {
                lines.add(HomeTextCopy.ankiBrowserLine(StudyTextCopy.compact(inventory.browserSearch, 96)))
            }
        } else {
            lines.add(HomeTextCopy.activeReasonText(row))
            if (row.browserSearch.isNotEmpty()) {
                lines.add(HomeTextCopy.ankiBrowserLine(StudyTextCopy.compact(row.browserSearch, 96)))
            }
        }
        return BrowseDetailPanelModel(HomeTextCopy.detailReasonTitle(), lines, MainActivityBase.BLUE, BrowseDetailPanelStyle.BAND)
    }

    fun detailActionsModel(
        row: RecordsImportModels.DashboardRow?,
        inventory: RecordsImportModels.KanjiInventoryItem?,
        displayKanji: String,
        fromBrowse: Boolean,
        browseQuery: String?,
        suspended: Boolean,
    ): BrowseDetailActionsModel {
        val browserSearch = HomeTextCopy.detailBrowserSearch(row, inventory)
        return BrowseDetailActionsModel(
            if (row != null && !suspended) HomeTextCopy.reviewNowLabel() else null,
            if (row != null && !suspended) Runnable { home.renderStudyForKanji(row.kanji) } else null,
            if (browserSearch.isEmpty()) null else HomeTextCopy.copyAnkiSearchLabel(),
            home.getString(R.string.copied_anki_search),
            if (browserSearch.isEmpty()) null else Runnable { copyAnkiSearch(browserSearch) },
            HomeTextCopy.localSuspendButtonLabel(suspended),
            Runnable {
                home.store.setKanjiLocallySuspended(displayKanji, !suspended, System.currentTimeMillis())
                Toast.makeText(home, HomeTextCopy.localSuspendToast(suspended), Toast.LENGTH_SHORT).show()
                renderDetail(displayKanji, fromBrowse, browseQuery ?: "")
            }
        )
    }

    fun localInventoryPanelModel(inventory: RecordsImportModels.KanjiInventoryItem): BrowseDetailPanelModel {
        val lines = mutableListOf(HomeTextCopy.localInventorySummary(inventory.sourceCount, inventory.exampleCount))
        if (inventory.browserSearch.isNotEmpty()) {
            lines.add(HomeTextCopy.localInventorySearchLine(StudyTextCopy.compact(inventory.browserSearch, 96)))
        }
        if (inventory.lastSeenAtMillis > 0L) {
            lines.add(HomeTextCopy.localInventoryLastSeenLine(inventory.lastSeenAtMillis))
        }
        return BrowseDetailPanelModel(HomeTextCopy.localInventoryTitle(), lines, Color.rgb(201, 245, 247), BrowseDetailPanelStyle.CARD)
    }

    private fun copyAnkiSearch(browserSearch: String) {
        val clipboard = home.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(HomeTextCopy.ankiSearchClipLabel(), browserSearch))
        Toast.makeText(home, HomeTextCopy.ankiSearchCopiedToast(), Toast.LENGTH_SHORT).show()
    }

    fun recoveryTimelineModel(timeline: RecordsStudyModels.KanjiRecoveryTimeline): BrowseTimelinePanelsModel {
        val row = timeline.currentRow
        val now = System.currentTimeMillis()
        val events = timeline.events.map { event ->
            BrowseTimelineEventModel(
                DateTextPolicy.timelineDate(event.occurredAtMillis),
                event.title,
                event.detail,
                TimelineCopy.sourceLine(event),
                timelineToneColor(TimelineCopy.eventTone(event.eventType))
            )
        }
        return BrowseTimelinePanelsModel(
            HomeTextCopy.recoveryTimelineTitle(),
            TimelineCopy.statusText(timeline, now),
            timelineToneColor(TimelineCopy.statusTone(timeline, now)),
            if (row != null) {
                HomeTextCopy.matureSupportTargetText(row.matureSupportCount, home.settings().matureSupportThreshold)
            } else {
                HomeTextCopy.noActiveEvidenceText()
            },
            events,
            if (timeline.events.isEmpty()) HomeTextCopy.timelineEmptyText() else null
        )
    }

    fun timelineToneColor(tone: TimelineCopy.Tone): Int {
        if (tone == TimelineCopy.Tone.POSITIVE) {
            return MainActivityBase.TEAL
        }
        if (tone == TimelineCopy.Tone.WARNING) {
            return MainActivityBase.CORAL
        }
        return MainActivityBase.BLUE
    }

    fun exampleModels(examples: List<RecordsImportModels.Example>): List<BrowseExampleCardModel> {
        return examples.map(::exampleModel)
    }

    fun exampleModel(example: RecordsImportModels.Example): BrowseExampleCardModel {
        val color = if (MainActivityBase.SOURCE_SUSPENDED == example.sourceType) MainActivityBase.CORAL else MainActivityBase.TEAL
        return BrowseExampleCardModel(
            HomeTextCopy.exampleSourceLabel(example),
            HomeTextCopy.exampleExpressionLine(example),
            example.sentence,
            HomeTextCopy.exampleMeaningLine(example),
            color
        )
    }

    class BrowseTimelinePanelsModel(
        @JvmField val title: String,
        @JvmField val statusText: String,
        @JvmField val statusColor: Int,
        @JvmField val supportText: String,
        events: List<BrowseTimelineEventModel>?,
        @JvmField val emptyText: String?,
    ) {
        @JvmField
        val events: List<BrowseTimelineEventModel> = events ?: emptyList()
    }

    class BrowseTimelineEventModel(
        dateText: String?,
        title: String?,
        detail: String?,
        sourceLine: String?,
        @JvmField val color: Int,
    ) {
        @JvmField val dateText: String = dateText.orEmpty()
        @JvmField val title: String = title.orEmpty()
        @JvmField val detail: String = detail.orEmpty()
        @JvmField val sourceLine: String = sourceLine.orEmpty()
    }
}
