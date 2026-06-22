package dev.bee.kanjianki

import java.util.Locale
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressAnalyticsChunkBenchmarkTest {
    @Test
    fun benchmarksProgressOverviewMetricChunkingAgainstPrebuiltRows() {
        benchmarkRowChunking(
            label = "progress-overview-metric-row-chunking",
            values = List(6) { index -> "progress-metric-$index" },
            chunkSize = 3,
        )
    }

    @Test
    fun benchmarksProgressReviewMetricChunkingAgainstPrebuiltRows() {
        benchmarkRowChunking(
            label = "progress-review-metric-row-chunking",
            values = List(4) { index -> "review-metric-$index" },
            chunkSize = 2,
        )
    }

    @Test
    fun benchmarksProgressMissedKanjiChunkingAgainstPrebuiltRows() {
        benchmarkRowChunking(
            label = "progress-missed-kanji-row-chunking",
            values = List(5) { index -> "missed-kanji-$index" },
            chunkSize = 3,
        )
    }

    private fun benchmarkRowChunking(
        label: String,
        values: List<String>,
        chunkSize: Int,
        iterations: Int = 500_000,
    ) {
        val precomputedRows = values.chunked(chunkSize)
        assertEquals(values, precomputedRows.flatten())

        var legacyChecksum = 0
        val legacyNanos = measureNanoTime {
            repeat(iterations) {
                legacyChecksum += values.chunked(chunkSize).fold(0) { acc, row ->
                    acc + row.size + row.first().length + row.last().length
                }
            }
        }

        var precomputedChecksum = 0
        val precomputedNanos = measureNanoTime {
            repeat(iterations) {
                precomputedChecksum += precomputedRows.fold(0) { acc, row ->
                    acc + row.size + row.first().length + row.last().length
                }
            }
        }

        assertEquals(legacyChecksum, precomputedChecksum)
        println(
            String.format(
                Locale.ROOT,
                "%s legacy_ms=%.3f legacy_avg_us=%.3f precomputed_ms=%.3f precomputed_avg_us=%.3f",
                label,
                legacyNanos / 1_000_000.0,
                legacyNanos / iterations.toDouble() / 1_000.0,
                precomputedNanos / 1_000_000.0,
                precomputedNanos / iterations.toDouble() / 1_000.0,
            ),
        )
    }
}
