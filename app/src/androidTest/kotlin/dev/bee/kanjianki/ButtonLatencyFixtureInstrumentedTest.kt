package dev.bee.kanjianki

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.data.LocalStore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val BUTTON_LATENCY_DATABASE_NAME = "kanji_anki_simple.db"

@RunWith(AndroidJUnit4::class)
class ButtonLatencyFixtureInstrumentedTest {
    @Test
    fun seedRepresentativeLocalStoreForButtonLatencyBenchmark() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(BUTTON_LATENCY_DATABASE_NAME)
        LocalStore(context).use { store ->
            ButtonLatencyBenchmarkFixtureSeeder.seedIfNeeded(context, store)
            val dashboardKanji = store.dashboardRows().map { it.kanji }.toSet()
            assertTrue(
                "dashboard rows should include benchmark representative kanji; actual=$dashboardKanji",
                dashboardKanji.containsAll(ButtonLatencyBenchmarkFixtureSeeder.representativeDashboardKanji()),
            )
            val studyKanji = store.studyItems().map { it.kanji }.toSet()
            assertTrue(
                "study items should include benchmark representative study kanji; actual=$studyKanji",
                studyKanji.containsAll(ButtonLatencyBenchmarkFixtureSeeder.representativeStudyKanji()),
            )
            val dueStudyKanji = ButtonLatencyBenchmarkFixtureSeeder.dueBenchmarkStudyKanji(
                store.studyItems(),
                System.currentTimeMillis(),
            )
            assertTrue("study benchmark should have at least one due card for the Reveal path; actual=$dueStudyKanji", dueStudyKanji.isNotEmpty())
        }
        benchmarkHoldMillis()?.takeIf { it > 0L }?.let { Thread.sleep(it) }
    }

    private fun benchmarkHoldMillis(): Long? {
        return InstrumentationRegistry.getArguments().getString("hold_ms")?.toLongOrNull()
    }
}
