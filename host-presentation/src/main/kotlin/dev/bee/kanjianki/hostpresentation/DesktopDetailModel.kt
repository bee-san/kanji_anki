package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.DateTextPolicy
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.KanjiNeighborPanelPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.core.StuckCardPolicy
import dev.bee.kanjianki.core.TimelineCopy
import dev.bee.kanjianki.data.HomeKanjiDetailSnapshot
import dev.bee.kanjianki.presentation.CopySearchButton
import dev.bee.kanjianki.presentation.DetailAccent
import dev.bee.kanjianki.presentation.DetailActions
import dev.bee.kanjianki.presentation.DetailBadge
import dev.bee.kanjianki.presentation.DetailIdentity
import dev.bee.kanjianki.presentation.DetailMissing
import dev.bee.kanjianki.presentation.DetailPanel
import dev.bee.kanjianki.presentation.ExampleCard
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniActionButton
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KanjiDetail
import dev.bee.kanjianki.presentation.MnemonicEditor
import dev.bee.kanjianki.presentation.NeighborPanel
import dev.bee.kanjianki.presentation.NeighborRow
import dev.bee.kanjianki.presentation.RecoveryTimeline
import dev.bee.kanjianki.presentation.TimelineEvent
import dev.bee.kanjianki.presentation.UiText

/**
 * Turns a loaded [HomeKanjiDetailSnapshot] into the portable [KanjiDetail].
 *
 * Every derivation here is Android's `MainActivityHomeBrowseDetail`, reached through
 * the same `:core` copy and policies it called — `HomeTextCopy`, `TimelineCopy`,
 * `KanjiNeighborPanelPolicy`, `StuckCardPolicy`, `DateTextPolicy`, `StudyTextCopy`.
 * The claim is the shared call path: both hosts render one kanji's detail from the
 * same functions, so the checkable form of "the two hosts agree" is that they call
 * the same code. What Android did *around* the model — `Runnable`s, packed ARGB, the
 * clipboard write, the toast — is a [KaniAction] here, dispatched by the shell.
 *
 * Two Android surfaces are deliberately absent, neither an oversight. The
 * stroke-order diagram needs the KanjiVG guide, which desktop has no provider for
 * until the Goal 183 asset binaries are wired — [KanjiDetail.strokeOrder] stays
 * `null` and the surface renders the rest of the card. And the review button routes
 * to the plain Study destination rather than a targeted one, because a per-kanji
 * study entry point is Goal 195's to build; until then it opens Study, which is what
 * `BrowseManualReviewPolicy` gated Android's own button on.
 */
object DesktopDetailModel {
    /**
     * The detail, or a "not found" card for a kanji with no local record.
     *
     * The missing test is Android's exactly: no inventory, no row, no study item, no
     * events, and no mnemonic. A kanji the user searched but never imported is a short
     * card, not an empty detail with blank panels.
     */
    fun detail(
        kanji: String,
        snapshot: HomeKanjiDetailSnapshot,
        matureSupportThreshold: Int,
        nowMillis: Long,
    ): KanjiDetail {
        val timeline = snapshot.timeline
        val row = timeline.currentRow
        val inventory = timeline.inventoryItem
        val displayKanji = HomeTextCopy.detailDisplayKanji(kanji, row, inventory)
        val suspended = snapshot.locallySuspended
        val stuck = isStuck(timeline.currentStudyItem, suspended)

        val missing = row == null &&
            inventory == null &&
            timeline.currentStudyItem == null &&
            timeline.events.isEmpty() &&
            snapshot.mnemonic.isEmpty()

        return KanjiDetail(
            kanji = displayKanji,
            identity = identity(row, inventory, suspended, stuck),
            reason = reasonPanel(row, inventory),
            actions = actions(row, inventory, timeline.currentStudyItem, displayKanji, suspended),
            mnemonic = mnemonicEditor(displayKanji, snapshot.mnemonic, stuck),
            timeline = recoveryTimeline(timeline, row, matureSupportThreshold, nowMillis),
            neighbors = neighbors(
                displayKanji,
                snapshot.similarPairs,
                snapshot.wrongPickCounts,
                snapshot.inventory,
            ),
            localInventory = inventory?.let(::localInventoryPanel),
            examples = row?.examples?.map(::exampleCard).orEmpty(),
            missing = if (missing) {
                DetailMissing(
                    title = UiText.Literal(HomeTextCopy.kanjiNotFoundTitle()),
                    body = UiText.Literal(HomeTextCopy.kanjiNotFoundBody()),
                )
            } else {
                null
            },
        )
    }

    private fun identity(
        row: RecordsImportModels.DashboardRow?,
        inventory: RecordsImportModels.KanjiInventoryItem?,
        suspended: Boolean,
        stuck: Boolean,
    ): DetailIdentity {
        val title = if (row == null) {
            HomeTextCopy.inventoryTitle(inventory)
        } else {
            StudyTextCopy.rowMeaning(row)
        }
        val reading = if (row == null) inventory?.readings.orEmpty() else row.reading
        val badges = buildList {
            if (suspended) add(DetailBadge(UiText.Literal(HomeTextCopy.suspendedChipLabel())))
            // Goal 68's "stuck" chip: a card that keeps failing at its demotion floor,
            // where a mnemonic is the suggested next move.
            if (stuck) add(DetailBadge(UiText.Literal(HomeTextCopy.stuckChipLabel())))
        }
        return DetailIdentity(
            title = UiText.Literal(title),
            reading = UiText.Literal(reading),
            badges = badges,
        )
    }

    /**
     * True on the same evidence Android's `isStuck` used.
     *
     * `null` ladder settings and the default demotion streak, matching the Android
     * call — the per-item availability comes from the study item itself.
     */
    private fun isStuck(item: RecordsStudyModels.StudyItem?, suspended: Boolean): Boolean =
        !suspended && item != null && StuckCardPolicy.isStuck(
            item.state,
            item.rung,
            item.phase,
            item.realAgainStreak,
            item.rungAvailability(),
            null,
            RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK,
        )

    /**
     * The reason band: why this kanji is here, plus its Anki search when there is one.
     *
     * A `null` row is a historical recovery item, an active one carries its own reason
     * text — the branch Android's `detailReasonPanelModel` took. The band is emphasized
     * (Android's `BAND` style); the info accent is the theme's blue.
     */
    private fun reasonPanel(
        row: RecordsImportModels.DashboardRow?,
        inventory: RecordsImportModels.KanjiInventoryItem?,
    ): DetailPanel {
        val lines = buildList {
            if (row == null) {
                add(UiText.Literal(HomeTextCopy.historicalReasonText()))
                if (inventory != null && inventory.browserSearch.isNotEmpty()) {
                    add(UiText.Literal(HomeTextCopy.ankiBrowserLine(StudyTextCopy.compact(inventory.browserSearch, SEARCH_MAX_CHARS))))
                }
            } else {
                add(UiText.Literal(HomeTextCopy.activeReasonText(row)))
                if (row.browserSearch.isNotEmpty()) {
                    add(UiText.Literal(HomeTextCopy.ankiBrowserLine(StudyTextCopy.compact(row.browserSearch, SEARCH_MAX_CHARS))))
                }
            }
        }
        return DetailPanel(
            title = UiText.Literal(HomeTextCopy.detailReasonTitle()),
            lines = lines,
            accent = DetailAccent.INFO,
            emphasized = true,
        )
    }

    /**
     * Review, copy-search, and suspend.
     *
     * Review is offered only for an active, non-retired, unsuspended card — the
     * `BrowseManualReviewPolicy.shouldOfferReview` gate, re-expressed here because that
     * policy is `:app`-internal. It opens the plain Study destination; a per-kanji
     * entry point is Goal 195's. Copy-search appears only when there is a search to
     * copy. Suspend is always offered, its label reflecting the current state.
     */
    private fun actions(
        row: RecordsImportModels.DashboardRow?,
        inventory: RecordsImportModels.KanjiInventoryItem?,
        item: RecordsStudyModels.StudyItem?,
        displayKanji: String,
        suspended: Boolean,
    ): DetailActions {
        val reviewEligible = row != null &&
            !suspended &&
            item?.state != StudyLadderRules.STATE_RETIRED
        val search = HomeTextCopy.detailBrowserSearch(row, inventory)
        return DetailActions(
            review = if (reviewEligible) {
                KaniActionButton(
                    label = UiText.Literal(HomeTextCopy.reviewNowLabel()),
                    action = KaniAction.Navigation.Open(KaniDestination.Study),
                )
            } else {
                null
            },
            copySearch = if (search.isEmpty()) {
                null
            } else {
                CopySearchButton(
                    label = UiText.Literal(HomeTextCopy.copyAnkiSearchLabel()),
                    copiedLabel = UiText.Literal(HomeTextCopy.ankiSearchCopiedToast()),
                    search = search,
                )
            },
            suspend = KaniActionButton(
                label = UiText.Literal(HomeTextCopy.localSuspendButtonLabel(suspended)),
                // A local suspension is a study toggle, not a collection write: marking
                // the kanji unstudied is what suspends it, so this is the same
                // `Browse.SetStudied` the checkbox dispatches. `studied = suspended`
                // inverts the current state.
                action = KaniAction.Browse.SetStudied(kanji = displayKanji, studied = suspended),
            ),
        )
    }

    private fun mnemonicEditor(
        displayKanji: String,
        initial: String,
        stuck: Boolean,
    ): MnemonicEditor = MnemonicEditor(
        kanji = displayKanji,
        title = UiText.Literal(HomeTextCopy.mnemonicNoteTitle()),
        fieldLabel = UiText.Literal(HomeTextCopy.mnemonicNoteFieldLabel()),
        helper = UiText.Literal(HomeTextCopy.mnemonicNoteHelper(stuck)),
        saveLabel = UiText.Literal(HomeTextCopy.saveMnemonicNoteLabel()),
        initial = initial,
    )

    /** The "confused with" panel, or `null` when the policy finds no neighbours. */
    private fun neighbors(
        kanji: String,
        pairs: List<RecordsImportModels.SimilarKanjiPair>,
        wrongPicks: Map<String, Map<String, Int>>,
        inventory: List<RecordsImportModels.KanjiInventoryItem>,
    ): NeighborPanel? {
        if (pairs.isEmpty()) return null
        val meanings = inventory.associate { it.kanji to it.primaryMeaning }
        val rows = KanjiNeighborPanelPolicy.build(kanji, pairs, wrongPicks, meanings)
        if (rows.isEmpty()) return null
        return NeighborPanel(
            title = UiText.Literal(HomeTextCopy.confusedWithTitle()),
            rows = rows.map { row ->
                NeighborRow(
                    kanji = row.kanji,
                    meaning = UiText.Literal(row.meaning),
                    evidence = UiText.Literal(
                        HomeTextCopy.confusedWithEvidence(row.youPickedCount, row.itStoleCount).orEmpty(),
                    ),
                )
            },
        )
    }

    private fun localInventoryPanel(
        inventory: RecordsImportModels.KanjiInventoryItem,
    ): DetailPanel {
        val lines = buildList {
            add(UiText.Literal(HomeTextCopy.localInventorySummary(inventory.sourceCount, inventory.exampleCount)))
            if (inventory.browserSearch.isNotEmpty()) {
                add(UiText.Literal(HomeTextCopy.localInventorySearchLine(StudyTextCopy.compact(inventory.browserSearch, SEARCH_MAX_CHARS))))
            }
            if (inventory.lastSeenAtMillis > 0L) {
                add(UiText.Literal(HomeTextCopy.localInventoryLastSeenLine(inventory.lastSeenAtMillis)))
            }
        }
        return DetailPanel(
            title = UiText.Literal(HomeTextCopy.localInventoryTitle()),
            lines = lines,
            accent = DetailAccent.POSITIVE,
            emphasized = false,
        )
    }

    private fun recoveryTimeline(
        timeline: RecordsStudyModels.KanjiRecoveryTimeline,
        row: RecordsImportModels.DashboardRow?,
        matureSupportThreshold: Int,
        nowMillis: Long,
    ): RecoveryTimeline = RecoveryTimeline(
        title = UiText.Literal(HomeTextCopy.recoveryTimelineTitle()),
        status = UiText.Literal(TimelineCopy.statusText(timeline, nowMillis)),
        statusAccent = accent(TimelineCopy.statusTone(timeline, nowMillis)),
        support = UiText.Literal(
            if (row != null) {
                HomeTextCopy.matureSupportTargetText(row.matureSupportCount, matureSupportThreshold)
            } else {
                HomeTextCopy.noActiveEvidenceText()
            },
        ),
        events = timeline.events.map { event ->
            TimelineEvent(
                date = UiText.Literal(DateTextPolicy.timelineDate(event.occurredAtMillis)),
                title = UiText.Literal(event.title),
                detail = UiText.Literal(event.detail),
                source = UiText.Literal(TimelineCopy.sourceLine(event)),
                accent = accent(TimelineCopy.eventTone(event.eventType)),
            )
        },
        empty = if (timeline.events.isEmpty()) {
            UiText.Literal(HomeTextCopy.timelineEmptyText())
        } else {
            UiText.EMPTY
        },
    )

    private fun exampleCard(example: RecordsImportModels.Example): ExampleCard = ExampleCard(
        source = UiText.Literal(HomeTextCopy.exampleSourceLabel(example)),
        expression = UiText.Literal(HomeTextCopy.exampleExpressionLine(example)),
        sentence = UiText.Literal(example.sentence),
        meaning = UiText.Literal(HomeTextCopy.exampleMeaningLine(example)),
        accent = if (example.sourceType == RecordsBase.SOURCE_SUSPENDED) {
            DetailAccent.WARNING
        } else {
            DetailAccent.POSITIVE
        },
    )

    /**
     * `:core`'s timeline tone as a portable accent.
     *
     * Positive stays positive, warning stays warning, and neutral folds to info —
     * `TimelineCopy.Tone.NEUTRAL` was Android's blue, which [DetailAccent.INFO] is.
     */
    private fun accent(tone: TimelineCopy.Tone): DetailAccent = when (tone) {
        TimelineCopy.Tone.POSITIVE -> DetailAccent.POSITIVE
        TimelineCopy.Tone.WARNING -> DetailAccent.WARNING
        TimelineCopy.Tone.NEUTRAL -> DetailAccent.INFO
    }

    /** Matches Android's `StudyTextCopy.compact(..., 96)` truncation of Anki searches. */
    private const val SEARCH_MAX_CHARS = 96
}
