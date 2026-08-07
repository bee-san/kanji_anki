package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.LadderCompletionForecastPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import kotlin.system.measureTimeMillis
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsPrecomputePerformanceSmokeTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun refreshDirectlyProducesCurrentCacheWithoutLegacyModelBuilder() {
        lateinit var snapshot: StatsCacheStore.Snapshot
        val elapsed = measureTimeMillis {
            snapshot = StatsPrecomputeStore(store).refresh(generatedAtMillis = 44_444L)
        }
        assertEquals(STATS_CACHE_FORMAT_VERSION, snapshot.cacheFormatVersion)
        assertEquals(STATS_REVIEW_DAY_SUMMARY_LIMIT, snapshot.reviewDaySummaries.size)
        assertTrue("empty-store stats refresh took ${elapsed}ms", elapsed < 2_000L)
    }

    @Test
    fun twoHundredItemForecastStaysWithinJvmBudget() {
        val rows = (0 until 200).map { index ->
            RecordsImportModels.DashboardRow(
                String(Character.toChars(0x4E00 + index)), null, "meaning", "reading", "",
                20, "weak", "Needs practice", 1, 0, 0, emptyList<RecordsImportModels.Example>(),
            )
        }
        lateinit var forecast: LadderCompletionForecastPolicy.Forecast
        fun runForecast() {
            forecast = LadderCompletionForecastPolicy.forecast(
                rows, emptyList(), RecordsSyncModels.Settings.kikuDefaults(),
                RecordsSchedulerModels.SchedulerParameters.defaults(),
                RecordsSchedulerModels.LearningStepSettings.defaults(),
                RecordsBase.StudyLadderSettings.defaults(),
                nowMillis = 1_700_000_000_000L,
            )
        }

        // One discarded warmup, then measure. The previous version timed all three runs
        // and took the fastest, but the *first* run carries this test's JIT compilation
        // and Robolectric class loading — on a loaded shared runner that first run can
        // be several times the steady-state cost, and when it dominated the minimum the
        // gate failed for reasons that were not Kani's. This was observed twice in ten
        // SonarQube runs while `Android CI` passed the same commits.
        //
        // The budget stays at 5s deliberately. It is not raised to make a run pass; the
        // measurement is corrected so 5s means what it was chosen to mean.
        runForecast()

        var fastestElapsed = Long.MAX_VALUE
        repeat(3) {
            fastestElapsed = minOf(fastestElapsed, measureTimeMillis { runForecast() })
        }
        assertEquals(200, forecast.totalItems)
        assertTrue("fastest 200-item ladder forecast took ${fastestElapsed}ms", fastestElapsed < 5_000L)
    }
}
