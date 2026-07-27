package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.RepairedWriteBackPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryPayloadsTest {
    @Test
    fun homeAndStudyPayloadsRetainPortableValues() {
        val syncStatus = SyncStatusSnapshot("success", 3, 4, 5, 2, 1_000L, "", "")
        val streak = StudyStreakSnapshot(2, 4, true, 3, 900L)
        val workload = AdaptiveWorkloadSnapshot(80, 25, "focus")
        val item = studyItem()
        val timeline = RecordsStudyModels.KanjiRecoveryTimeline(null, item, emptyList())
        val home = HomeSnapshot(
            activeRows = emptyList(),
            studyItems = listOf(item),
            locallySuspendedKanji = setOf("休"),
            latestSync = syncStatus,
            latestSuccessfulSyncAtMillis = 1_000L,
            studyStreak = streak,
            dueLegacyWritingRepairs = emptyList(),
            repairedHandoffKanji = listOf("直"),
            consecutiveFailedSyncs = 2,
        )
        val detail = HomeKanjiDetailSnapshot(
            kanji = "休",
            dashboardRow = null,
            inventoryItem = null,
            timeline = timeline,
            mnemonic = "person by a tree",
            similarPairs = emptyList(),
            wrongPickCounts = emptyMap(),
            inventory = emptyList(),
            locallySuspended = true,
        )
        val saveMnemonic = SaveMnemonicCommand("休", "person by a tree", 1_100L)
        val suspend = SetLocalSuspensionCommand(listOf("休"), true, 1_200L)

        val queue = StudyQueueSnapshot(
            activeRows = emptyList(),
            studyItems = listOf(item),
            locallySuspendedKanji = emptySet(),
            latestSuccessfulSyncAtMillis = 1_000L,
            studyLadder = ladder,
            schedulerParameters = parameters,
            schedulerFsrsWeights = null,
            learningSteps = learningSteps,
            adaptiveWorkload = workload,
            studyAheadMinutes = 15,
            studyStreak = streak,
            recentReviewStats = reviewStats,
            studiedKanjiToday = setOf("休"),
            dueLegacyWritingRepairs = emptyList(),
        )
        val queueWrite = StudyQueueWriteCommand(listOf(item), baseline = emptyList())
        val tokenQuery = ReviewTokenQuery("token", "休", "kanji_meaning", "signature")
        val tokenStatus = ReviewTokenStatus(consumed = true, matchesReview = true)
        val recoveryQuery = StudyRecoveryQuery(
            review = tokenQuery,
            repairId = 4L,
            repairAttemptsBefore = 1,
            repairPassed = true,
        )
        val recoveryStatus = StudyRecoveryStatus(tokenStatus, legacyRepairFinished = true)
        val choiceData = StudyChoiceDataSnapshot(
            kanjiReadingUsages = emptyList(),
            kanjiReadingPool = emptyList(),
            readingKanjiUsages = emptyList(),
            readingKanjiCandidates = emptyMap(),
            activeRows = emptyList(),
            inventory = emptyList(),
            similarPairs = emptyList(),
            wrongPickCounts = emptyMap(),
        )
        val finishRepair = FinishLegacyRepairCommand(4L, "repair-token", true, 1_300L)
        val skipRepair = SkipLegacyRepairCommand(5L, "skip-token", 1_400L)

        assertEquals("success", home.latestSync?.status)
        assertEquals(2, home.consecutiveFailedSyncs)
        assertEquals("休", detail.kanji)
        assertEquals("person by a tree", saveMnemonic.note)
        assertTrue(suspend.suspended)
        assertEquals(80, queue.adaptiveWorkload.workPercent)
        assertEquals(emptyList<RecordsStudyModels.StudyItem>(), queueWrite.baseline)
        assertEquals("signature", recoveryQuery.review.answerSignature)
        assertTrue(recoveryStatus.legacyRepairFinished)
        assertTrue(choiceData.inventory.isEmpty())
        assertTrue(finishRepair.passed)
        assertEquals("skip-token", skipRepair.token)
    }

    @Test
    fun statsPayloadsExposeLookupAndTimingBehavior() {
        val taskTime = StudyTaskTimeSnapshot(600L, 1_200L, 3)
        val ladderHealth = LadderHealthSnapshot(
            rungCounts = mapOf(RecordsBase.LadderRung.KANJI_MEANING to 2),
            totalActiveItems = 2,
            realDueReviewsToMove = 4,
            ladderPromotionIntervalDays = 21,
            ladderDemotionFailStreak = 3,
            promotionReadyCount = 1,
            demotionRiskCount = 1,
            demotionReadyCount = 0,
            stuckCount = 0,
        )
        val adaptiveHealth = AdaptiveHealthSnapshot(
            coreCounts = mapOf(CoreSkill.RECOGNITION to 2),
            activeRepairsByTask = mapOf("write_kanji" to 1),
            activeRepairsByFailure = mapOf(FailureKind.WRITING_SHAPE to 1),
            totalAdaptiveItems = 2,
            contextualCompleteCount = 1,
            activeRepairCount = 1,
            revalidationPendingCount = 1,
            recentCoreMissCount = 1,
            escalationRiskCount = 0,
            stuckRepairCount = 0,
            malformedStateCount = 0,
        )
        val weak = WeakKanjiImprovedSnapshot(
            improvedCount = 1,
            averageBeforeWeakness = 80.0,
            averageAfterWeakness = 40.0,
            examples = listOf(KanjiImprovementSnapshot("弱", 80.0, 40.0)),
        )
        val support = MatureSupportGainedSnapshot(
            gainedSupportCount = 1,
            matureSupportGained = 2,
            firstSupportCount = 1,
            examples = listOf(KanjiSupportGainSnapshot("漢", 0, 2)),
        )
        val outcome = KaniOutcomeSnapshot(weak, support, ladderHealth, adaptiveHealth)
        val repairEvidence = KanjiRepairEvidenceSnapshot(
            kanji = "弱",
            status = KanjiRepairEvidencePolicy.Status.IMPROVING,
            reason = "improving",
            explanation = "Weakness decreased.",
            beforeWeakness = 80,
            afterWeakness = 40,
            beforeMatureSupport = 0,
            afterMatureSupport = 2,
            kaniReviews = 4,
            writingFailures = 0,
            lastMistakeAtMillis = 800L,
            lastSyncAtMillis = 1_000L,
            confidence = 0.9,
            confidenceReason = "Enough evidence.",
        )
        val snapshot = StatsSnapshot(
            outcomeStats = outcome,
            impactReport = KanjiImpactAnalyzer.Report(1, 0, 0, emptyList()),
            generatedAtMillis = 1_000L,
            sourceVersion = 2L,
            studyImpactStats = StudyImpactSnapshot(5, 2, 1, 1, 0, 1),
            recentMistakes = listOf(RecentMistakeSnapshot("弱", "again", 800L)),
            studyStreak = StudyStreakSnapshot(2, 4, true, 3, 900L),
            studyTaskTimeStats = taskTime,
            cacheFormatVersion = 11,
            reviewDaySummaries = listOf(ReviewDaySummarySnapshot(0L, 5, 1, 1, 2, 1, 1, 0)),
            kanjiRepairEvidence = listOf(repairEvidence),
            taskTypeDaySummaries = listOf(TaskTypeDaySummarySnapshot(0L, "kanji_meaning", 4, 5)),
            cumulativeKanjiPracticed = listOf(CumulativeKanjiSnapshot(0L, 12)),
            wrongPickCounts = mapOf("弱" to mapOf("弓" to 2)),
            confusionMeanings = mapOf("弓" to "bow"),
            ladderForecast = null,
        )

        assertEquals(400L, taskTime.averageMillisPerTask())
        assertEquals(2, ladderHealth.countFor(RecordsBase.LadderRung.KANJI_MEANING))
        assertEquals(0, ladderHealth.countFor(null))
        assertEquals(2, adaptiveHealth.countFor(CoreSkill.RECOGNITION))
        assertEquals(0, adaptiveHealth.countFor(null))
        assertEquals(1, adaptiveHealth.repairCountFor("write_kanji"))
        assertEquals(0, adaptiveHealth.repairCountFor(null))
        assertEquals(1, adaptiveHealth.failureCountFor(FailureKind.WRITING_SHAPE))
        assertEquals(0, adaptiveHealth.failureCountFor(null))
        assertEquals(5, snapshot.reviewDaySummaries.single().total)
        assertEquals(4, snapshot.taskTypeDaySummaries.single().correct)
        assertEquals(12, snapshot.cumulativeKanjiPracticed.single().cumulativeCount)
        assertEquals("improving", snapshot.kanjiRepairEvidence.single().reason)
        assertNull(snapshot.ladderForecast)
    }

    @Test
    fun settingsPayloadsRepresentEveryPortableWrite() {
        val workload = AdaptiveWorkloadSnapshot(80, 25, "focus")
        val commands: List<SettingsSaveCommand> = listOf(
            SettingsSaveCommand.Sync(syncSettings, tagRepairedCards = true),
            SettingsSaveCommand.AdaptiveWorkload(workload),
            SettingsSaveCommand.StudyAhead(15),
            SettingsSaveCommand.StudyLadder(ladder),
            SettingsSaveCommand.NewCardSort("balanced_priority"),
            SettingsSaveCommand.Theme(KaniThemeChoice.DARK),
            SettingsSaveCommand.SchedulerParameters(parameters),
            SettingsSaveCommand.SchedulerFsrsWeights(listOf(0.1, 0.2)),
            SettingsSaveCommand.FsrsPersonalizationEnabled(true),
            SettingsSaveCommand.FsrsFitSummary("{\"status\":\"adopted\"}"),
            SettingsSaveCommand.ResetFsrsPersonalization,
            SettingsSaveCommand.LearningSteps(learningSteps),
            SettingsSaveCommand.NoteTypeFields(
                "Kiku",
                "Expression",
                "Reading",
                "Meaning",
                "Sentence",
                "Frequency",
                "FrequencySort",
            ),
            SettingsSaveCommand.ImportFilters(
                activeCards = true,
                suspendedCards = false,
                taggedCards = true,
                tags = "focus",
                weakCards = true,
                weakDifficulty = 8.0,
                weakLapses = 4,
                minMatchingCards = 2,
                browserQueryCards = true,
                browserQuery = "tag:focus",
                tagRepairedCards = true,
            ),
            SettingsSaveCommand.FrequencyRange(100, 2_000),
            SettingsSaveCommand.DeckLimits(12, 40),
            SettingsSaveCommand.LadderThresholds(30, 4),
        )
        val snapshot = SettingsSnapshot(
            sync = syncSettings,
            tagRepairedCards = true,
            adaptiveWorkload = workload,
            studyAheadMinutes = 15,
            studyLadder = ladder,
            schedulerParameters = parameters,
            schedulerFsrsWeights = null,
            learningSteps = learningSteps,
            themeChoice = KaniThemeChoice.DARK,
            fsrsPersonalizationEnabled = true,
            fsrsFitSummaryJson = "{\"status\":\"adopted\"}",
        )
        val fit = CommitFsrsFitCommand(
            weightsToAdopt = listOf(0.1, 0.2),
            summaryJson = "{\"status\":\"adopted\"}",
            disabledSummaryJson = null,
            preserveExistingWeights = false,
        )

        assertEquals(17, commands.size)
        assertTrue((commands[0] as SettingsSaveCommand.Sync).tagRepairedCards)
        assertEquals(80, (commands[1] as SettingsSaveCommand.AdaptiveWorkload).value.workPercent)
        assertEquals(15, (commands[2] as SettingsSaveCommand.StudyAhead).minutes)
        assertSame(ladder, (commands[3] as SettingsSaveCommand.StudyLadder).value)
        assertEquals("balanced_priority", (commands[4] as SettingsSaveCommand.NewCardSort).mode)
        assertEquals(KaniThemeChoice.DARK, (commands[5] as SettingsSaveCommand.Theme).choice)
        assertSame(parameters, (commands[6] as SettingsSaveCommand.SchedulerParameters).value)
        assertEquals(listOf(0.1, 0.2), (commands[7] as SettingsSaveCommand.SchedulerFsrsWeights).weights)
        assertTrue((commands[8] as SettingsSaveCommand.FsrsPersonalizationEnabled).enabled)
        assertEquals("{\"status\":\"adopted\"}", (commands[9] as SettingsSaveCommand.FsrsFitSummary).summaryJson)
        assertSame(SettingsSaveCommand.ResetFsrsPersonalization, commands[10])
        assertSame(learningSteps, (commands[11] as SettingsSaveCommand.LearningSteps).value)
        assertEquals(
            "Expression",
            commands.filterIsInstance<SettingsSaveCommand.NoteTypeFields>().single().expressionField,
        )
        assertEquals(
            "tag:focus",
            commands.filterIsInstance<SettingsSaveCommand.ImportFilters>().single().browserQuery,
        )
        assertEquals(
            2_000,
            commands.filterIsInstance<SettingsSaveCommand.FrequencyRange>().single().maxRank,
        )
        assertEquals(
            40,
            commands.filterIsInstance<SettingsSaveCommand.DeckLimits>().single().activeQueueCap,
        )
        assertEquals(
            4,
            commands.filterIsInstance<SettingsSaveCommand.LadderThresholds>().single().demotionFailStreak,
        )
        assertEquals(KaniThemeChoice.DARK, snapshot.themeChoice)
        assertFalse(fit.preserveExistingWeights)
    }

    @Test
    fun syncPayloadsInvokeThePureQueuePlanner() {
        val plan = SyncQueuePlan(
            items = listOf(studyItem()),
            adaptiveLoadPlan = adaptiveLoadPlan,
        )
        var plannerInput: SyncQueuePlanningSnapshot? = null
        val planner = SyncQueuePlanner { snapshot ->
            plannerInput = snapshot
            plan
        }
        val planning = SyncQueuePlanningSnapshot(
            rows = emptyList(),
            activeRows = emptyList(),
            currentItems = emptyList(),
            locallySuspendedKanji = emptySet(),
            settings = syncSettings,
            repairEvidenceInputs = emptyList(),
            studyLadder = ladder,
            schedulerParameters = parameters,
            schedulerFsrsWeights = null,
            learningSteps = learningSteps,
            adaptiveWorkload = AdaptiveWorkloadSnapshot(100, 25, "all"),
            recentReviewStats = reviewStats,
            currentStudyStreakDays = 3,
            studiedKanjiToday = emptySet(),
            nowMillis = 2_000L,
        )
        val publication = SyncPublicationCommand(
            snapshot = RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            imports = emptyList(),
            auditImports = emptyList(),
            rows = emptyList(),
            settings = syncSettings,
            timing = SyncTimingSnapshot(1_000L, 2_000L),
            removalMessage = null,
            similarIndex = null,
            dictionary = null,
            queuePlanner = planner,
        )
        val state = StoredSyncState(
            hasCollectionMirror = true,
            suspendedImports = emptyList(),
            unrestoredSuspendedArchiveCardIds = emptySet(),
            studyItems = emptyList(),
            latestSuccessfulSyncAtMillis = 2_000L,
        )
        val result = SyncPublicationResult(7L, emptyList(), adaptiveLoadPlan)
        val failure = RecordSyncFailureCommand(1_000L, 2_000L, "failed", "provider", "unavailable")
        val writeBack = RecordRepairedWriteBackCommand(
            proposal = emptyProposal,
            taggedNoteIds = setOf(11L),
            occurredAtMillis = 2_000L,
            syncId = 7L,
        )

        assertSame(plan, publication.queuePlanner.plan(planning))
        assertSame(planning, plannerInput)
        assertTrue(state.hasCollectionMirror)
        assertEquals(7L, result.syncId)
        assertEquals("provider", failure.errorCode)
        assertEquals(setOf(11L), writeBack.taggedNoteIds)
    }

    private companion object {
        val ladder: RecordsBase.StudyLadderSettings = RecordsBase.StudyLadderSettings.defaults()
        val parameters: RecordsSchedulerModels.SchedulerParameters =
            RecordsSchedulerModels.SchedulerParameters.defaults()
        val learningSteps: RecordsSchedulerModels.LearningStepSettings =
            RecordsSchedulerModels.LearningStepSettings.defaults()
        val syncSettings: RecordsSyncModels.Settings = RecordsSyncModels.Settings.kikuDefaults()
        val reviewStats = RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0)
        val adaptiveLoadPlan = RecordsSchedulerModels.AdaptiveLoadPlan(
            100,
            0,
            0,
            emptyList(),
            0,
            true,
            "all",
        )
        val emptyProposal = RepairedWriteBackPolicy.Proposal(
            noteIdsToTag = emptySet(),
            cardIdsByNote = emptyMap(),
            kanjiByNote = emptyMap(),
            repairedKanji = emptyList(),
            candidateSourceCount = 0,
            rejectedCardCount = 0,
        )

        fun studyItem(): RecordsStudyModels.StudyItem =
            RecordsStudyModels.StudyItem(
                "休",
                "review",
                1_000L,
                1.0,
                2.0,
                1,
                0,
                0,
                0,
                "",
                900L,
            )
    }
}
