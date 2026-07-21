package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ingestion-side fixes on queue seeding:
 *  - Finding 1: ceiling-parked items do not consume the active-queue cap.
 *  - Finding 3: kanji already repaired in Anki are never admitted for study.
 *  - Finding 7: a suspend/unsuspend reshuffle that only changes the preferred
 *    example (same meaning) preserves earned scheduler state.
 *  - Finding 2: evidence-strong kanji are seeded straight into review.
 */
class StudyQueueSeederIngestionTest {
    private val seeder = StudyQueueSeeder()

    // ---- Finding 3: admission gate on mature support ----

    @Test
    fun alreadyRepairedKanjiIsNeverAdmitted() {
        val items = seeder.seedQueue(
            listOf(supportedRow("古"), suspendedRow("新")),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            1000L,
            0L,
            ladder = null,
        )
        assertNull("古 already has mature support and must not be admitted", find(items, "古"))
        assertTrue("新 still needs repair and is admitted", find(items, "新") != null)
    }

    @Test
    fun regressingEvidenceStillAdmitsAnAlreadySupportedKanji() {
        val items = seeder.seedQueue(
            listOf(supportedRow("古")),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            1000L,
            0L,
            ladder = null,
            evidenceStatusByKanji = mapOf("古" to KanjiRepairEvidencePolicy.Status.REGRESSING),
        )
        assertTrue("REGRESSING evidence overrides the mature-support admission gate", find(items, "古") != null)
    }

    // ---- Finding 1: ceiling parking frees the active-queue cap ----

    @Test
    fun ceilingParkedItemDoesNotBlockNewAdmission() {
        val items = seeder.seedQueue(
            listOf(suspendedRow("裂"), suspendedRow("新")),
            listOf(ceilingItem("裂", matureIntervalDays = 100)),
            settingsWithQueue(activeQueueCap = 1, newPerDay = 5),
            2000L,
            1000L,
            ladder = null,
        )
        // 裂 rides a long interval at the top rung, so despite a cap of 1 the new
        // kanji 新 is still admitted.
        assertTrue("新 admitted because the parked ceiling item frees the cap", find(items, "新") != null)
        assertEquals("review", find(items, "裂")!!.state)
    }

    @Test
    fun shortIntervalCeilingItemStillCountsAgainstTheCap() {
        val items = seeder.seedQueue(
            listOf(suspendedRow("裂"), suspendedRow("新")),
            listOf(ceilingItem("裂", matureIntervalDays = 10)),
            settingsWithQueue(activeQueueCap = 1, newPerDay = 5),
            2000L,
            1000L,
            ladder = null,
        )
        // A still-short interval at the ceiling is not parked, so it holds the
        // single cap slot and blocks admission.
        assertNull("新 blocked: the un-parked ceiling item fills the cap", find(items, "新"))
    }

    @Test
    fun ceilingParkingRequiresIntervalPastThreshold() {
        val settings = settingsWithQueue(
            activeQueueCap = 1,
            newPerDay = 5,
            ladderPromotionIntervalDays = 20,
        )
        val rows = listOf(suspendedRow("裂"), suspendedRow("新"))

        val atThreshold = seeder.seedQueue(
            rows,
            listOf(ceilingItem("裂", matureIntervalDays = 80)),
            settings,
            2_000L,
            1_000L,
            ladder = null,
        )
        val pastThreshold = seeder.seedQueue(
            rows,
            listOf(ceilingItem("裂", matureIntervalDays = 81)),
            settings,
            2_000L,
            1_000L,
            ladder = null,
        )

        assertNull("an item at the threshold still consumes the queue slot", find(atThreshold, "新"))
        assertTrue("an item past the threshold is parked", find(pastThreshold, "新") != null)
    }

    @Test
    fun saturatedCeilingParkingThresholdCannotBePassed() {
        val settings = settingsWithQueue(
            activeQueueCap = 1,
            newPerDay = 5,
            ladderPromotionIntervalDays = Int.MAX_VALUE,
        )
        val rows = listOf(suspendedRow("裂"), suspendedRow("新"))

        val belowThreshold = seeder.seedQueue(
            rows,
            listOf(ceilingItem("裂", matureIntervalDays = Int.MAX_VALUE - 1)),
            settings,
            2_000L,
            1_000L,
            ladder = null,
        )
        val atThreshold = seeder.seedQueue(
            rows,
            listOf(ceilingItem("裂", matureIntervalDays = Int.MAX_VALUE)),
            settings,
            2_000L,
            1_000L,
            ladder = null,
        )

        assertNull("saturated threshold must not wrap and park an item early", find(belowThreshold, "新"))
        assertNull("an item cannot grow past a saturated threshold", find(atThreshold, "新"))
    }

    @Test
    fun adaptiveContextualCeilingParksEvenWhenSentenceVariantIsAvailable() {
        val items = seeder.seedQueue(
            listOf(suspendedRow("裂"), suspendedRow("新")),
            listOf(adaptiveContextualItem("裂", revalidationPending = false)),
            settingsWithQueue(activeQueueCap = 1, newPerDay = 5),
            2000L,
            1000L,
            ladder = RecordsBase.StudyLadderSettings.defaults(),
        )

        assertTrue("sentence is a variant, so the validated contextual item parks", find(items, "新") != null)
    }

    @Test
    fun adaptiveContextualRevalidationStillConsumesTheQueueCap() {
        val items = seeder.seedQueue(
            listOf(suspendedRow("裂"), suspendedRow("新")),
            listOf(adaptiveContextualItem("裂", revalidationPending = true)),
            settingsWithQueue(activeQueueCap = 1, newPerDay = 5),
            2000L,
            1000L,
            ladder = RecordsBase.StudyLadderSettings.defaults(),
        )

        assertNull("an unresolved contextual miss must not park", find(items, "新"))
    }

    // ---- Finding 7: preserve earned state on example reshuffle ----

    @Test
    fun exampleReshuffleWithSameMeaningPreservesSchedulerState() {
        val oldRow = exampleRow("裂", expression = "裂ける", meaning = "split")
        val newRow = exampleRow("裂", expression = "決裂", meaning = "split")
        val oldSignature = StudyQueueSeeder.answerSignature(oldRow)

        val existing = matureReviewItem("裂", oldSignature)
        val items = seeder.seedQueue(
            listOf(newRow),
            listOf(existing),
            RecordsSyncModels.Settings.kikuDefaults(),
            5000L,
            0L,
            ladder = null,
        )

        val preserved = find(items, "裂")!!
        assertEquals("review", preserved.state)
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, preserved.rung)
        assertEquals(5, preserved.totalReviews)
        assertEquals(50.0, preserved.stability, 0.001)
        assertEquals(StudyQueueSeeder.answerSignature(newRow), preserved.answerSignature)
    }

    @Test
    fun locallyIneligibleItemDoesNotConsumeAdmissionCapacity() {
        val suspendedLocally = matureReviewItem(
            "痛",
            StudyQueueSeeder.answerSignature(suspendedRow("痛")),
        ).copyBuilder()
            .schedulerRevision(5L)
            .build()
        val candidate = suspendedRow("新")

        val items = StudyQueueSeeder().seedQueue(
            listOf(suspendedRow("痛"), candidate),
            listOf(candidate),
            listOf(suspendedLocally),
            settingsWithQueue(activeQueueCap = 1, newPerDay = 5),
            2_000L,
            1_000L,
            plan = null,
            ladder = null,
            evidenceStatusByKanji = emptyMap(),
        )

        assertEquals(StudyLadderRules.STATE_REVIEW, items.single { it.kanji == "痛" }.state)
        assertEquals(StudyLadderRules.STATE_NEW, items.single { it.kanji == "新" }.state)
    }

    @Test
    fun duplicateLegacySignaturesCollapseWithoutDiscardingDurableReviewEvidence() {
        val currentRow = exampleRow("裂", expression = "決裂", meaning = "split")
        val olderRow = exampleRow("裂", expression = "裂ける", meaning = "split")
        val reviewedMemory = RecordsStudyModels.TaskMemory(
            "review", 12_000L, 40.0, 4.0, 7, 3, 0, "hard", 45, 2, 8_000L, 9_000L,
        )
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.CONTEXTUAL_READING,
            recognitionReviewCount = 4,
            contextualReadingReviewCount = 7,
            coreDueAtMillis = 12_000L,
        )
        val strongest = matureReviewItem("裂", StudyQueueSeeder.answerSignature(olderRow))
            .copyBuilder()
            .lapses(3)
            .wordReadingMemory(reviewedMemory)
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .schedulerRevision(4L)
            .build()
        val duplicate = matureReviewItem("裂", "")
            .copyBuilder()
            .totalReviews(1)
            .lapses(0)
            .wordReadingMemory(RecordsStudyModels.TaskMemory.initial())
            .schedulerRevision(9L)
            .build()

        val items = seeder.seedQueue(
            listOf(currentRow),
            listOf(duplicate, strongest),
            RecordsSyncModels.Settings.kikuDefaults(),
            5_000L,
            0L,
            ladder = null,
        )

        assertEquals(1, items.count { it.kanji == "裂" })
        val canonical = find(items, "裂")!!
        assertEquals(9L, canonical.schedulerRevision)
        assertEquals(5, canonical.totalReviews)
        assertEquals(3, canonical.lapses)
        assertEquals(50.0, canonical.stability, 0.001)
        assertEquals(reviewedMemory.encode(), canonical.wordReadingMemory.encode())
        assertEquals(AdaptiveRouteStateCodec.encode(route), canonical.adaptiveRouteStateJson)
        assertEquals(StudyQueueSeeder.answerSignature(currentRow), canonical.answerSignature)

        val reverseCanonical = seeder.seedQueue(
            listOf(currentRow),
            listOf(strongest, duplicate),
            RecordsSyncModels.Settings.kikuDefaults(),
            5_000L,
            0L,
            ladder = null,
        ).single()
        assertEquals(canonical.state, reverseCanonical.state)
        assertEquals(canonical.dueAtMillis, reverseCanonical.dueAtMillis)
        assertEquals(canonical.totalReviews, reverseCanonical.totalReviews)
        assertEquals(canonical.lapses, reverseCanonical.lapses)
        assertEquals(canonical.wordReadingMemory.encode(), reverseCanonical.wordReadingMemory.encode())
        assertEquals(canonical.adaptiveRouteStateJson, reverseCanonical.adaptiveRouteStateJson)
        assertEquals(canonical.answerSignature, reverseCanonical.answerSignature)
        assertEquals(canonical.schedulerRevision, reverseCanonical.schedulerRevision)
    }

    @Test
    fun meaningChangeStillResetsSchedulerState() {
        val oldRow = exampleRow("裂", expression = "裂ける", meaning = "split")
        val changedRow = exampleRow("裂", expression = "裂ける", meaning = "tear apart")
        val oldSignature = StudyQueueSeeder.answerSignature(oldRow)

        val existing = matureReviewItem("裂", oldSignature)
        val items = seeder.seedQueue(
            listOf(changedRow),
            listOf(existing),
            RecordsSyncModels.Settings.kikuDefaults(),
            5000L,
            0L,
            ladder = null,
        )

        val reset = find(items, "裂")!!
        assertEquals(0, reset.totalReviews)
        assertEquals("learning", reset.state)
        assertFalse("rung demoted on genuine content change", reset.rung == RecordsBase.LadderRung.FONT_MEANING)
    }

    // ---- Finding 2: evidence-strong kanji seed straight into review ----

    @Test
    fun matureActiveKanjiSeedsReviewAtTopRung() {
        val items = seeder.seedQueue(
            listOf(matureActiveRow("裂")),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            1000L,
            0L,
            ladder = null,
        )
        val seeded = find(items, "裂")!!
        assertEquals("review", seeded.state)
        assertEquals(RecordsBase.LadderRung.WORD_READING, seeded.rung)
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, seeded.phase)
        assertTrue("seeded stability tracks Anki evidence, not the placeholder", seeded.stability >= 20.0)
    }

    // ---- helpers ----

    private fun find(items: List<RecordsStudyModels.StudyItem>, kanji: String): RecordsStudyModels.StudyItem? {
        return items.firstOrNull { it.kanji == kanji }
    }

    private fun supportedRow(kanji: String): RecordsImportModels.DashboardRow {
        // matureSupportCount 2 meets the default retirement threshold.
        return RecordsImportModels.DashboardRow(
            kanji, 900, "meaning", "reading", "search", 5, "reason", "reason text", 2, 0, 2,
            emptyList<RecordsImportModels.Example>(),
        )
    }

    private fun suspendedRow(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji, 900, "meaning", "reading", "search", 24, "suspended_archive", "reason text", 0, 1, 0,
            emptyList<RecordsImportModels.Example>(),
        )
    }

    private fun matureActiveRow(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji, 900, "meaning", "reading", "search", 5, "reason", "reason text", 1, 0, 1,
            listOf(
                RecordsImportModels.Example(
                    RecordsBase.SOURCE_ACTIVE, 1L, 1L, "裂ける", "さける", "to split",
                    "sentence", true, 0, 30, 6, 30.0, 4.0, null,
                ),
            ),
        )
    }

    private fun exampleRow(kanji: String, expression: String, meaning: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji, 900, meaning, "reading", "search", 24, "suspended_archive", "reason text", 0, 1, 0,
            listOf(
                RecordsImportModels.Example(
                    RecordsBase.SOURCE_SUSPENDED, 1L, 1L, expression, "reading", meaning,
                    "sentence", false, 1, 0, 3, null, null, null,
                ),
            ),
        )
    }

    private fun ceilingItem(kanji: String, matureIntervalDays: Int): RecordsStudyModels.StudyItem {
        return baseItem(kanji).copyBuilder()
            .state("review")
            .stability(80.0)
            .totalReviews(4)
            .rung(RecordsBase.LadderRung.WORD_READING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .matureIntervalDays(matureIntervalDays)
            .createdAtMillis(0L)
            .build()
    }

    private fun adaptiveContextualItem(
        kanji: String,
        revalidationPending: Boolean,
    ): RecordsStudyModels.StudyItem {
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.CONTEXTUAL_READING,
            contextualReadingReviewCount = 1,
            revalidationPending = revalidationPending,
        )
        val memory = RecordsStudyModels.TaskMemory(
            StudyLadderRules.STATE_REVIEW,
            10_000L,
            80.0,
            5.0,
            4,
            0,
            0,
            "good",
            100,
            1,
            1L,
        )
        return ceilingItem(kanji, matureIntervalDays = 100)
            .withTaskMemory(StudyTaskTypes.WORD_READING, memory)
            .copyBuilder()
            .hasSentenceReading(true)
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .build()
    }

    private fun matureReviewItem(kanji: String, signature: String): RecordsStudyModels.StudyItem {
        return baseItem(kanji).copyBuilder()
            .state("review")
            .stability(50.0)
            .totalReviews(5)
            .rung(RecordsBase.LadderRung.FONT_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .matureIntervalDays(60)
            .answerSignature(signature)
            .createdAtMillis(0L)
            .build()
    }

    private fun baseItem(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji, "new", 0L, 0.4, 5.0, 0, 0, 0, 0, 0, 0, 0L, false, "", 0L, 0, "", "", 100L,
        )
    }

    private fun settingsWithQueue(
        activeQueueCap: Int,
        newPerDay: Int,
        ladderPromotionIntervalDays: Int = RecordsSyncModels.Settings.kikuDefaults().ladderPromotionIntervalDays,
    ): RecordsSyncModels.Settings {
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
            defaults.importActiveCards,
            defaults.importSuspendedCards,
            defaults.importTaggedCards,
            defaults.importTags,
            defaults.importWeakCards,
            defaults.importWeakFsrsDifficultyThreshold,
            defaults.importWeakLapsesThreshold,
            defaults.importMinMatchingCardsPerKanji,
            defaults.importBrowserQueryCards,
            defaults.importBrowserQuery,
            defaults.newCardSortMode,
            ladderPromotionIntervalDays,
            defaults.ladderDemotionFailStreak,
            defaults.ladderPromotionMinPasses,
        )
    }
}
