package dev.bee.kanjianki.host

import android.content.Context
import dev.bee.kanjianki.application.SyncUseCases
import dev.bee.kanjianki.core.SyncProgressCopy
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.hostpresentation.HostSyncEngine
import dev.bee.kanjianki.hostpresentation.SyncRunProgress
import dev.bee.kanjianki.hostpresentation.SyncRunResult
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.sync.ManualSyncEngine
import dev.bee.kanjianki.sync.SyncCancellation
import dev.bee.kanjianki.sync.SyncProgress
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.sync.createManualSyncEngine

/**
 * Binds Android's `ManualSyncEngine` to the shared [HostSyncEngine] port.
 *
 * The whole reason this file is separate from [HostSyncEngine]: constructing an engine
 * needs a `Context` (asset readers), an `AlarmManager` re-arm, and a widget refresh, none
 * of which the shared graph can name. What crosses the boundary is only a run and its
 * outcome.
 *
 * The engine is built *per run*, not once, and that is deliberate — it reads a
 * [SettingsSnapshot] and a gateway at construction time, so a reused instance would sync
 * against whatever the settings were when the host started rather than what they are now.
 */
internal object AndroidSyncEngineAdapter {
    /**
     * A [HostSyncEngine] that runs one Android sync.
     *
     * [repairedNoteIds] is the confirmed set for the repaired-note tag write-back, and
     * both authorization parameters default to *not* authorized. CLAUDE.md makes that
     * write manual-confirm-only and forbids the automatic runner from performing it, so
     * the safe value is the default and a caller has to name the confirmed ids to enable
     * it — an omission cannot silently authorize a provider write.
     */
    fun of(
        context: Context,
        syncUseCases: SyncUseCases,
        gateway: CollectionGateway,
        settings: () -> SettingsSnapshot,
        repairedNoteIds: Set<Long>? = null,
    ): HostSyncEngine = HostSyncEngine { progress, cancelled ->
        val engine = createManualSyncEngine(
            context = context,
            syncUseCases = syncUseCases,
            gateway = gateway,
            settings = settings(),
            progress = SyncProgress.Listener { update -> progress(runProgress(update)) },
            repairedWriteBackAuthorized = repairedNoteIds != null,
            confirmedRepairedNoteIds = repairedNoteIds,
            cancellation = SyncCancellation { cancelled() },
        )
        runResult(engine.run())
    }

    /**
     * [update] as the portable projection, with the stage wording already resolved.
     *
     * Resolved here because [SyncProgressCopy] localizes off the default `Locale` and the
     * shared graph has no resources of its own. The fraction stays null until the total is
     * known: a bar that reads 100% because the total is still -1 is worse than no bar.
     */
    internal fun runProgress(update: SyncProgress): SyncRunProgress {
        val label = UiText.Literal(SyncProgressCopy.stageTitle(update.coreStage()))
        if (!update.totalKnown() || update.totalCards <= 0) return SyncRunProgress(label = label)
        return SyncRunProgress(
            label = label,
            fraction = SyncProgressCopy
                .progressPermille(update.scannedCards, update.totalCards)
                .toFloat() / PERMILLE,
        )
    }

    /**
     * [result] as the portable outcome.
     *
     * The ordering matters: `skipped` is checked before `success`, because a skipped run
     * reports neither, and reading `success == false` first would turn "another sync is
     * already running" into a user-visible failure with a retry button.
     *
     * A failure's `retryable` flag decides the kind rather than a message match: the
     * engine already computed whether another attempt can help, and re-deriving it from
     * copy is how the two would drift.
     */
    internal fun runResult(result: ManualSyncEngine.SyncResult): SyncRunResult = when {
        result.skipped -> SyncRunResult.Skipped(message = UiText.Literal(result.message.orEmpty()))
        result.success -> SyncRunResult.Succeeded(importedKanji = result.importedSuspendedKanji)
        else -> SyncRunResult.Failed(
            PresentationFailure(
                kind = if (result.retryable) {
                    PresentationFailure.Kind.TRANSIENT
                } else {
                    PresentationFailure.Kind.PROVIDER_UNAVAILABLE
                },
                message = UiText.Literal(result.message.orEmpty()),
            ),
        )
    }

    private const val PERMILLE = 1_000f
}
