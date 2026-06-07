package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsImportModels
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNewCardSortPreviewCacheTest {
    @Test
    fun reusesPreviewRowsAndWarningsForTheSameDashboardRowSnapshot() {
        val rows = dashboardRows(120)
        val similarityChecks = AtomicInteger(0)
        val baselineIterations = 25
        val hitIterations = 500_000

        val baselineNanos = measureNanoTime {
            repeat(baselineIterations) {
                SettingsNewCardSortPreviewCache.resolve(rows, null) { _, _ ->
                    similarityChecks.incrementAndGet()
                    true
                }
            }
        }

        // Build the first snapshot with a similarity checker that always reports nearby pairs.
        val cachedSnapshot = SettingsNewCardSortPreviewCache.resolve(rows, null) { _, _ ->
            similarityChecks.incrementAndGet()
            true
        }
        val checksAfterBuild = similarityChecks.get()

        assertTrue(cachedSnapshot.previewWarningsByMode.isNotEmpty())

        val hitNanos = measureNanoTime {
            repeat(hitIterations) {
                assertSame(
                    cachedSnapshot,
                    SettingsNewCardSortPreviewCache.resolve(rows, cachedSnapshot) { _, _ ->
                        error("cache hit should not rebuild preview warnings")
                    },
                )
            }
        }
        assertEquals(checksAfterBuild, similarityChecks.get())

        val changedRows = rows + dashboardRow(999)
        val rebuiltSnapshot = SettingsNewCardSortPreviewCache.resolve(changedRows, cachedSnapshot) { _, _ ->
            similarityChecks.incrementAndGet()
            true
        }
        assertNotSame(cachedSnapshot, rebuiltSnapshot)
        assertTrue(similarityChecks.get() > checksAfterBuild)

        println(
            String.format(
                Locale.ROOT,
                "settings-new-card-sort baseline_ms=%.3f baseline_avg_us=%.3f hit_ms=%.3f hit_avg_us=%.6f",
                baselineNanos / 1_000_000.0,
                baselineNanos / baselineIterations.toDouble() / 1_000.0,
                hitNanos / 1_000_000.0,
                hitNanos / hitIterations.toDouble() / 1_000.0,
            ),
        )
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