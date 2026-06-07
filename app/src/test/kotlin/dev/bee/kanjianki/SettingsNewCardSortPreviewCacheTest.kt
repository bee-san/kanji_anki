package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsImportModels
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale
import kotlin.system.measureNanoTime

class SettingsNewCardSortPreviewCacheTest {
    @Test
    fun reusesPreviewRowsForTheSameDashboardRowSnapshot() {
        val rows = dashboardRows(120)

        // Warm the JIT so the measured timings reflect the preview work rather than class loading.
        SettingsNewCardSortPreviewCache.buildPreviewRowsByMode(rows)

        val baselineIterations = 25
        val hitIterations = 500_000

        val baselineNanos = measureNanoTime {
            repeat(baselineIterations) {
                SettingsNewCardSortPreviewCache.buildPreviewRowsByMode(rows)
            }
        }

        val cachedSnapshot = SettingsNewCardSortPreviewCache.resolve(rows, null)
        val afterNanos = measureNanoTime {
            repeat(hitIterations) {
                assertSame(cachedSnapshot, SettingsNewCardSortPreviewCache.resolve(rows, cachedSnapshot))
            }
        }

        val baselineMs = baselineNanos / 1_000_000.0
        val afterMs = afterNanos / 1_000_000.0
        val baselineAvgUs = baselineNanos / baselineIterations.toDouble() / 1_000.0
        val afterAvgUs = afterNanos / hitIterations.toDouble() / 1_000.0
        println(
            String.format(
                Locale.ROOT,
                "settings-new-card-sort baseline_ms=%.3f baseline_avg_us=%.3f hit_ms=%.3f hit_avg_us=%.6f",
                baselineMs,
                baselineAvgUs,
                afterMs,
                afterAvgUs,
            ),
        )

        val changedRows = rows + dashboardRow(999)
        val rebuiltSnapshot = SettingsNewCardSortPreviewCache.resolve(changedRows, cachedSnapshot)
        assertNotSame(cachedSnapshot, rebuiltSnapshot)
    }

    private fun dashboardRows(count: Int): List<RecordsImportModels.DashboardRow> {
        return List(count) { index -> dashboardRow(index) }
    }

    private fun dashboardRow(index: Int): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            "字$index",
            if (index % 4 == 0) null else index + 1,
            "meaning $index",
            "reading $index",
            "browser $index",
            index % 13,
            "reason-${index % 3}",
            "reason text $index",
            1,
            0,
            0,
            listOf(example(index)),
        )
    }

    private fun example(index: Int): RecordsImportModels.Example {
        val difficulty = 1.0 + (index % 7)
        val retrievability = 0.1 + (index % 8) * 0.1
        return RecordsImportModels.Example(
            "active",
            index.toLong() + 1,
            index.toLong() + 10_000,
            "expr-$index",
            "read-$index",
            "meaning-$index",
            "sentence-$index",
            index % 2 == 0,
            index % 5,
            index + 1,
            (index % 6) + 1,
            2.5 + index,
            difficulty,
            retrievability,
        )
    }
}