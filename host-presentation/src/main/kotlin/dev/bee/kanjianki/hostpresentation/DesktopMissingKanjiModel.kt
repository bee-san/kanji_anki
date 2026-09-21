package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiReport
import dev.bee.kanjianki.core.MissingKanjiTextCopy
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.MissingKanjiContent
import dev.bee.kanjianki.presentation.MissingKanjiDestinations
import dev.bee.kanjianki.presentation.MissingKanjiProvider
import dev.bee.kanjianki.presentation.MissingKanjiRow
import dev.bee.kanjianki.presentation.MissingKanjiScreen

/**
 * Maps a Missing Kanji scan to the portable [MissingKanjiScreen] both hosts render.
 *
 * Host-neutral like the other five mappers here, and named `Desktop*` for consistency
 * with them rather than because it is desktop-only: nothing in it knows about AWT, a
 * `Context`, or a provider. Every string comes from `:core`'s
 * [MissingKanjiTextCopy], so the two hosts cannot drift into describing the same
 * report differently.
 *
 * The state machine is the load-bearing part. "No dictionary" and "no missing kanji"
 * must not render the same way — one means Kani cannot check the collection, the
 * other means it checked and found nothing to add — and a screen that collapsed them
 * would tell a user their collection is complete when it was never examined.
 */
object DesktopMissingKanjiModel {
    /**
     * The screen for a host that has not scanned yet.
     *
     * [dictionaryAvailable] is separate from [provider] on purpose. A reachable
     * collection with no dictionary can still be *scanned* — the observed kanji come
     * from the provider — but the report has nothing to compare them against, so the
     * honest state is unavailable rather than an empty report.
     */
    fun firstRun(
        provider: MissingKanjiProvider,
        dictionaryAvailable: Boolean,
    ): MissingKanjiScreen = MissingKanjiScreen(
        content = when {
            provider == MissingKanjiProvider.NOT_INSTALLED -> MissingKanjiContent.ProviderMissing
            provider == MissingKanjiProvider.PERMISSION_REQUIRED ->
                MissingKanjiContent.PermissionRequired
            provider == MissingKanjiProvider.UNAVAILABLE -> MissingKanjiContent.ProviderMissing
            // Reference assets absent: reported as its own failure code rather than as
            // FirstRun, so the surface can say why scanning would be pointless instead
            // of inviting a scan that can only produce an empty report.
            !dictionaryAvailable -> MissingKanjiContent.Error(DICTIONARY_UNAVAILABLE)
            else -> MissingKanjiContent.FirstRun
        },
        providerAvailability = provider,
        primaryActionLabel = MissingKanjiTextCopy.actionLabel(),
        primaryAction = KaniAction.MissingKanji.ScanIntent,
        destinations = MissingKanjiDestinations(),
    )

    /** The screen while a scan runs. */
    fun scanning(
        provider: MissingKanjiProvider,
        notesScanned: Int,
        uniqueKanji: Int,
        skippedNotes: Int,
        cancelling: Boolean,
    ): MissingKanjiScreen = MissingKanjiScreen(
        content = MissingKanjiContent.Scanning(
            // Coerced, not trusted: a provider that reports a negative or decreasing
            // count would otherwise render a progress line that counts backwards.
            notesScanned = notesScanned.coerceAtLeast(0),
            uniqueKanji = uniqueKanji.coerceAtLeast(0),
            skippedNotes = skippedNotes.coerceAtLeast(0),
            cancelling = cancelling,
        ),
        providerAvailability = provider,
        primaryActionLabel = if (cancelling) {
            MissingKanjiTextCopy.cancellingLabel()
        } else {
            MissingKanjiTextCopy.actionLabel()
        },
        primaryAction = KaniAction.MissingKanji.CancelScan,
        // No destinations mid-scan: the candidate set is still changing, and a batch
        // add against a partial report would admit kanji the finished scan excludes.
        destinations = MissingKanjiDestinations(operationInProgress = true),
    )

    /** The screen for a failed scan or an unreadable source. */
    fun failed(
        provider: MissingKanjiProvider,
        failureCode: String,
    ): MissingKanjiScreen = MissingKanjiScreen(
        content = MissingKanjiContent.Error(failureCode),
        providerAvailability = provider,
        primaryActionLabel = MissingKanjiTextCopy.actionLabel(),
        primaryAction = KaniAction.MissingKanji.ScanIntent,
        destinations = MissingKanjiDestinations(),
    )

    /**
     * The screen for a finished scan.
     *
     * @param admittedKanji kanji already in Kani's queue, which the report marks and
     *   offers to remove rather than offering to add again.
     * @param canCreateAnkiNotes whether the provider accepted the additive-note
     *   capability gate. False leaves CSV as the fallback, which is always available.
     */
    fun report(
        provider: MissingKanjiProvider,
        report: MissingKanjiReport,
        admittedKanji: Set<String> = emptySet(),
        canCreateAnkiNotes: Boolean = false,
        defaultDeckName: String = "",
        staleReason: String? = null,
        operationInProgress: Boolean = false,
    ): MissingKanjiScreen {
        val rows = report.missing.map { candidate -> row(candidate, admittedKanji) }
        val selectable = rows.count { !it.inKani }
        return MissingKanjiScreen(
            content = MissingKanjiContent.Report(
                summaryLine = MissingKanjiTextCopy.uniqueAnkiMetric(
                    report.uniqueObservedKanjiCount,
                ),
                missingCountLine = MissingKanjiTextCopy.missingMetric(report.missingKanjiCount),
                staleLine = staleReason?.let(MissingKanjiTextCopy::staleResultsLabel),
                rows = rows,
            ),
            providerAvailability = provider,
            primaryActionLabel = MissingKanjiTextCopy.actionLabel(),
            primaryAction = KaniAction.MissingKanji.ScanIntent,
            destinations = MissingKanjiDestinations(
                // Local admission needs nothing from the provider, so it is offered
                // whenever there is something to admit.
                addToKaniEnabled = selectable > 0 && !operationInProgress,
                // Capability-gated: a provider that cannot accept notes must not show a
                // button that will fail, and Kani's only permitted non-tag write is this
                // additive one behind that gate.
                createAnkiEnabled = canCreateAnkiNotes && selectable > 0 && !operationInProgress,
                // Always available, and deliberately so: CSV is the complete fallback
                // for every provider that refuses writes.
                csvExportEnabled = selectable > 0 && !operationInProgress,
                defaultDeckName = defaultDeckName,
                operationInProgress = operationInProgress,
            ),
        )
    }

    private fun row(
        candidate: MissingKanjiCandidate,
        admittedKanji: Set<String>,
    ): MissingKanjiRow {
        val admitted = candidate.literal in admittedKanji
        return MissingKanjiRow(
            literal = candidate.literal,
            meaning = candidate.primaryMeaning,
            reading = candidate.primaryReading,
            // An unranked kanji gets the explicit unranked label rather than a blank
            // line: blank reads as missing data, and unranked is a real answer that
            // the documented sort-last behaviour depends on.
            rankLine = candidate.jitenRank
                ?.let { rank -> MissingKanjiTextCopy.topPresetLabel(rank) }
                ?: MissingKanjiTextCopy.includeUnrankedLabel(),
            inKani = admitted,
            canRemove = admitted,
        )
    }

    /**
     * The failure code for a profile with no dictionary.
     *
     * A distinct code rather than reusing a scan failure, because the remedy differs:
     * a scan failure is worth retrying, and this one is not fixed by trying again.
     */
    const val DICTIONARY_UNAVAILABLE: String = "dictionary_unavailable"
}
