package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Evidence-based admission seeding (Finding 2): kanji the learner already reads
 * in a mature context are validated once at the top rung instead of climbing the
 * whole ladder, while everything else keeps the conservative new-learning start.
 */
class AdmissionEvidencePolicyTest {
    private val ladder = RecordsBase.StudyLadderSettings.defaults()
    private val settings = RecordsSyncModels.Settings.kikuDefaults()

    @Test
    fun matureActiveNeverSuspendedSeedsReviewAtTopRung() {
        val row = row(
            "裂",
            active = 1,
            suspended = 0,
            mature = 1,
            examples = listOf(activeExample(mature = true, intervalDays = 40, stability = 40.0, difficulty = 3.0)),
        )
        val seed = AdmissionEvidencePolicy.seedFor(row, ladder, settings)

        assertTrue(AdmissionEvidencePolicy.isAlreadyReadInContext(row))
        assertTrue(seed.isReviewSeed())
        assertEquals(RecordsBase.LadderRung.WORD_READING, seed.rung)
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, seed.phase)
        // Stability tracks Anki's own memory estimate rather than the new-card
        // placeholder, so the confirmation review schedules a realistic interval.
        assertEquals(40.0, seed.stability, 0.001)
        assertEquals(3.0, seed.difficulty, 0.001)
        assertEquals(40, seed.matureIntervalDays)
    }

    @Test
    fun suspendedExampleBlocksReviewSeedEvenWithMatureSupport() {
        val row = row("裂", active = 1, suspended = 1, mature = 1, examples = emptyList())
        val seed = AdmissionEvidencePolicy.seedFor(row, ladder, settings)

        assertFalse(AdmissionEvidencePolicy.isAlreadyReadInContext(row))
        assertFalse(seed.isReviewSeed())
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, seed.rung)
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, seed.phase)
    }

    @Test
    fun noMatureSupportStartsNewLearningAtDefaultRung() {
        val row = row("裂", active = 1, suspended = 0, mature = 0, examples = emptyList())
        val seed = AdmissionEvidencePolicy.seedFor(row, ladder, settings)

        assertFalse(seed.isReviewSeed())
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, seed.rung)
        assertEquals(0.4, seed.stability, 0.001)
    }

    @Test
    fun difficultyIsPrimedFromLapsesWhenNoFsrsDifficulty() {
        val row = row(
            "裂",
            active = 1,
            suspended = 1,
            mature = 0,
            examples = listOf(suspendedExample(lapses = 4)),
        )
        val seed = AdmissionEvidencePolicy.seedFor(row, ladder, settings)

        // 5.0 base + 4 lapses, capped by the max lapse bonus of 5.
        assertEquals(9.0, seed.difficulty, 0.001)
    }

    @Test
    fun missingFsrsStabilityFallsBackToIntervalDays() {
        val row = row(
            "裂",
            active = 1,
            suspended = 0,
            mature = 1,
            examples = listOf(activeExample(mature = true, intervalDays = 55, stability = null, difficulty = null)),
        )
        val seed = AdmissionEvidencePolicy.seedFor(row, ladder, settings)

        assertTrue(seed.isReviewSeed())
        assertEquals(55.0, seed.stability, 0.001)
    }

    @Test
    fun nullRowSeedsPlainNewCard() {
        val seed = AdmissionEvidencePolicy.seedFor(null, ladder, settings)
        assertFalse(seed.isReviewSeed())
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, seed.rung)
        assertEquals(5.0, seed.difficulty, 0.001)
    }

    private fun row(
        kanji: String,
        active: Int,
        suspended: Int,
        mature: Int,
        examples: List<RecordsImportModels.Example>,
    ): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji, 900, "meaning", "reading", "search", 10, "reason", "reason text",
            active, suspended, mature, examples,
        )
    }

    private fun activeExample(
        mature: Boolean,
        intervalDays: Int,
        stability: Double?,
        difficulty: Double?,
    ): RecordsImportModels.Example {
        return RecordsImportModels.Example(
            RecordsBase.SOURCE_ACTIVE, 1L, 1L, "裂ける", "さける", "to split",
            "sentence", mature, 0, intervalDays, 5, stability, difficulty, null,
        )
    }

    private fun suspendedExample(lapses: Int): RecordsImportModels.Example {
        return RecordsImportModels.Example(
            RecordsBase.SOURCE_SUSPENDED, 2L, 2L, "裂ける", "さける", "to split",
            "sentence", false, lapses, 0, 3, null, null, null,
        )
    }
}
