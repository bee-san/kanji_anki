package dev.bee.kanjianki.fsrs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.FsrsPersonalization
import dev.bee.kanjianki.core.FsrsWeightFitter
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.FsrsFitSummaryCodec
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.HashSet

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FsrsFitRunnerTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun adoptedFitPersistsFullPrecisionWeightsUsedByNextScheduler() {
        val weights = customWeights()
        val fit = fitResult(weights, adopted = true, reason = FsrsWeightFitter.REASON_ADOPTED)
        store.saveFsrsPersonalizationEnabled(true)

        FsrsFitRunner.run(
            store,
            1234L,
            FsrsFitRunner.FitOperation { _, _ -> fit },
        )

        assertArrayEquals(weights, store.schedulerFsrsWeights(), 0.0)
        val summary = FsrsFitSummaryCodec.decode(store.fsrsFitSummaryJson())!!
        assertEquals(true, summary.adopted)
        assertEquals(1234L, summary.fittedAtMillis)

        val defaultDue = reviewWith(BridgeScheduler())
        val fittedDue = reviewWith(BridgeScheduler.withWeights(store.schedulerFsrsWeights()))
        assertNotEquals(defaultDue, fittedDue)
    }

    @Test
    fun cancelledFitWritesSummaryButPreservesPreviouslyAdoptedWeights() {
        val existing = customWeights()
        store.saveSchedulerFsrsWeights(existing)
        val cancelled = fitResult(
            DoubleArray(21) { 0.2 }.also { it[20] = 0.3 },
            adopted = false,
            reason = FsrsWeightFitter.REASON_CANCELLED,
        )

        FsrsFitRunner.run(
            store,
            5678L,
            FsrsFitRunner.FitOperation { _, stop ->
                stop()
                cancelled
            },
            shouldStop = { true },
        )

        assertArrayEquals(existing, store.schedulerFsrsWeights(), 0.0)
        assertEquals(FsrsWeightFitter.REASON_CANCELLED, FsrsFitSummaryCodec.decode(store.fsrsFitSummaryJson())!!.reason)
    }

    @Test
    fun optOutDuringFitCannotRestoreAdoptedWeights() {
        store.saveFsrsPersonalizationEnabled(true)
        val fit = fitResult(customWeights(), adopted = true, reason = FsrsWeightFitter.REASON_ADOPTED)

        val result = FsrsFitRunner.run(
            store,
            9012L,
            FsrsFitRunner.FitOperation { _, _ ->
                store.saveFsrsPersonalizationEnabled(false)
                fit
            },
        )

        assertEquals(false, result.adopted)
        assertEquals(FsrsWeightFitter.REASON_DISABLED_DURING_FIT, result.reason)
        assertNull(store.schedulerFsrsWeights())
        val summary = FsrsFitSummaryCodec.decode(store.fsrsFitSummaryJson())!!
        assertEquals(false, summary.adopted)
        assertEquals(FsrsWeightFitter.REASON_DISABLED_DURING_FIT, summary.reason)
    }

    @Test
    fun failedFitRecordsSummaryAndPreservesPreviouslyAdoptedWeights() {
        val existing = customWeights()
        store.saveFsrsPersonalizationEnabled(true)
        store.saveSchedulerFsrsWeights(existing)

        FsrsFitRunner.recordFailure(store, 12_345L)

        assertArrayEquals(existing, store.schedulerFsrsWeights(), 0.0)
        val summary = FsrsFitSummaryCodec.decode(store.fsrsFitSummaryJson())!!
        assertEquals(false, summary.adopted)
        assertEquals(FsrsWeightFitter.REASON_FAILED, summary.reason)
        assertEquals(12_345L, summary.fittedAtMillis)
    }

    @Test
    fun nonFiniteLossesAreOmittedFromPersistedSummary() {
        store.saveFsrsPersonalizationEnabled(true)
        val nonFinite = fitResult(
            customWeights(),
            adopted = false,
            reason = FsrsWeightFitter.REASON_INSUFFICIENT_IMPROVEMENT,
        ).copy(
            defaultTrainingLoss = Double.NaN,
            defaultValidationLoss = Double.POSITIVE_INFINITY,
            fittedTrainingLoss = Double.NEGATIVE_INFINITY,
            fittedValidationLoss = Double.NaN,
        )

        FsrsFitRunner.run(
            store,
            98_765L,
            FsrsFitRunner.FitOperation { _, _ -> nonFinite },
        )

        val summary = FsrsFitSummaryCodec.decode(store.fsrsFitSummaryJson())!!
        assertNull(summary.defaultTrainingLoss)
        assertNull(summary.defaultValidationLoss)
        assertNull(summary.fittedTrainingLoss)
        assertNull(summary.fittedValidationLoss)
        assertEquals(98_765L, summary.fittedAtMillis)
    }

    private fun reviewWith(scheduler: BridgeScheduler): Long {
        val item = RecordsStudyModels.StudyItem(
            "裂", "review", 0L, 5.0, 5.0, 1,
            0, 0, 0, 0, 0, 0L, false, "token", 5,
        ).withRungAndPhase(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.SchedulerPhase.REVIEW)
        val request = RecordsSchedulerModels.ReviewRequest("裂", "token", "good", false, false, false, 0)
        return scheduler.applyReview(item, request, HashSet(), 10L * BridgeScheduler.DAY).item.dueAtMillis
    }

    /**
     * FSRS-7 defaults with the long-term stability increase base at its ceiling.
     *
     * Derived rather than spelled out as 35 literals: FSRS-7's clipper has per-index
     * bounds plus three ordering constraints, and this test cares only that the vector
     * is valid and *not* the default — `adoptedFitPersistsFullPrecisionWeightsUsedByNextScheduler`
     * asserts the fitted vector changes the scheduled due time, which a default-equal
     * vector could not do.
     *
     * w[7] is the long-term `increaseBase`, bounded [0, 4] and defaulting to 2.3054, so
     * 4.0 is a real perturbation with no ordering partner to violate.
     */
    private fun customWeights(): DoubleArray =
        FsrsPersonalization.defaultWeights().also { it[7] = 4.0 }

    private fun fitResult(weights: DoubleArray, adopted: Boolean, reason: String) = FsrsWeightFitter.Result(
        weights = weights,
        sampleCount = 500,
        trainingSampleCount = 400,
        validationSampleCount = 100,
        defaultTrainingLoss = 0.5,
        defaultValidationLoss = 0.5,
        fittedTrainingLoss = 0.45,
        fittedValidationLoss = 0.45,
        adopted = adopted,
        reason = reason,
        epochsCompleted = 4,
    )
}
