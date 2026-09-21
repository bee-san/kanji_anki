package dev.bee.kanjianki.sync

import dev.bee.kanjianki.core.JitenKanjiRanks
import dev.bee.kanjianki.core.KanjiAnalyzer
import dev.bee.kanjianki.core.KanjiImportSelector
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.StudyQueueSeeder
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.ProviderCollectionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Goldens for what [CollectionCapability.FSRS_MEMORY_STATE] actually costs a
 * provider that cannot declare it.
 *
 * [ProviderCapabilityPolicyTest] pins the normalization step in isolation: with
 * the capability absent the FSRS fields are stripped. That is necessary but not
 * sufficient, because stripped fields are only interesting if something
 * downstream reads them. This test runs the whole admission path a real sync
 * runs — normalize, import selection, dashboard analysis, queue seeding — and
 * pins the observable difference at the far end, so a change to any of the four
 * stages that silently equalizes or silently widens the AnkiDroid/AnkiConnect
 * gap has to change a number here.
 *
 * Both directions carry signal. The FSRS path is not uniformly "weaker card":
 * for the mature example the provider's own memory state makes the card look
 * *more* fragile (retrievability 0.42 is below the weak threshold, so the row
 * scores 25 and reports `fsrs_weak_memory`) while the portable fallback sees
 * only a healthy 25-day interval and scores 13 with `weak_support`. But the
 * fallback then seeds a *larger* stability, because it derives stability from
 * that same interval rather than from the provider's 9-day stability. Neither
 * path is a scaled version of the other, which is the reason the desktop
 * provider's missing capability is a documented behavior difference rather than
 * a rounding error.
 */
class ProviderCapabilityAdmissionGoldenTest {
    private val settings = RecordsSyncModels.Settings.kikuDefaults()
    private val ranks = JitenKanjiRanks(mapOf(KANJI to 900))

    /**
     * An active, mature, high-difficulty card — the shape where the provider's
     * FSRS state disagrees most with what interval and lapses alone imply.
     */
    @Test
    fun aMatureCardIsScoredAndSeededDifferentlyWithoutProviderFsrs() {
        val snapshot = snapshotOf(
            card(
                queue = 2,
                type = 2,
                intervalDays = 25,
                reps = 12,
                lapses = 4,
                suspended = false,
                fsrsStability = 9.0,
                fsrsDifficulty = 8.5,
                fsrsRetrievability = 0.42,
            ),
        )

        val withFsrs = admit(snapshot, declaresFsrs = true)
        assertEquals(
            ProviderCapabilityPolicy.MemoryEvidence.PROVIDER_FSRS,
            withFsrs.evidence,
        )
        assertEquals(25, withFsrs.row.weaknessScore)
        assertEquals("fsrs_weak_memory", withFsrs.row.reasonCode)
        assertEquals(9.0, withFsrs.item.stability, 0.0)
        assertEquals(8.5, withFsrs.item.difficulty, 0.0)
        assertEquals(9, withFsrs.item.matureIntervalDays)

        val portable = admit(snapshot, declaresFsrs = false)
        assertEquals(
            ProviderCapabilityPolicy.MemoryEvidence.INTERVAL_LAPSE_FALLBACK,
            portable.evidence,
        )
        // The provider's low retrievability was the whole reason this row read as
        // weak memory; without it the row falls back to support pressure.
        assertEquals(13, portable.row.weaknessScore)
        assertEquals("weak_support", portable.row.reasonCode)
        // Stability now comes from the 25-day interval, not the 9-day FSRS
        // stability, so the fallback is the *more* optimistic of the two here.
        assertEquals(25.0, portable.item.stability, 0.0)
        // Difficulty falls back to 5.0 + min(5, lapses) = 5.0 + 4.
        assertEquals(9.0, portable.item.difficulty, 0.0)
        assertEquals(25, portable.item.matureIntervalDays)

        // Both paths admit the same kanji at the same evidence-seeded rung: the
        // capability changes how strong the memory looks, never whether the
        // learner is asked about the kanji at all.
        for (result in listOf(withFsrs, portable)) {
            assertEquals(KANJI, result.item.kanji)
            assertEquals(RecordsBase.LadderRung.WORD_READING, result.item.rung)
            assertEquals(RecordsBase.SchedulerPhase.REVIEW, result.item.phase)
        }
    }

    /**
     * A suspended leech — the shape where the two paths agree on the dashboard
     * and diverge only in the seed. Worth pinning precisely because the
     * agreement is easy to mistake for "the capability does not matter here":
     * the reason code and score match, but the seeded difficulty does not.
     */
    @Test
    fun aSuspendedLeechAgreesOnTheRowAndStillDivergesOnSeedDifficulty() {
        val snapshot = snapshotOf(
            card(
                queue = -1,
                type = -1,
                intervalDays = 5,
                reps = 6,
                lapses = 1,
                suspended = true,
                fsrsStability = 2.0,
                fsrsDifficulty = 8.5,
                fsrsRetrievability = 0.30,
            ),
        )

        val withFsrs = admit(snapshot, declaresFsrs = true)
        val portable = admit(snapshot, declaresFsrs = false)

        // Suspension dominates the weakness score, so the row is identical.
        for (result in listOf(withFsrs, portable)) {
            assertEquals(22, result.row.weaknessScore)
            assertEquals("suspended_archive", result.row.reasonCode)
            assertEquals(0, result.row.activeExampleCount)
            assertEquals(1, result.row.suspendedExampleCount)
            // A suspended-only kanji has no mature support, so it starts new
            // rather than riding a seeded review interval; the placeholder
            // stability is the same either way.
            assertEquals(StudyLadderRules.STATE_NEW, result.item.state)
            assertEquals(RecordsBase.LadderRung.KANJI_MEANING, result.item.rung)
            assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, result.item.phase)
            assertEquals(0, result.item.matureIntervalDays)
        }

        // The divergence that survives: the provider's 8.5 versus the portable
        // 5.0 + min(5, 1) lapse-derived estimate.
        assertEquals(8.5, withFsrs.item.difficulty, 0.0)
        assertEquals(6.0, portable.item.difficulty, 0.0)
    }

    /** One kanji's worth of the whole admission path, for one capability set. */
    private fun admit(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        declaresFsrs: Boolean,
    ): Admission {
        val capabilities = if (declaresFsrs) {
            setOf(CollectionCapability.READ_COLLECTION, CollectionCapability.FSRS_MEMORY_STATE)
        } else {
            setOf(CollectionCapability.READ_COLLECTION)
        }
        val normalized = ProviderCapabilityPolicy.normalize(
            ProviderCollectionSnapshot(snapshot, capabilities, null),
        )
        val imports = KanjiImportSelector(ranks, settings.suspendedRankMin, settings.suspendedRankMax)
            .importFrom(normalized.snapshot, settings)
        val rows = KanjiAnalyzer().rebuild(normalized.snapshot, imports, ranks, settings)
        val items = StudyQueueSeeder().seedQueue(
            rows,
            emptyList(),
            settings,
            NOW_MILLIS,
            START_OF_DAY_MILLIS,
            ladder = null,
        )
        return Admission(
            normalized.memoryEvidence,
            rows.single { it.kanji == KANJI },
            items.single { it.kanji == KANJI },
        )
    }

    private class Admission(
        val evidence: ProviderCapabilityPolicy.MemoryEvidence,
        val row: RecordsImportModels.DashboardRow,
        val item: RecordsStudyModels.StudyItem,
    )

    private fun snapshotOf(card: RecordsSyncModels.Card) =
        RecordsSyncModels.CollectionSnapshot(listOf(note()), listOf(card))

    private fun note() = RecordsSyncModels.Note(
        NOTE_ID,
        42L,
        settings.modelName,
        mapOf(
            settings.expressionField to KANJI,
            settings.readingField to "はし",
            settings.meaningField to "bridge",
            settings.sentenceField to "橋を渡る。",
            settings.frequencyField to "1",
            settings.frequencySortField to "1",
        ),
        emptyList(),
    )

    @Suppress("LongParameterList")
    private fun card(
        queue: Int,
        type: Int,
        intervalDays: Int,
        reps: Int,
        lapses: Int,
        suspended: Boolean,
        fsrsStability: Double,
        fsrsDifficulty: Double,
        fsrsRetrievability: Double,
    ) = RecordsSyncModels.Card(
        CARD_ID,
        NOTE_ID,
        0,
        "Mining",
        "Mining",
        queue,
        type,
        100,
        intervalDays,
        reps,
        lapses,
        suspended,
        fsrsStability,
        fsrsDifficulty,
        fsrsRetrievability,
    )

    private companion object {
        const val KANJI = "橋"
        const val NOTE_ID = 11L
        const val CARD_ID = 110L
        const val NOW_MILLIS = 1_000L
        const val START_OF_DAY_MILLIS = 0L
    }
}
