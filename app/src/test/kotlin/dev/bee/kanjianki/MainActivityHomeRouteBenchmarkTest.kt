package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.HomeDeckOverview
import dev.bee.kanjianki.core.HomeDeckOverviewPolicy
import dev.bee.kanjianki.core.FocusQueuePolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyCollectionLookup
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.StudyQueueSeeder
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.data.StudyStatsStore
import java.util.Locale
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityHomeRouteBenchmarkTest {
    @Test
    fun benchmarksFocusQueueRowLookupAgainstLegacyRowScan() {
        val nowMillis = 1_725_000_000_000L
        val rows = benchmarkRows(192)
        val items = benchmarkStudyItems(rows, nowMillis)
        val plan = RecordsSchedulerModels.AdaptiveLoadPlan(
            workloadPercent = 55,
            target = 48,
            remaining = 18,
            focusKanji = rows.take(48).map { it.kanji },
            newAdmissionLimit = 12,
            allKanjiMode = false,
            status = "active",
        )
        val ladder = RecordsBase.StudyLadderSettings.defaults()
        val studyAheadMillis = 7 * 24 * 60 * 60 * 1_000L
        val iterations = 400

        val legacySample = legacyQueuedEntries(rows, items, nowMillis, studyAheadMillis, plan, ladder).map { it.row.kanji }
        val optimizedSample = FocusQueuePolicy.queuedEntries(rows, items, nowMillis, studyAheadMillis, plan, ladder).map { it.row.kanji }
        assertEquals(false, legacySample.isEmpty())
        assertEquals(legacySample, optimizedSample)

        var legacyChecksum = 0
        val legacyNanos = measureNanoTime {
            repeat(iterations) {
                val entries = legacyQueuedEntries(rows, items, nowMillis, studyAheadMillis, plan, ladder)
                legacyChecksum += entries.fold(0) { acc, entry ->
                    acc + entry.row.kanji.length + entry.item.kanji.length
                }
            }
        }

        var optimizedChecksum = 0
        val optimizedNanos = measureNanoTime {
            repeat(iterations) {
                val entries = FocusQueuePolicy.queuedEntries(rows, items, nowMillis, studyAheadMillis, plan, ladder)
                optimizedChecksum += entries.fold(0) { acc, entry ->
                    acc + entry.row.kanji.length + entry.item.kanji.length
                }
            }
        }

        assertEquals(legacyChecksum, optimizedChecksum)
        println(
            String.format(
                Locale.ROOT,
                "focus-queue-row-lookup legacy_ms=%.3f legacy_avg_us=%.3f optimized_ms=%.3f optimized_avg_us=%.3f",
                legacyNanos / 1_000_000.0,
                legacyNanos / iterations.toDouble() / 1_000.0,
                optimizedNanos / 1_000_000.0,
                optimizedNanos / iterations.toDouble() / 1_000.0,
            ),
        )
    }

    @Test
    fun benchmarksRecentMistakesMapBuildAgainstPrebuiltLookup() {
        val rows = benchmarkRows(256)
        val mistakes = benchmarkRecentMistakes(96)
        val rowsByKanji = StudyCollectionLookup.dashboardRowsByKanji(rows)
        val iterations = 5_000

        val legacySample = homeRecentMistakesPanelModel(
            mistakes = mistakes,
            rowsByKanji = StudyCollectionLookup.dashboardRowsByKanji(rows),
            onCardClick = {},
        ).cards.map { it.kanji }
        val prebuiltSample = homeRecentMistakesPanelModel(
            mistakes = mistakes,
            rowsByKanji = rowsByKanji,
            onCardClick = {},
        ).cards.map { it.kanji }
        assertEquals(false, legacySample.isEmpty())
        assertEquals(legacySample, prebuiltSample)

        var legacyChecksum = 0
        val legacyNanos = measureNanoTime {
            repeat(iterations) {
                val model = homeRecentMistakesPanelModel(
                    mistakes = mistakes,
                    rowsByKanji = StudyCollectionLookup.dashboardRowsByKanji(rows),
                    onCardClick = {},
                )
                legacyChecksum += model.cards.fold(0) { acc, card ->
                    acc + card.kanji.length + card.title.length + card.subtitle.length
                }
            }
        }

        var prebuiltChecksum = 0
        val prebuiltNanos = measureNanoTime {
            repeat(iterations) {
                val model = homeRecentMistakesPanelModel(
                    mistakes = mistakes,
                    rowsByKanji = rowsByKanji,
                    onCardClick = {},
                )
                prebuiltChecksum += model.cards.fold(0) { acc, card ->
                    acc + card.kanji.length + card.title.length + card.subtitle.length
                }
            }
        }

        assertEquals(legacyChecksum, prebuiltChecksum)
        println(
            String.format(
                Locale.ROOT,
                "recent-mistakes-map-build legacy_ms=%.3f legacy_avg_us=%.3f prebuilt_ms=%.3f prebuilt_avg_us=%.3f",
                legacyNanos / 1_000_000.0,
                legacyNanos / iterations.toDouble() / 1_000.0,
                prebuiltNanos / 1_000_000.0,
                prebuiltNanos / iterations.toDouble() / 1_000.0,
            ),
        )
    }

    @Test
    fun benchmarksHomeDeckOverviewAgainstLegacyActiveFamilyScan() {
        val nowMillis = 1_725_000_000_000L
        val rows = benchmarkRows(256)
        val studyItems = benchmarkHomeDeckOverviewStudyItems(rows, nowMillis)
        val suspendedKanji = rows.filterIndexed { index, _ -> index % 5 == 0 }.map { it.kanji }.toSet()
        val iterations = 1_500

        val legacySample = legacyHomeDeckOverview(studyItems, rows, nowMillis, suspendedKanji)
        val optimizedSample = HomeDeckOverviewPolicy.from(studyItems, rows, nowMillis, suspendedKanji)
        assertEquals(legacySample, optimizedSample)

        var legacyChecksum = 0
        val legacyNanos = measureNanoTime {
            repeat(iterations) {
                val overview = legacyHomeDeckOverview(studyItems, rows, nowMillis, suspendedKanji)
                legacyChecksum += overview.dueCount + overview.newCount + overview.learningCount + overview.relearningCount + overview.suspendedCount + overview.buriedCount
            }
        }

        var optimizedChecksum = 0
        val optimizedNanos = measureNanoTime {
            repeat(iterations) {
                val overview = HomeDeckOverviewPolicy.from(studyItems, rows, nowMillis, suspendedKanji)
                optimizedChecksum += overview.dueCount + overview.newCount + overview.learningCount + overview.relearningCount + overview.suspendedCount + overview.buriedCount
            }
        }

        assertEquals(legacyChecksum, optimizedChecksum)
        println(
            String.format(
                Locale.ROOT,
                "home-deck-overview legacy_ms=%.3f legacy_avg_us=%.3f optimized_ms=%.3f optimized_avg_us=%.3f",
                legacyNanos / 1_000_000.0,
                legacyNanos / iterations.toDouble() / 1_000.0,
                optimizedNanos / 1_000_000.0,
                optimizedNanos / iterations.toDouble() / 1_000.0,
            ),
        )
    }

    @Test
    fun benchmarksRecentMistakeTraceSectionAgainstRepeatedTokenization() {
        val label = "recent-mistake-裂"
        val precomputedTraceSection = buttonTraceSection(label)
        val iterations = 500_000

        var legacyChecksum = 0
        val legacyNanos = measureNanoTime {
            repeat(iterations) {
                legacyChecksum += buttonTraceSection(label).length
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
                "recent-mistake-trace-section legacy_ms=%.3f legacy_avg_us=%.3f precomputed_ms=%.3f precomputed_avg_us=%.3f",
                legacyNanos / 1_000_000.0,
                legacyNanos / iterations.toDouble() / 1_000.0,
                precomputedNanos / 1_000_000.0,
                precomputedNanos / iterations.toDouble() / 1_000.0,
            ),
        )
    }

    @Test
    fun benchmarksHomeActionTraceSectionAgainstRepeatedTokenization() {
        val action = HomeActionModel("Browse", R.drawable.ic_book_24, onClick = {})
        val precomputedTraceSection = action.traceSection
        val iterations = 500_000

        var legacyChecksum = 0
        val legacyNanos = measureNanoTime {
            repeat(iterations) {
                legacyChecksum += buttonTraceSection("home-action-${action.label}").length
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
                "home-action-trace-section legacy_ms=%.3f legacy_avg_us=%.3f precomputed_ms=%.3f precomputed_avg_us=%.3f",
                legacyNanos / 1_000_000.0,
                legacyNanos / iterations.toDouble() / 1_000.0,
                precomputedNanos / 1_000_000.0,
                precomputedNanos / iterations.toDouble() / 1_000.0,
            ),
        )
    }

    @Test
    fun benchmarksHomeSectionHeaderTraceSectionAgainstRepeatedTokenization() {
        val label = "View all"
        val precomputedTraceSection = buttonTraceSection("home-section-header-$label")
        val iterations = 500_000

        var legacyChecksum = 0
        val legacyNanos = measureNanoTime {
            repeat(iterations) {
                legacyChecksum += buttonTraceSection("home-section-header-$label").length
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
                "home-section-header-trace-section legacy_ms=%.3f legacy_avg_us=%.3f precomputed_ms=%.3f precomputed_avg_us=%.3f",
                legacyNanos / 1_000_000.0,
                legacyNanos / iterations.toDouble() / 1_000.0,
                precomputedNanos / 1_000_000.0,
                precomputedNanos / iterations.toDouble() / 1_000.0,
            ),
        )
    }

    private fun legacyQueuedEntries(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        nowMillis: Long,
        studyAheadMillis: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
        ladder: RecordsBase.StudyLadderSettings?,
    ): List<FocusQueuePolicy.QueueEntry> {
        val safeRows = rows
        val activeItems = BridgeScheduler().focusQueueItems(items, safeRows, nowMillis, studyAheadMillis, ladder)
        val focusOrder = focusOrder(plan)
        val entries = ArrayList<FocusQueuePolicy.QueueEntry>()
        for (item in activeItems) {
            val row = safeRows.firstOrNull { it.kanji == item.kanji } ?: continue
            entries.add(FocusQueuePolicy.QueueEntry(row, item))
        }
        entries.sortWith(
            compareBy<FocusQueuePolicy.QueueEntry> { focusOrder.getOrDefault(it.row.kanji, Int.MAX_VALUE) }
                .thenBy { if (it.item.dueAtMillis <= nowMillis) 0 else 1 }
                .thenBy { FocusQueuePolicy.stateRank(it.item.state) }
                .thenBy { it.item.dueAtMillis }
                .thenBy { -it.row.weaknessScore }
                .thenBy { it.row.kanji },
        )
        return entries
    }

    private fun focusOrder(plan: RecordsSchedulerModels.AdaptiveLoadPlan?): Map<String, Int> {
        val focusOrder = HashMap<String, Int>()
        if (plan != null) {
            for (index in plan.focusKanji.indices) {
                focusOrder[plan.focusKanji[index]] = index
            }
        }
        return focusOrder
    }

    private fun benchmarkRows(count: Int): List<RecordsImportModels.DashboardRow> {
        return List(count) { index ->
            val kanji = "字$index"
            RecordsImportModels.DashboardRow(
                kanji,
                index + 1,
                "meaning-$kanji",
                "reading-$kanji",
                "search-$kanji",
                10 + index,
                "reason-$index",
                "reason text $index",
                1 + (index % 3),
                index % 2,
                2 + (index % 4),
                listOf(example("example-$kanji")),
            )
        }
    }

    private fun benchmarkStudyItems(
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
    ): List<RecordsStudyModels.StudyItem> {
        return rows.mapIndexed { index, row ->
            RecordsStudyModels.StudyItem(
                row.kanji,
                StudyLadderRules.STATE_REVIEW,
                nowMillis + ((index % 9) - 4) * 86_400_000L,
                1.0 + index,
                2.0,
                1,
                0,
                0,
                0,
                "",
                nowMillis,
            )
                .copyBuilder()
                .rung(RecordsBase.LadderRung.KANJI_MEANING)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .activeToken("token-${row.kanji}")
                .build()
        }
    }

    private fun benchmarkRecentMistakes(count: Int): List<StudyStatsStore.RecentMistake> {
        return List(count) { index ->
            val kanji = "字$index"
            StudyStatsStore.RecentMistake(
                kanji,
                if (index % 2 == 0) StudyRatings.AGAIN else StudyRatings.GOOD,
                1_725_000_000_000L + index * 60_000L,
            )
        }
    }

    private fun benchmarkHomeDeckOverviewStudyItems(
        rows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
    ): List<RecordsStudyModels.StudyItem> {
        return rows.mapIndexed { index, row ->
            val state = when (index % 4) {
                0 -> StudyLadderRules.STATE_REVIEW
                1 -> StudyLadderRules.STATE_NEW
                else -> StudyLadderRules.STATE_LEARNING
            }
            val phase = if (index % 4 == 2) {
                RecordsBase.SchedulerPhase.NEW_LEARNING
            } else {
                RecordsBase.SchedulerPhase.RELEARNING
            }
            val builder = RecordsStudyModels.StudyItem(
                row.kanji,
                state,
                if (index % 4 == 0) nowMillis - 86_400_000L else nowMillis + 86_400_000L,
                1.0 + index,
                2.0,
                1,
                0,
                0,
                0,
                "",
                nowMillis,
            ).copyBuilder()
                .phase(phase)

            if (index % 2 == 0) {
                builder.answerSignature(StudyQueueSeeder.answerSignature(row))
            } else {
                builder.answerSignature("")
            }
            if (index % 7 == 0) {
                builder.suppressedByTaskType("sync")
            }
            builder.build()
        }
    }

    private fun legacyHomeDeckOverview(
        studyItems: List<RecordsStudyModels.StudyItem>,
        dashboardRows: List<RecordsImportModels.DashboardRow>,
        nowMillis: Long,
        locallySuspendedKanji: Set<String>,
    ): HomeDeckOverview {
        val activeFamilyKeys = dashboardRows
            .asSequence()
            .map { StudyQueueSeeder.rowFamilyKey(it) }
            .toHashSet()
        val activeRows = dashboardRows
            .asSequence()
            .map { it.kanji }
            .toHashSet()

        var dueCount = 0
        var newCount = 0
        var learningCount = 0
        var relearningCount = 0
        var buriedCount = 0

        for (item in studyItems) {
            if (!legacyIsActiveStudyItem(item, activeFamilyKeys, activeRows)) {
                continue
            }
            if (item.suppressedByTaskType.isNotEmpty()) {
                buriedCount++
                continue
            }
            when {
                item.state == StudyLadderRules.STATE_REVIEW && item.dueAtMillis <= nowMillis -> dueCount++
                item.state == StudyLadderRules.STATE_NEW -> newCount++
                item.state == StudyLadderRules.STATE_LEARNING && item.phase == RecordsBase.SchedulerPhase.NEW_LEARNING -> learningCount++
                item.state == StudyLadderRules.STATE_LEARNING && item.phase == RecordsBase.SchedulerPhase.RELEARNING -> relearningCount++
            }
        }

        val suspendedCount = locallySuspendedKanji.count { it in activeRows }

        return HomeDeckOverview(
            dueCount = dueCount,
            newCount = newCount,
            learningCount = learningCount,
            relearningCount = relearningCount,
            suspendedCount = suspendedCount,
            buriedCount = buriedCount,
        )
    }

    private fun legacyIsActiveStudyItem(
        item: RecordsStudyModels.StudyItem,
        activeFamilyKeys: Set<String>,
        activeRows: Set<String>,
    ): Boolean {
        if (StudyQueueSeeder.familyKey(item) in activeFamilyKeys) {
            return true
        }
        return item.answerSignature.isEmpty() && item.kanji in activeRows
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
}
