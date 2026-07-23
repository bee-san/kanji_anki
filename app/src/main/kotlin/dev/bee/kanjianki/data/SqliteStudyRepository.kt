package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels

internal class SqliteStudyRepository(
    private val store: LocalStore,
) : StudyRepository {
    override suspend fun loadQueue(nowMillis: Long) = safeStoreCall {
        store.readSnapshot {
            val rows = store.activeStudyDashboardRows()
            val dayStart = LocalDayPolicy.localDayStart(nowMillis)
            StudyQueueSnapshot(
                activeRows = rows.toList(),
                studyItems = if (rows.isEmpty()) {
                    emptyList()
                } else {
                    store.studyItemsForKanji(rows.map { it.kanji }).toList()
                },
                locallySuspendedKanji = store.locallySuspendedKanji().toSet(),
                latestSuccessfulSyncAtMillis = store.latestSuccessfulSyncFinishedAt(),
                studyLadder = store.studyLadderSettings(),
                schedulerParameters = store.schedulerParameters(),
                schedulerFsrsWeights = store.schedulerFsrsWeights()?.toList(),
                learningSteps = store.learningStepSettings(),
                adaptiveWorkload = AdaptiveWorkloadSnapshot(
                    store.adaptiveLoadWorkPercent(),
                    store.adaptiveLoadMaxItems(),
                    store.adaptiveLoadMode(),
                ),
                studyAheadMinutes = store.studyAheadMinutes(),
                studyStreak = store.studyStreak(nowMillis).toRepositorySnapshot(),
                recentReviewStats = store.reviewStatsSince(nowMillis - RECENT_REVIEW_WINDOW_MILLIS),
                studiedKanjiToday = store.studiedKanjiSince(dayStart).toSet(),
                dueLegacyWritingRepairs = store.dueSimilarWritingRepairs(nowMillis).toList(),
            )
        }
    }

    override suspend fun loadItems(kanji: Collection<String>) = safeStoreCall {
        store.studyItemsForKanji(kanji).toList()
    }

    override suspend fun replaceQueue(command: StudyQueueWriteCommand) = safeStoreCall {
        store.replaceStudyItems(
            command.items,
            null,
            0L,
            null,
            command.baseline,
        )
    }

    override suspend fun annotateCapabilities(items: List<RecordsStudyModels.StudyItem>) =
        safeStoreCall {
            store.annotateSimilarKanjiAvailability(items).toList()
        }

    override suspend fun commitReview(command: ReviewCommitCommand) = safeStoreCall {
        store.commitReview(command)
    }

    override suspend fun undoLastReview(snapshot: AppliedReviewSnapshot) = safeStoreCall {
        store.undoLastAppliedReview(snapshot)
    }

    override suspend fun reviewTokenStatus(query: ReviewTokenQuery) = safeStoreCall {
        store.readSnapshot {
            ReviewTokenStatus(
                consumed = store.hasConsumedToken(query.token),
                matchesReview = store.hasMatchingConsumedReview(
                    query.token,
                    query.kanji,
                    query.taskType,
                    query.answerSignature,
                ),
            )
        }
    }

    override suspend fun recoveryStatus(query: StudyRecoveryQuery) = safeStoreCall {
        store.readSnapshot {
            val token = ReviewTokenStatus(
                consumed = store.hasConsumedToken(query.review.token),
                matchesReview = store.hasMatchingConsumedReview(
                    query.review.token,
                    query.review.kanji,
                    query.review.taskType,
                    query.review.answerSignature,
                ),
            )
            val repairFinished = query.repairId?.let {
                store.hasFinishedSimilarWritingRepairAttempt(
                    it,
                    query.review.token,
                    query.repairAttemptsBefore,
                    query.repairPassed,
                )
            } ?: false
            StudyRecoveryStatus(token, repairFinished)
        }
    }

    override suspend fun loadChoiceData(kanji: String, nowMillis: Long) = safeStoreCall {
        store.readSnapshot {
            StudyChoiceDataSnapshot(
                kanjiReadingUsages = store.kanjiReadingUsagesFor(kanji).toList(),
                kanjiReadingPool = store.kanjiReadingPoolFor(kanji).toList(),
                readingKanjiUsages = store.kanjiReadingUsagesForReadingKanji(kanji).toList(),
                readingKanjiCandidates = store.readingKanjiCandidatesFor(kanji)
                    .mapValues { (_, candidates) -> candidates.toList() },
                activeRows = store.activeDashboardRows().toList(),
                inventory = store.searchKanjiInventory("", false).toList(),
                similarPairs = store.similarPairsForKanji(kanji).toList(),
                wrongPickCounts = store.choiceWrongPickCounts(nowMillis)
                    .mapValues { (_, counts) -> counts.toMap() },
            )
        }
    }

    override suspend fun loadDueSimilarChoice(targetKanji: String, nowMillis: Long) = safeStoreCall {
        store.dueSimilarChoiceForActiveTarget(targetKanji, nowMillis)
    }

    override suspend fun loadDueLegacyWritingRepairs(nowMillis: Long) = safeStoreCall {
        store.dueSimilarWritingRepairs(nowMillis).toList()
    }

    override suspend fun saveLegacyWritingRepair(
        repair: RecordsImportModels.SimilarKanjiWritingRepair,
    ) = safeStoreCall {
        store.saveSimilarWritingRepair(repair)
    }

    override suspend fun finishLegacyWritingRepair(command: FinishLegacyRepairCommand) = safeStoreCall {
        store.finishSimilarWritingRepair(
            command.repairId,
            command.token,
            command.passed,
            command.finishedAtMillis,
        )
    }

    override suspend fun skipLegacyWritingRepair(command: SkipLegacyRepairCommand) = safeStoreCall {
        store.skipSimilarWritingRepair(
            command.repairId,
            command.token,
            command.skippedAtMillis,
        )
    }

    override suspend fun loadMnemonic(kanji: String) = safeStoreCall {
        store.kanjiMnemonicNote(kanji)
    }

    private companion object {
        const val RECENT_REVIEW_WINDOW_MILLIS = 7L * 86_400_000L
    }
}
