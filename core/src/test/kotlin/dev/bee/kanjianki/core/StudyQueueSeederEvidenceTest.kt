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
        val seeder = StudyQueueSeeder()
        val items = seeder.seedQueue(
            listOf(coveredRow("裂")),
            listOf(retiredItem("裂")),
            RecordsSyncModels.Settings.kikuDefaults(),
            1000L,
            0L,
            ladder = null,
            evidenceStatusByKanji = mapOf("裂" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        )
        val reopened = findItem(items, "裂")
        assertEquals("new", reopened.state)
        assertEquals(0, reopened.totalReviews)
        assertEquals(1000L, reopened.createdAtMillis)
    }

    @Test
    fun regressingReopenStillRequiresAdmissionRoom() {
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
        assertEquals("retired", findItem(items, "裂").state)
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
        assertEquals("new", findItem(baseline, "謎").state)
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
        assertEquals("new", findItem(items, "裂").state)
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
        return newItem(kanji).copyBuilder()
            .state("retired")
            .totalReviews(3)
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
        return RecordsImportModels.DashboardRow(
            kanji, 900, "meaning", "reading", "search", 30, "reason", "reason text", 1, 0, 0,
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
