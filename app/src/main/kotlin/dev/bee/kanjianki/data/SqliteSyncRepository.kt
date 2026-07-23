package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.ManualKanjiAdmissionPolicy
import dev.bee.kanjianki.core.MissingKanjiTextCopy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SuspendedImportPolicy

internal class SqliteSyncRepository(
    private val store: LocalStore,
) : SyncRepository {
    override suspend fun loadStoredState() = safeStoreCall {
        store.readSnapshot {
            StoredSyncState(
                hasCollectionMirror = store.hasPersistedCollectionMirror(),
                suspendedImports = store.suspendedImports().toList(),
                unrestoredSuspendedArchiveCardIds = store.unrestoredSuspendedArchiveCardIds().toSet(),
                studyItems = store.studyItems().toList(),
                latestSuccessfulSyncAtMillis = store.latestSuccessfulSyncFinishedAt(),
            )
        }
    }

    override suspend fun publish(command: SyncPublicationCommand) = safeStoreCall {
        store.publishSyncAtomically {
            val syncId = store.saveSuccessfulSync(
                command.snapshot,
                command.imports,
                command.rows,
                command.settings,
                LocalStoreBase.SyncTiming(
                    command.timing.startedAtMillis,
                    command.timing.finishedAtMillis,
                ),
                command.removalMessage,
                command.similarIndex,
                command.auditImports,
                LocalStoreBase.STATUS_PENDING,
                command.dictionary,
            )
            val nowMillis = command.timing.finishedAtMillis
            val locallySuspended = store.locallySuspendedKanji().toSet()
            val queueRows = ManualKanjiAdmissionPolicy.mergeRows(
                providerRows = command.rows,
                candidates = store.missingKanjiStore().manualSources().map { source -> source.candidate },
                reasonText = MissingKanjiTextCopy.dictionarySourceReason(),
            )
            val activeRows = SuspendedImportPolicy.activeRows(queueRows, locallySuspended)
            val currentItems = store.studyItems().toList()
            val queuePlan = command.queuePlanner.plan(
                SyncQueuePlanningSnapshot(
                    rows = queueRows,
                    activeRows = activeRows.toList(),
                    currentItems = currentItems,
                    locallySuspendedKanji = locallySuspended,
                    settings = command.settings,
                    repairEvidenceInputs = store.kanjiRepairEvidenceInputs().toList(),
                    studyLadder = store.studyLadderSettings(),
                    schedulerParameters = store.schedulerParameters(),
                    schedulerFsrsWeights = store.schedulerFsrsWeights()?.toList(),
                    learningSteps = store.learningStepSettings(),
                    adaptiveWorkload = AdaptiveWorkloadSnapshot(
                        store.adaptiveLoadWorkPercent(),
                        store.adaptiveLoadMaxItems(),
                        store.adaptiveLoadMode(),
                    ),
                    recentReviewStats = store.reviewStatsSince(nowMillis - RECENT_REVIEW_WINDOW_MILLIS),
                    currentStudyStreakDays = store.studyStreak(nowMillis).currentDays,
                    studiedKanjiToday = store.studiedKanjiSince(
                        LocalDayPolicy.localDayStart(nowMillis),
                    ).toSet(),
                    nowMillis = nowMillis,
                ),
            )
            val annotatedItems = store.annotateSimilarKanjiAvailability(queuePlan.items)
            store.commitPendingSyncStudyItems(
                annotatedItems,
                syncId,
                nowMillis,
                command.settings,
                currentItems,
            )
            SyncPublicationResult(
                syncId = syncId,
                activeRows = activeRows.toList(),
                adaptiveLoadPlan = queuePlan.adaptiveLoadPlan,
            )
        }
    }

    override suspend fun recordFailure(command: RecordSyncFailureCommand) = safeStoreCall {
        store.saveFailedSync(
            command.startedAtMillis,
            command.finishedAtMillis,
            command.status,
            command.errorCode,
            command.errorMessage,
        )
    }

    override suspend fun updateRemovalMessage(syncId: Long, message: String?) = safeStoreCall {
        store.updateSyncRemovalMessage(syncId, message)
    }

    override suspend fun repairedWriteBackProposal(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        matureSupportThreshold: Int,
    ) = safeStoreCall {
        store.readSnapshot {
            store.repairedWriteBackProposal(snapshot, matureSupportThreshold)
        }
    }

    override suspend fun repairedWriteBackPreview(matureSupportThreshold: Int) = safeStoreCall {
        store.readSnapshot {
            store.repairedWriteBackPreview(matureSupportThreshold)
        }
    }

    override suspend fun recordRepairedWriteBack(command: RecordRepairedWriteBackCommand) = safeStoreCall {
        store.recordRepairedWriteBack(
            command.proposal,
            command.taggedNoteIds,
            command.occurredAtMillis,
            command.syncId,
        )
    }

    override suspend fun loadRepairedHandoff() = safeStoreCall {
        store.pendingRepairedHandoffKanji().toList()
    }

    override suspend fun dismissRepairedHandoff() = safeStoreCall {
        store.dismissRepairedHandoff()
    }

    private companion object {
        const val RECENT_REVIEW_WINDOW_MILLIS = 7L * 86_400_000L
    }
}
