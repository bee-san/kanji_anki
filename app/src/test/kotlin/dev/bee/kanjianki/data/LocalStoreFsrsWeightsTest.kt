package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.FsrsPersonalization
import dev.bee.kanjianki.core.FsrsWeightFitter
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreFsrsWeightsTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        ShadowLog.clear()
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        ShadowLog.clear()
    }

    /**
     * Valid FSRS-7 weights, built by projecting through the fitter's own bounds.
     *
     * Not a hand-written literal: FSRS-7's clipper has 35 per-index bounds plus three
     * ordering constraints whose bound is another parameter, so an arbitrary
     * `DoubleArray(35) { 0.1 }` is rejected. Deriving them here means these storage
     * tests exercise persistence rather than re-encoding the engine's bounds table in
     * a third place.
     */
    private fun validWeights(mutate: (DoubleArray) -> Unit = {}): DoubleArray =
        FsrsWeightFitter.projectIntoBounds(FsrsPersonalization.defaultWeights().also(mutate))

    @Test
    fun fullPrecisionWeightsRoundTripWithoutPutDoubleRounding() {
        val weights = validWeights {
            // In bounds for w[9] ([0.3, 3.0]) and w[26] ([0.0, 1.0]) respectively, and
            // carrying more precision than SettingsRepository.putDouble's four decimals
            // could survive — which is what this test is about.
            it[9] = 1.2345678901234567
            it[26] = 0.0658
        }

        store.saveSchedulerFsrsWeights(weights)

        assertArrayEquals(weights, store.schedulerFsrsWeights(), 0.0)
        val raw = store.getStringSetting(FsrsPersonalization.WEIGHTS_SETTING_KEY, "")!!
        assertEquals(FsrsPersonalization.PARAMETER_COUNT, raw.split(',').size)
        assertEquals("1.2345678901234567", raw.split(',')[9])
        assertEquals("0.0658", raw.split(',')[26])
    }

    @Test
    fun malformedWeightsFallBackAndEmitOneSanitizedLinePerRead() {
        val valid = FsrsPersonalization.defaultWeights()
        val malformed = listOf(
            // A 21-long vector: the shape a device stored before the FSRS-7 switch.
            // This is the case a real upgrade hits, so it is first — it must fall open
            // to defaults with one log line rather than crash a read.
            List(21) { "0.1" }.joinToString(","),
            List(34) { "0.1" }.joinToString(","),
            valid.clone().also { it[0] = Double.NaN }.joinToString(","),
            // w[25] has an inclusive lower bound of 2.5.
            valid.clone().also { it[25] = 0.0 }.joinToString(","),
        )
        malformed.forEach { encoded ->
            ShadowLog.clear()
            store.putStringSetting(FsrsPersonalization.WEIGHTS_SETTING_KEY, encoded)
            assertNull(store.schedulerFsrsWeights())
            val logs = ShadowLog.getLogsForTag("LocalStoreStudySettings")
            assertEquals(1, logs.size)
            assertEquals("Invalid scheduler_fsrs_weights; using FSRS defaults.", logs.single().msg)
            assertFalse(logs.single().msg.contains(encoded))
        }
    }

    @Test
    fun personalizationIsEnabledByDefault() {
        assertTrue(store.fsrsPersonalizationEnabled())
    }

    @Test
    fun freshInstallAdoptsSuccessfulPersonalizedFit() {
        val weights = validWeights()

        val adopted = store.commitFsrsFitOutcome(
            weightsToAdopt = weights,
            summaryJson = "{\"adopted\":true}",
            disabledSummaryJson = "{\"adopted\":false}",
            preserveExistingWeights = false,
        )

        assertTrue(adopted)
        assertArrayEquals(weights, store.schedulerFsrsWeights(), 0.0)
    }

    @Test
    fun toggleOffAndResetClearOnlyPersonalizationState() {
        val weights = validWeights()
        store.saveFsrsPersonalizationEnabled(true)
        store.saveSchedulerFsrsWeights(weights)
        store.saveFsrsFitSummaryJson("{\"adopted\":true}")
        assertTrue(store.fsrsPersonalizationEnabled())

        store.saveFsrsPersonalizationEnabled(false)
        assertFalse(store.fsrsPersonalizationEnabled())
        assertNull(store.schedulerFsrsWeights())
        assertTrue(store.fsrsFitSummaryJson().isNotEmpty())

        store.resetFsrsPersonalization()
        assertEquals("", store.fsrsFitSummaryJson())
    }

    @Test
    fun liveWeightChangesInvalidateTheForecastCache() {
        val weights = validWeights()
        val cache = StatsCacheStore(store)
        val before = cache.currentSourceVersion()

        store.saveSchedulerFsrsWeights(weights)
        val afterAdoption = cache.currentSourceVersion()
        assertTrue(afterAdoption > before)

        store.saveFsrsPersonalizationEnabled(false)
        val afterOptOut = cache.currentSourceVersion()
        assertTrue(afterOptOut > afterAdoption)

        store.resetFsrsPersonalization()
        assertTrue(cache.currentSourceVersion() > afterOptOut)
    }
}
