package dev.bee.kanjianki

import java.util.Locale
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonRowChunkBenchmarkTest {
    @Test
    fun benchmarksStudyChoiceRowChunkingAgainstPrebuiltRows() {
        benchmarkRowChunking(
            label = "study-choice-row-chunking",
            values = List(17) { index -> "choice-$index" },
            iterations = 500_000,
        )
    }

    @Test
    fun benchmarksHomeActionRowChunkingAgainstPrebuiltRows() {
        benchmarkRowChunking(
            label = "home-action-row-chunking",
            values = List(9) { index -> "home-action-$index" },
            iterations = 500_000,
        )
    }

    @Test
    fun benchmarksReminderPresetRowChunkingAgainstPrebuiltRows() {
        benchmarkRowChunking(
            label = "reminder-preset-row-chunking",
            values = List(12) { index -> "preset-$index" },
            iterations = 500_000,
        )
    }

    private fun benchmarkRowChunking(
        label: String,
        values: List<String>,
        iterations: Int,
    ) {
        val precomputedRows = values.chunked(2)
        assertEquals(values, precomputedRows.flatten())
        assertEquals(values.chunked(2), precomputedRows)

        var legacyChecksum = 0
        val legacyNanos = measureNanoTime {
            repeat(iterations) {
                val rows = values.chunked(2)
                legacyChecksum += rows.fold(0) { acc, row -> acc + row.size }
            }
        }

        var prebuiltChecksum = 0
        val prebuiltNanos = measureNanoTime {
            repeat(iterations) {
                prebuiltChecksum += precomputedRows.fold(0) { acc, row -> acc + row.size }
            }
        }

        assertEquals(legacyChecksum, prebuiltChecksum)
        println(
            String.format(
                Locale.ROOT,
                "%s legacy_ms=%.3f legacy_avg_us=%.3f prebuilt_ms=%.3f prebuilt_avg_us=%.3f",
                label,
                legacyNanos / 1_000_000.0,
                legacyNanos / iterations.toDouble() / 1_000.0,
                prebuiltNanos / 1_000_000.0,
                prebuiltNanos / iterations.toDouble() / 1_000.0,
            ),
        )
    }
}
