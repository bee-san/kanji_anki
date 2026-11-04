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
        // One discarded warmup, then measure — the same defect the forecast budget below
        // had. A single cold run charges this budget for JIT compilation of the whole
        // precompute path plus Robolectric's first touch of the SQLite layer.
        //
        // Repeating `refresh` is safe: it recomputes from the store and republishes the
        // cache row for the timestamp it is given, so a second call with the same
        // `generatedAtMillis` produces the same snapshot rather than accumulating state.
        // The assertions below check that the *measured* run produced a valid snapshot.
        StatsPrecomputeStore(store).refresh(generatedAtMillis = 44_444L)

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

        // One discarded warmup, then the fastest of several samples.
        //
        // Measured 2026-08-10 on an idle Cloud Desktop, printing every sample: the
        // forecast itself runs in **~830ms** and the minimum is stable to within 3%
        // ([851, 824, 828]). So the 5s budget has ~6x headroom on the work it measures,
        // and a failure at 5s is not this code getting slower — it is the machine. The
        // one SonarQube failure after the warmup fix means work that takes 830ms here
        // took over 5000ms there, a 6x slowdown from CPU starvation while a Sonar scan
        // runs alongside it.
        //
        // The budget is therefore NOT raised: 5s vs 830ms is already the right shape for
        // catching an order-of-magnitude regression, which is the only thing a
        // wall-clock gate can honestly detect. What is added is tolerance for a starved
        // runner — more samples, so one descheduled window cannot decide the outcome.
        // Raising the number instead would weaken the gate everywhere to accommodate one
        // job's scheduling.
        runForecast()

        var fastestElapsed = Long.MAX_VALUE
        repeat(SAMPLE_COUNT) {
            fastestElapsed = minOf(fastestElapsed, measureTimeMillis { runForecast() })
        }
        assertEquals(200, forecast.totalItems)
        assertTrue("fastest 200-item ladder forecast took ${fastestElapsed}ms", fastestElapsed < 5_000L)
    }

    private companion object {
        /**
         * Samples taken before the minimum is believed.
         *
         * More than three because the minimum is what defends against a descheduled
         * window: at ~830ms per sample this costs well under a second in total, and it
         * makes a single starved interval unable to fail the gate on its own.
         */
        const val SAMPLE_COUNT = 5
    }
}
