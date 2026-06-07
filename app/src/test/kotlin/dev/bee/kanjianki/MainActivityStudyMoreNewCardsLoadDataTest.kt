package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import java.util.Locale
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class MainActivityStudyMoreNewCardsLoadDataTest {
    @Test
    fun resolvesRowsAndExistingOnceWhenSnapshotMissing() {
        val rows = listOf(dashboardRow("字A"), dashboardRow("字B"))
        val existing = listOf(studyItem("字A"), studyItem("字B"))
        var rowsCalls = 0
        var existingCalls = 0
        var requestedKanji: List<String>? = null

        val resolved = resolveStudyMoreNewCardsLoadData(
            snapshot = null,
            loadRows = {
                rowsCalls++
                rows
            },
            loadExisting = { kanji ->
                existingCalls++
                requestedKanji = kanji
                existing
            },
        )

        assertNotNull(resolved)
        assertEquals(1, rowsCalls)
        assertEquals(1, existingCalls)
        assertEquals(listOf("字A", "字B"), requestedKanji)
        assertSame(rows, resolved!!.rows)
        assertSame(existing, resolved.existing)
    }

    @Test
    fun reusesSnapshotWithoutCallingLoaders() {
        val rows = listOf(dashboardRow("字A"), dashboardRow("字B"))
        val existing = listOf(studyItem("字A"), studyItem("字B"))
        val snapshot = MainActivityStudyDoneActions.StudyMoreNewCardsSnapshot(rows, existing)
        var rowsCalls = 0
        var existingCalls = 0

        val resolved = resolveStudyMoreNewCardsLoadData(
            snapshot = snapshot,
            loadRows = {
                rowsCalls++
                error("loadRows should not be called when snapshot is present")
            },
            loadExisting = {
                existingCalls++
                error("loadExisting should not be called when snapshot is present")
            },
        )

        assertNotNull(resolved)
        assertEquals(0, rowsCalls)
        assertEquals(0, existingCalls)
        assertSame(rows, resolved!!.rows)
        assertSame(existing, resolved.existing)
    }

    @Test
    fun benchmarksSnapshotHitAgainstFallbackPath() {
        val rows = listOf(dashboardRow("字A"), dashboardRow("字B"))
        val existing = listOf(studyItem("字A"), studyItem("字B"))
        val baselineIterations = 50_000
        val hitIterations = 500_000

        val baselineNanos = measureNanoTime {
            repeat(baselineIterations) {
                assertNotNull(
                    resolveStudyMoreNewCardsLoadData(
                        snapshot = null,
                        loadRows = { rows },
                        loadExisting = { existing },
                    ),
                )
            }
        }

        val snapshot = MainActivityStudyDoneActions.StudyMoreNewCardsSnapshot(rows, existing)
        val hitNanos = measureNanoTime {
            repeat(hitIterations) {
                assertNotNull(
                    resolveStudyMoreNewCardsLoadData(
                        snapshot = snapshot,
                        loadRows = { error("loadRows should not be called on snapshot hit") },
                        loadExisting = { error("loadExisting should not be called on snapshot hit") },
                    ),
                )
            }
        }

        println(
            String.format(
                Locale.ROOT,
                "study-more-new-cards-load-data baseline_ms=%.3f baseline_avg_us=%.3f hit_ms=%.3f hit_avg_us=%.6f",
                baselineNanos / 1_000_000.0,
                baselineNanos / baselineIterations.toDouble() / 1_000.0,
                hitNanos / 1_000_000.0,
                hitNanos / hitIterations.toDouble() / 1_000.0,
            ),
        )
    }

    @Test
    fun benchmarksCachedAvailabilityHitAgainstRecomputePath() {
        val rows = listOf(dashboardRow("字A"), dashboardRow("字B"))
        val existing = listOf(studyItem("字A"), studyItem("字B"))
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ladder = RecordsBase.StudyLadderSettings.defaults()
        val now = 1_720_000_000_000L
        val startOfDay = now - (now % 86_400_000L)
        val baselineIterations = 20_000
        val hitIterations = 500_000

        val baselineNanos = measureNanoTime {
            repeat(baselineIterations) {
                assertNotNull(
                    resolveStudyMoreNewCardsAvailability(
                        snapshot = null,
                        cachedAvailableCount = null,
                        loadRows = { rows },
                        loadExisting = { existing },
                        countAvailable = { loadData ->
                            BridgeScheduler().countExtraNewCardsAvailable(
                                loadData.rows,
                                loadData.existing,
                                settings,
                                now,
                                startOfDay,
                                ladder,
                            )
                        },
                    ),
                )
            }
        }

        val snapshot = MainActivityStudyDoneActions.StudyMoreNewCardsSnapshot(rows, existing)
        val hitNanos = measureNanoTime {
            repeat(hitIterations) {
                assertNotNull(
                    resolveStudyMoreNewCardsAvailability(
                        snapshot = snapshot,
                        cachedAvailableCount = 7,
                        loadRows = { error("loadRows should not be called on cached availability hit") },
                        loadExisting = { error("loadExisting should not be called on cached availability hit") },
                        countAvailable = { error("countAvailable should not be called on cached availability hit") },
                    ),
                )
            }
        }

        println(
            String.format(
                Locale.ROOT,
                "study-more-new-cards-availability baseline_ms=%.3f baseline_avg_us=%.3f hit_ms=%.3f hit_avg_us=%.6f",
                baselineNanos / 1_000_000.0,
                baselineNanos / baselineIterations.toDouble() / 1_000.0,
                hitNanos / 1_000_000.0,
                hitNanos / hitIterations.toDouble() / 1_000.0,
            ),
        )
    }

    private fun dashboardRow(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            100,
            "meaning-$kanji",
            "reading-$kanji",
            kanji,
            7,
            "reason",
            "reason text",
            1,
            0,
            0,
            listOf(example(kanji)),
        )
    }

    private fun example(expression: String): RecordsImportModels.Example {
        return RecordsImportModels.Example(
            "active",
            1L,
            2L,
            expression,
            "reading",
            "meaning",
            "",
            false,
            0,
        )
    }

    private fun studyItem(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            "review",
            1_000L,
            1.0,
            2.0,
            1,
            0,
            0,
            0,
            "",
            1_000L,
        )
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("token-$kanji")
            .build()
    }
}
