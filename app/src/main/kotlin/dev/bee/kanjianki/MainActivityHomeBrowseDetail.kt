package dev.bee.kanjianki

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import dev.bee.kanjianki.core.DateTextPolicy
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StuckCardPolicy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.TimelineCopy
import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.KanjiNeighborPanelPolicy
import dev.bee.kanjianki.core.study.StrokeOrderDiagramPolicy

internal class MainActivityHomeBrowseDetail(private val home: MainActivityHome) {
    private data class BrowseRouteData(
        val model: BrowseScreenModel,
    )

    private data class BrowseDetailRouteData(
        val model: BrowseDetailScreenModel?,
        val missingModel: BrowseDetailMissingModel?,
    )

    fun renderBrowseKanji(query: String?, onlySimilarKanji: Boolean = false, allKanjiScope: Boolean = false) {
        val requestedQuery = query ?: ""
        home.activeBrowseQuery = requestedQuery
        home.activeBrowseSimilarOnly = onlySimilarKanji
        home.activeBrowseAllKanji = allKanjiScope
        home.renderAsyncHomeRoute(
            loadingTitle = HomeTextCopy.browseActionLabel(),
            load = {
                if (allKanjiScope) {
                    BrowseRouteData(allKanjiBrowseScreenModel(home, requestedQuery))
                } else {
                    val items = home.store.searchKanjiInventory(requestedQuery, onlySimilarKanji)
                    BrowseRouteData(browseScreenModel(home, requestedQuery, items, onlySimilarKanji, allKanjiScope = false))
                }
            },
            render = { data ->
                home.activeBrowseQuery = data.model.initialQuery
                home.activeBrowseSimilarOnly = data.model.similarFilterActive
                home.activeBrowseAllKanji = data.model.allKanjiScope
                home.renderHomeRoute(backAction = Runnable { home.renderHome() }) {
                    BrowseScreen(data.model)
                }
            },
            traceName = "browse-route",
        )
    }

    fun renderReadOnlyDetail(kanji: String, browseQuery: String?) {
        val requestedQuery = browseQuery ?: ""
        home.renderAsyncHomeRoute(
            loadingTitle = HomeTextCopy.browseActionLabel(),
            load = {
                val dictionary = home.warmDictionaryLookup()
                val entry = dictionary.lookupKanji(kanji)
                BrowseDetailRouteData(
                    if (entry != null) readOnlyDetailModel(entry, requestedQuery) else null,
                    if (entry == null) BrowseDetailMissingModel(
                        HomeTextCopy.backToBrowseKanjiLabel(),
                        Runnable { renderBrowseKanji(requestedQuery, allKanjiScope = true) },
                        HomeTextCopy.kanjiNotFoundTitle(),
                        HomeTextCopy.kanjiNotFoundBody()
                    ) else null
                )
            },
            render = { data ->
                val backAction = Runnable { renderBrowseKanji(requestedQuery, allKanjiScope = true) }
                if (data.missingModel != null) {
                    home.renderHomeRoute(backAction = backAction) { BrowseDetailMissing(data.missingModel) }
                } else {
                    home.renderHomeRoute(backAction = backAction) { BrowseDetailScreen(data.model!!) }
                }
            },
            traceName = "browse-readonly-detail",
        )
    }

    private fun readOnlyDetailModel(
        entry: DictionaryLookup.KanjiEntry,
        browseQuery: String,
    ): BrowseDetailScreenModel {
        val lines = mutableListOf<String>()
        if (entry.meanings.isNotEmpty()) lines.add(entry.meanings.joinToString(", "))
        if (entry.onReadings.isNotEmpty()) lines.add("On: " + entry.onReadings.joinToString("、"))
        if (entry.kunReadings.isNotEmpty()) lines.add("Kun: " + entry.kunReadings.joinToString("、"))
        if (entry.strokeCount > 0) lines.add(HomeTextCopy.localizedStrokeCount(entry.strokeCount))
        if (entry.grade > 0) lines.add(HomeTextCopy.localizedGrade(entry.grade))
        if (entry.jitenRank != null) lines.add(HomeTextCopy.localizedJitenRank(entry.jitenRank))
        return BrowseDetailScreenModel(
            hero = BrowseDetailHeroModel(
                entry.literal,
                HomeTextCopy.backToBrowseKanjiLabel(),
                Runnable { renderBrowseKanji(browseQuery, allKanjiScope = true) }
            ),
            identity = BrowseDetailIdentityModel(
                title = if (entry.meanings.isNotEmpty()) entry.meanings[0] else "",
                reading = entry.firstReading(),
                stateBadges = emptyList(),
            ),
            strokeOrder = strokeOrderModel(entry.literal),
            reason = BrowseDetailPanelModel(
                title = HomeTextCopy.browseDictionaryPanelTitle(),
                lines = lines,
                color = MainActivityBase.BLUE,
                style = BrowseDetailPanelStyle.CARD,
            ),
            neighbors = null,
            localInventory = BrowseDetailPanelModel(
                title = "",
                lines = listOf(HomeTextCopy.browseNotInDeckLine()),
                color = MainActivityBase.CORAL,
                style = BrowseDetailPanelStyle.BAND,
            ),
            mnemonicNote = BrowseMnemonicNoteModel(
                title = HomeTextCopy.mnemonicNoteTitle(),
                fieldLabel = HomeTextCopy.mnemonicNoteFieldLabel(),
                helper = HomeTextCopy.mnemonicNoteHelper(false),
                initialNote = "",
                saveLabel = HomeTextCopy.saveMnemonicNoteLabel(),
                onSave = { note -> saveMnemonicNote(entry.literal, note) },
            ),
            actions = BrowseDetailActionsModel(
                reviewLabel = null,
                onReview = null,
                copyLabel = null,
                copiedLabel = "",
                onCopy = null,
                suspendLabel = HomeTextCopy.localSuspendButtonLabel(false),
                onSuspend = Runnable {},
            ),
            timeline = MainActivityHomeBrowseDetail.BrowseTimelinePanelsModel(
                "",
                "",
                MainActivityBase.BLUE,
                "",
                emptyList(),
                null,
            ),
            examplesTitle = "",
            examples = emptyList(),
        )
    }

    fun renderDetail(kanji: String, fromBrowse: Boolean) {
        renderDetail(kanji, fromBrowse, if (fromBrowse) home.activeBrowseQuery else "", null)
    }

    fun renderDetail(kanji: String, fromBrowse: Boolean, browseQuery: String?) {
        renderDetail(kanji, fromBrowse, browseQuery, null)
    }

    fun renderDetail(kanji: String, fromBrowse: Boolean, browseQuery: String?, customBackAction: Runnable?) {
        val requestedQuery = browseQuery ?: ""
        home.renderAsyncHomeRoute(
            loadingTitle = HomeTextCopy.browseActionLabel(),
            load = {
                val timeline = home.store.timelineForKanji(kanji)
                val row = timeline.currentRow
                val inventory = timeline.inventoryItem
                val displayKanji = HomeTextCopy.detailDisplayKanji(kanji, row, inventory)
                val mnemonicNote = home.store.kanjiMnemonicNote(displayKanji)
                val isMissing = inventory == null &&
                    row == null &&
                    timeline.currentStudyItem == null &&
                    timeline.events.isEmpty() &&
                    mnemonicNote.isEmpty()
                val missingModel = if (isMissing) {
                    BrowseDetailMissingModel(
                        if (customBackAction != null) StudyTextCopy.backToStudyLabel() else HomeTextCopy.homeLabel(),
                        customBackAction ?: Runnable { home.renderHome() },
                        HomeTextCopy.kanjiNotFoundTitle(),
                        HomeTextCopy.kanjiNotFoundBody()
                    )
                } else {
                    null
                }
                val detailModel = if (isMissing) {
                    null
                } else {
                    detailScreenModel(
                        timeline,
                        row,
                        inventory,
                        displayKanji,
                        mnemonicNote,
                        fromBrowse,
                        requestedQuery,
                        inventory != null && inventory.suspended,
                        customBackAction,
                    )
                }
                BrowseDetailRouteData(detailModel, missingModel)
            },
            render = { data ->
                val detailBackAction = customBackAction ?: if (fromBrowse) {
                    Runnable { renderBrowseKanji(requestedQuery, home.activeBrowseSimilarOnly) }
                } else {
                    Runnable { home.renderHome() }
                }
                if (data.missingModel != null) {
                    home.renderHomeRoute(backAction = detailBackAction) {
                        BrowseDetailMissing(data.missingModel)
                    }
                } else {
                    home.renderHomeRoute(backAction = detailBackAction) {
                        BrowseDetailScreen(data.model!!)
                    }
                }
            },
            traceName = "browse-detail",
        )
    }

    fun detailScreenModel(
        timeline: RecordsStudyModels.KanjiRecoveryTimeline,
        row: RecordsImportModels.DashboardRow?,
        inventory: RecordsImportModels.KanjiInventoryItem?,
        displayKanji: String,
        mnemonicNote: String,
        fromBrowse: Boolean,
        browseQuery: String?,
        suspended: Boolean,
        customBackAction: Runnable? = null,
    ): BrowseDetailScreenModel {
        val stuck = isStuck(timeline.currentStudyItem, suspended)
        return BrowseDetailScreenModel(
            detailHeroModel(displayKanji, fromBrowse, browseQuery ?: "", customBackAction),
            detailIdentityModel(row, inventory, suspended, timeline.currentStudyItem),
            strokeOrderModel(displayKanji),
            detailReasonPanelModel(row, inventory),
            neighborsModel(displayKanji),
            inventory?.let(::localInventoryPanelModel),
            mnemonicNoteModel(displayKanji, mnemonicNote, stuck),
            detailActionsModel(
                row,
                inventory,
                timeline.currentStudyItem,
                displayKanji,
                fromBrowse,
                browseQuery ?: "",
                suspended,
            ),
            recoveryTimelineModel(timeline),
            HomeTextCopy.examplesTitle(),
            row?.examples?.let(::exampleModels).orEmpty()
        )
    }

    fun strokeOrderModel(kanji: String): BrowseStrokeOrderModel? {
        val guide = home.strokeGuide(kanji) ?: return null
        val diagram = StrokeOrderDiagramPolicy.build(guide)
        if (diagram.isEmpty()) return null
        val panels = diagram.panels.map { panel ->
            BrowseStrokeOrderPanelModel(
                strokes = panel.strokes.map { stroke ->
                    BrowseStrokeOrderStrokeModel(
                        points = stroke.inkStroke.points.map { pt -> Pair(pt.x, pt.y) },
                        highlighted = stroke.highlighted,
                    )
                },
                startPointX = panel.startPoint?.x,
                startPointY = panel.startPoint?.y,
                strokeNumber = panel.strokeNumber,
            )
        }
        val overflowText = if (diagram.omittedStrokeCount > 0) {
            HomeTextCopy.strokeOrderOverflow(diagram.omittedStrokeCount)
        } else {
            null
        }
        return BrowseStrokeOrderModel(
            title = HomeTextCopy.strokeOrderTitle(),
            panels = panels,
            overflowText = overflowText,
        )
    }

    fun neighborsModel(kanji: String): BrowseNeighborPanelModel? {
        val pairs = home.store.similarPairsForKanji(kanji)
        if (pairs.isEmpty()) return null
        val wrongPicks = home.store.choiceWrongPickCounts(System.currentTimeMillis())
        val inventory = home.store.searchKanjiInventory("", false)
        val meanings = inventory.associate { it.kanji to it.primaryMeaning }
        val rows = KanjiNeighborPanelPolicy.build(kanji, pairs, wrongPicks, meanings)
        if (rows.isEmpty()) return null
        return BrowseNeighborPanelModel(
            title = HomeTextCopy.confusedWithTitle(),
            rows = rows.map { row ->
                BrowseNeighborRowModel(
                    kanji = row.kanji,
                    meaning = row.meaning,
                    evidenceLine = HomeTextCopy.confusedWithEvidence(row.youPickedCount, row.itStoleCount),
                    onTap = Runnable { renderDetail(row.kanji, true) },
                )
            }
        )
    }

    fun detailHeroModel(
        displayKanji: String,
        fromBrowse: Boolean,
        browseQuery: String?,
        customBackAction: Runnable? = null,
    ): BrowseDetailHeroModel {
        return BrowseDetailHeroModel(
            displayKanji,
            when {
                customBackAction != null -> StudyTextCopy.backToStudyLabel()
                fromBrowse -> HomeTextCopy.backToBrowseKanjiLabel()
                else -> HomeTextCopy.homeLabel()
            },
            customBackAction ?: if (fromBrowse) {
                Runnable { renderBrowseKanji(browseQuery, home.activeBrowseSimilarOnly) }
            } else {
                Runnable { home.renderHome() }
            }
        )
    }

    fun detailIdentityModel(
        row: RecordsImportModels.DashboardRow?,
        inventory: RecordsImportModels.KanjiInventoryItem?,
        suspended: Boolean,
        studyItem: RecordsStudyModels.StudyItem? = null,
    ): BrowseDetailIdentityModel {
        val title = if (row == null) HomeTextCopy.inventoryTitle(inventory) else StudyTextCopy.rowMeaning(row)
        val reading = if (row == null) inventory?.readings.orEmpty() else row.reading
        val stateBadges = ArrayList<BrowseStateBadgeModel>()
        if (suspended) {
            stateBadges.add(BrowseStateBadgeModel(HomeTextCopy.suspendedChipLabel(), MainActivityBase.CORAL))
        }
        // Goal 68: surface a "stuck" chip when a card keeps failing at its
        // demotion floor, suggesting the learner try a mnemonic.
        if (isStuck(studyItem, suspended)) {
            stateBadges.add(BrowseStateBadgeModel(HomeTextCopy.stuckChipLabel(), MainActivityBase.CORAL))
        }
        return BrowseDetailIdentityModel(title, reading, stateBadges)
    }

    private fun isStuck(studyItem: RecordsStudyModels.StudyItem?, suspended: Boolean): Boolean {
        return !suspended && studyItem != null && StuckCardPolicy.isStuck(
            studyItem.state,
            studyItem.rung,
            studyItem.phase,
            studyItem.realAgainStreak,
            studyItem.rungAvailability(),
            null,
            RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK,
        )
    }

    fun mnemonicNoteModel(
        displayKanji: String,
        initialNote: String,
        stuck: Boolean,
    ): BrowseMnemonicNoteModel {
        return BrowseMnemonicNoteModel(
            title = HomeTextCopy.mnemonicNoteTitle(),
            fieldLabel = HomeTextCopy.mnemonicNoteFieldLabel(),
            helper = HomeTextCopy.mnemonicNoteHelper(stuck),
            initialNote = initialNote,
            saveLabel = HomeTextCopy.saveMnemonicNoteLabel(),
            onSave = { note -> saveMnemonicNote(displayKanji, note) },
        )
    }

    private fun saveMnemonicNote(displayKanji: String, note: String) {
        val normalizedNote = note.trim()
        home.io.execute {
            home.store.saveKanjiMnemonicNote(displayKanji, normalizedNote, System.currentTimeMillis())
            home.postToMainIfActive {
                val message = if (normalizedNote.isEmpty()) {
                    HomeTextCopy.mnemonicNoteClearedToast()
                } else {
                    HomeTextCopy.mnemonicNoteSavedToast()
                }
                Toast.makeText(home, message, Toast.LENGTH_SHORT).show()
            }
        }
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
        studyItem: RecordsStudyModels.StudyItem?,
        displayKanji: String,
        fromBrowse: Boolean,
        browseQuery: String?,
        suspended: Boolean,
    ): BrowseDetailActionsModel {
        val browserSearch = HomeTextCopy.detailBrowserSearch(row, inventory)
        val reviewEligible = BrowseManualReviewPolicy.shouldOfferReview(
            hasDashboardRow = row != null,
            suspended = suspended,
            item = studyItem,
        )
        val reviewAction = if (reviewEligible) {
            val reviewRow = requireNotNull(row)
            Runnable { home.renderStudyForKanji(reviewRow.kanji) }
        } else {
            null
        }
        return BrowseDetailActionsModel(
            if (reviewAction != null) HomeTextCopy.reviewNowLabel() else null,
            reviewAction,
            if (browserSearch.isEmpty()) null else HomeTextCopy.copyAnkiSearchLabel(),
            home.getString(R.string.copied_anki_search),
            if (browserSearch.isEmpty()) null else Runnable { copyAnkiSearch(browserSearch) },
            HomeTextCopy.localSuspendButtonLabel(suspended),
            Runnable {
                home.io.execute {
                    home.store.setKanjiLocallySuspended(displayKanji, !suspended, System.currentTimeMillis())
                    home.main.post {
                        Toast.makeText(home, HomeTextCopy.localSuspendToast(suspended), Toast.LENGTH_SHORT).show()
                        renderDetail(displayKanji, fromBrowse, browseQuery ?: "")
                    }
                }
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
        return BrowseDetailPanelModel(HomeTextCopy.localInventoryTitle(), lines, MainActivityBase.TEAL, BrowseDetailPanelStyle.CARD)
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
