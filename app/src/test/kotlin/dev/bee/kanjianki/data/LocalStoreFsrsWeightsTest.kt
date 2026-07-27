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

    @Test
    fun fullPrecisionWeightsRoundTripWithoutPutDoubleRounding() {
        val weights = DoubleArray(21) { index -> 0.1 + index / 1000.0 }.also {
            it[0] = 0.12345678901234567
            it[19] = 0.0658
            it[20] = 0.1542
        }

        store.saveSchedulerFsrsWeights(weights)

        assertArrayEquals(weights, store.schedulerFsrsWeights(), 0.0)
        val raw = store.getStringSetting(FsrsPersonalization.WEIGHTS_SETTING_KEY, "")!!
        assertTrue(raw.startsWith("0.12345678901234566,"))
        assertEquals("0.0658", raw.split(',')[19])
    }

    @Test
    fun malformedWeightsFallBackAndEmitOneSanitizedLinePerRead() {
        val malformed = listOf(
            List(20) { "0.1" }.joinToString(","),
            List(21) { if (it == 0) "NaN" else "0.1" }.joinToString(","),
            List(21) { if (it == 20) "0" else "0.1" }.joinToString(","),
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
        val weights = DoubleArray(21) { 0.1 }.also { it[20] = 0.2 }

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
        val weights = DoubleArray(21) { 0.1 }.also { it[20] = 0.2 }
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
        val weights = DoubleArray(21) { 0.1 }.also { it[20] = 0.2 }
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
