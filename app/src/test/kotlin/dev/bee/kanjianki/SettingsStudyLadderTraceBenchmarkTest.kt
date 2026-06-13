package dev.bee.kanjianki

import dev.bee.kanjianki.core.SettingsTextCopy
import java.util.Locale
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStudyLadderTraceBenchmarkTest {
    @Test
    fun benchmarksPrecomputedLadderTraceSectionAgainstRepeatedTokenization() {
        val label = SettingsTextCopy.restoreDefaultLadderLabel()
        val precomputedTraceSection = "kani.button.${traceToken(label)}"
        val iterations = 500_000

        var legacyChecksum = 0
        val legacyNanos = measureNanoTime {
            repeat(iterations) {
                legacyChecksum += "kani.button.${traceToken(label)}".length
            }
        }

        var precomputedChecksum = 0
        val precomputedNanos = measureNanoTime {
            repeat(iterations) {
                precomputedChecksum += precomputedTraceSection.length
            }
        }

        assertEquals(legacyChecksum, precomputedChecksum)
        println(
            String.format(
                Locale.ROOT,
                "settings-ladder-trace-section legacy_ms=%.3f legacy_avg_us=%.3f precomputed_ms=%.3f precomputed_avg_us=%.3f",
                legacyNanos / 1_000_000.0,
                legacyNanos / iterations.toDouble() / 1_000.0,
                precomputedNanos / 1_000_000.0,
                precomputedNanos / iterations.toDouble() / 1_000.0,
            ),
        )
    }
}
