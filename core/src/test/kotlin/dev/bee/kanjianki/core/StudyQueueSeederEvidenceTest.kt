package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the evidence-informed retire/reopen gate on queue seeding:
 * REGRESSING repair evidence blocks support-based retirement and reopens
 * retired repairs, while a null/absent evidence map preserves the exact
 * pre-existing behavior.
 */
class StudyQueueSeederEvidenceTest {
    @Test
    fun regressingEvidenceBlocksRetirementDespiteMatureSupport() {
        val seeder = StudyQueueSeeder()
        val items = seeder.seedQueue(
            listOf(coveredRow("裂")),
            listOf(reviewedItem("裂")),
            RecordsSyncModels.Settings.kikuDefaults(),
            1000L,
            0L,
            ladder = null,
            evidenceStatusByKanji = mapOf("裂" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        )
        assertEquals("review", findItem(items, "裂").state)
    }

    @Test
    fun nonRegressingEvidenceStillRetiresWithMatureSupport() {
        val seeder = StudyQueueSeeder()
        for (status in listOf(
            KanjiRepairEvidencePolicy.Status.IMPROVING,
            KanjiRepairEvidencePolicy.Status.STABLE,
            KanjiRepairEvidencePolicy.Status.INSUFFICIENT_EVIDENCE,
        )) {
            val items = seeder.seedQueue(
                listOf(coveredRow("裂")),
                listOf(reviewedItem("裂")),
                RecordsSyncModels.Settings.kikuDefaults(),
                1000L,
                0L,
                ladder = null,
                evidenceStatusByKanji = mapOf("裂" to status),
            )
            assertEquals("status $status must not block retirement", "retired", findItem(items, "裂").state)
        }
    }

    @Test
    fun matureSupportRetiresAnUnreviewedPersistedItem() {
        val original = newItem("裂").copyBuilder()
            .dueAtMillis(777L)
            .createdAtMillis(123L)
            .answerSignature("裂||reading|meaning")
            .build()

        val items = StudyQueueSeeder().seedQueue(
            listOf(coveredRow("裂")),
            listOf(original),
            RecordsSyncModels.Settings.kikuDefaults(),
            1_000L,
            0L,
            ladder = null,
        )

        val retired = findItem(items, "裂")
        assertEquals("retired", retired.state)
        assertEquals(777L, retired.dueAtMillis)
        assertEquals(123L, retired.createdAtMillis)
        assertEquals(0, retired.totalReviews)
    }

    @Test
    fun missingRowRetirementStaysUnconditionalEvenWhenRegressing() {
        val seeder = StudyQueueSeeder()
        val items = seeder.seedQueue(
            listOf(coveredRow("謎")),
            listOf(reviewedItem("古")),
            RecordsSyncModels.Settings.kikuDefaults(),
            1000L,
            0L,
            ladder = null,
            evidenceStatusByKanji = mapOf("古" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        )
        assertEquals("retired", findItem(items, "古").state)
    }

    @Test
    fun regressingEvidenceReopensRetiredItemDespiteMatureSupport() {
        val original = retiredItem("裂").copyBuilder()
            .dueAtMillis(777L)
            .createdAtMillis(123L)
            .stability(9.5)
            .schedulerRevision(4L)
            .build()
        val seeder = StudyQueueSeeder()
        val items = seeder.seedQueue(
            listOf(coveredRow("裂")),
            listOf(original),
            RecordsSyncModels.Settings.kikuDefaults(),
            1000L,
            0L,
            ladder = null,
            evidenceStatusByKanji = mapOf("裂" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        )
        val reopened = findItem(items, "裂")
        assertEquals("review", reopened.state)
        assertEquals(3, reopened.totalReviews)
        assertEquals(777L, reopened.dueAtMillis)
        assertEquals(123L, reopened.createdAtMillis)
        assertEquals(9.5, reopened.stability, 0.001)
        assertEquals(5L, reopened.schedulerRevision)
    }

    @Test
    fun supportDropReopensAnUnreviewedRetiredItemWithoutResettingItsIdentity() {
        val original = newItem("裂").copyBuilder()
            .state("retired")
            .dueAtMillis(777L)
            .createdAtMillis(123L)
            .answerSignature("裂|裂ける|さける|meaning")
            .schedulerRevision(4L)
            .build()

        val items = StudyQueueSeeder().seedQueue(
            listOf(activeRow("裂")),
            listOf(original),
            RecordsSyncModels.Settings.kikuDefaults(),
            1_000L,
            0L,
            ladder = null,
        )

        val reopened = findItem(items, "裂")
        assertEquals("new", reopened.state)
        assertEquals(777L, reopened.dueAtMillis)
        assertEquals(123L, reopened.createdAtMillis)
        assertEquals(5L, reopened.schedulerRevision)
    }

    @Test
    fun regressingReopenIgnoresAdmissionRoom() {
        val seeder = StudyQueueSeeder()
        val active = newItem("謎").copyBuilder().createdAtMillis(0L).build()
        val items = seeder.seedQueue(
            listOf(activeRow("謎"), coveredRow("裂")),
            listOf(active, retiredItem("裂")),
            settingsWithQueue(1, 3),
            1000L,
            500L,
            ladder = null,
            evidenceStatusByKanji = mapOf("裂" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        )
        assertEquals("review", findItem(items, "裂").state)
    }

    @Test
    fun reopeningDoesNotConsumeDailyNewAdmission() {
        val seeder = StudyQueueSeeder()
        val existing = listOf(
            newItem("謎").copyBuilder().createdAtMillis(0L).build(),
            retiredItem("裂"),
        )
        val items = seeder.seedQueue(
            listOf(activeRow("裂"), activeRow("謎"), activeRow("新")),
            existing,
            settingsWithQueue(3, 1),
            1_000L,
            500L,
            ladder = null,
            evidenceStatusByKanji = mapOf("裂" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        )

        assertEquals("review", findItem(items, "裂").state)
        assertEquals("new", findItem(items, "新").state)
    }

    @Test
    fun nullEvidenceMapPreservesRetireAndReopenBehavior() {
        val seeder = StudyQueueSeeder()
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val baseline = seeder.seedQueue(
            listOf(coveredRow("裂"), activeRow("謎")),
            listOf(reviewedItem("裂"), retiredItem("謎")),
            settings,
            1000L,
            0L,
            ladder = null,
        )
        val withNullMap = seeder.seedQueue(
            listOf(coveredRow("裂"), activeRow("謎")),
            listOf(reviewedItem("裂"), retiredItem("謎")),
            settings,
            1000L,
            0L,
            ladder = null,
            evidenceStatusByKanji = null,
        )
        assertEquals("retired", findItem(baseline, "裂").state)
        assertEquals("review", findItem(baseline, "謎").state)
        assertEquals(
            baseline.map { item -> item.kanji + ":" + item.state },
            withNullMap.map { item -> item.kanji + ":" + item.state },
        )
    }

    @Test
    fun bridgeSchedulerPassesEvidenceThroughLadderOverload() {
        val scheduler = BridgeScheduler()
        val items = scheduler.seedQueue(
            listOf(coveredRow("裂")),
            listOf(reviewedItem("裂")),
            RecordsSyncModels.Settings.kikuDefaults(),
            1000L,
            0L,
            RecordsBase.StudyLadderSettings.defaults(),
            mapOf("裂" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        )
        assertEquals("review", findItem(items, "裂").state)
    }

    @Test
    fun bridgeSchedulerPassesEvidenceThroughPlanOverload() {
        val scheduler = BridgeScheduler()
        val items = scheduler.seedQueue(
            listOf(coveredRow("裂")),
            listOf(retiredItem("裂")),
            RecordsSyncModels.Settings.kikuDefaults(),
            1000L,
            0L,
            null as RecordsSchedulerModels.AdaptiveLoadPlan?,
            RecordsBase.StudyLadderSettings.defaults(),
            mapOf("裂" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        )
        assertEquals("review", findItem(items, "裂").state)
    }

    @Test
    fun adaptivePlanFilteringDoesNotBlockAutomaticReopening() {
        val emptyFocus = RecordsSchedulerModels.AdaptiveLoadPlan(
            20,
            0,
            0,
            emptyList(),
            0,
            false,
            "retired items excluded from active projection",
        )
        val scheduler = BridgeScheduler()
        val settings = RecordsSyncModels.Settings.kikuDefaults()

        val supportDrop = scheduler.seedQueue(
            listOf(activeRow("落")),
            listOf(retiredItem("落")),
            settings,
            1_000L,
            0L,
            emptyFocus,
            RecordsBase.StudyLadderSettings.defaults(),
        )
        val regressing = scheduler.seedQueue(
            listOf(coveredRow("裂")),
            listOf(retiredItem("裂")),
            settings,
            1_000L,
            0L,
            emptyFocus,
            RecordsBase.StudyLadderSettings.defaults(),
            mapOf("裂" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        )

        assertEquals("review", findItem(supportDrop, "落").state)
        assertEquals("review", findItem(regressing, "裂").state)
    }

    @Test
    fun bridgeSchedulerKeepsLocallyIneligibleProviderRowOutOfRetirement() {
        val scheduler = BridgeScheduler()
        val items = scheduler.seedQueue(
            listOf(activeRow("謎")),
            emptyList(),
            listOf(reviewedItem("謎")),
            RecordsSyncModels.Settings.kikuDefaults(),
            1_000L,
            0L,
            null as RecordsSchedulerModels.AdaptiveLoadPlan?,
            RecordsBase.StudyLadderSettings.defaults(),
            mapOf("謎" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        )

        assertEquals("review", findItem(items, "謎").state)
    }

    @Test
    fun reopeningRelearningItemRestoresLearningState() {
        val retired = reviewedItem("謎").copyBuilder()
            .state(StudyLadderRules.STATE_RETIRED)
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .build()

        val now = 1_000L
        val reopened = StudyQueueSeeder().seedQueue(
            listOf(activeRow("謎")),
            listOf(retired),
            RecordsSyncModels.Settings.kikuDefaults(),
            now,
            now,
            null,
            RecordsBase.StudyLadderSettings.defaults(),
            mapOf("謎" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        ).single()

        assertEquals(StudyLadderRules.STATE_LEARNING, reopened.state)
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, reopened.phase)
    }

    @Test
    fun reopeningRetiredItemWithChangedMeaningResetsUnrelatedReviewState() {
        val oldRow = activeRowWithMeaning("謎", "old meaning")
        val newRow = activeRowWithMeaning("謎", "new meaning")
        val retired = reviewedItem("謎").copyBuilder()
            .state(StudyLadderRules.STATE_RETIRED)
            .answerSignature(StudyQueueSeeder.answerSignature(oldRow))
            .totalReviews(9)
            .lapses(3)
            .wordReadingMemory(
                RecordsStudyModels.TaskMemory(
                    "review", 900L, 12.0, 4.0, 7, 2, 0, "good", 30, 1, 700L, 800L,
                ),
            )
            .schedulerRevision(12L)
            .build()

        val now = 1_000L
        val reopened = StudyQueueSeeder().seedQueue(
            listOf(newRow),
            listOf(retired),
            RecordsSyncModels.Settings.kikuDefaults(),
            now,
            now,
            ladder = null,
        ).single()

        assertEquals(StudyLadderRules.STATE_NEW, reopened.state)
        assertEquals(now, reopened.dueAtMillis)
        assertEquals(0, reopened.totalReviews)
        assertEquals(0, reopened.lapses)
        assertEquals(RecordsStudyModels.TaskMemory.initial().encode(), reopened.wordReadingMemory.encode())
        assertEquals(StudyQueueSeeder.answerSignature(newRow), reopened.answerSignature)
        assertEquals(14L, reopened.schedulerRevision)
    }

    private fun newItem(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "new", 0, 0.4, 5.0, 0, 0, 0, 0, 0, 0, 0L, false, null, 0)
    }

    private fun reviewedItem(kanji: String): RecordsStudyModels.StudyItem {
        return newItem(kanji).copyBuilder()
            .state("review")
            .stability(1.5)
            .totalReviews(3)
            .rung(RecordsBase.LadderRung.FONT_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .build()
    }

    private fun retiredItem(kanji: String): RecordsStudyModels.StudyItem {
        return reviewedItem(kanji).copyBuilder()
            .state("retired")
            .build()
    }

    private fun coveredRow(kanji: String): RecordsImportModels.DashboardRow {
        // matureSupportCount 2 meets the kikuDefaults matureSupportThreshold of 2.
        return RecordsImportModels.DashboardRow(
            kanji, 900, "meaning", "reading", "search", 5, "reason", "reason text", 2, 1, 2,
            ArrayList<RecordsImportModels.Example>(),
        )
    }

    private fun activeRow(kanji: String): RecordsImportModels.DashboardRow {
        return activeRowWithMeaning(kanji, "meaning")
    }

    private fun activeRowWithMeaning(kanji: String, meaning: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji, 900, meaning, "reading", "search", 30, "reason", "reason text", 1, 0, 0,
            ArrayList<RecordsImportModels.Example>(),
        )
    }

    private fun findItem(items: List<RecordsStudyModels.StudyItem>, kanji: String): RecordsStudyModels.StudyItem {
        for (item in items) {
            if (item.kanji == kanji) {
                return item
            }
        }
        throw AssertionError("Missing study item for $kanji")
    }

    private fun settingsWithQueue(activeQueueCap: Int, newPerDay: Int): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
            defaults.modelName,
            defaults.templateName,
            defaults.expressionField,
            defaults.readingField,
            defaults.meaningField,
            defaults.sentenceField,
            defaults.frequencyField,
            defaults.frequencySortField,
            defaults.matureDays,
            defaults.matureSupportThreshold,
            defaults.suspendedRankMin,
            defaults.suspendedRankMax,
            activeQueueCap,
            newPerDay,
            defaults.writingTriggerMissDays,
            defaults.recognitionPromotionPasses,
            defaults.realDueReviewsToMove,
        )
    }
}
