package dev.bee.kanjianki

import android.content.Context
import android.util.Log
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.sync.ManualSyncEngine
import dev.bee.kanjianki.sync.createManualSyncEngine

/**
 * Debug-only fixture for the button-latency benchmark's normal-app routes.
 *
 * Gradle instrumentation seeding is useful for tests, but benchmark runs can
 * reinstall/clear the debug app between the seed step and the smoke command.
 * Seeding from the debug app itself when BENCHMARK_ROUTE is present keeps the
 * live app state deterministic without shipping fixture data in release builds.
 */
internal object ButtonLatencyBenchmarkFixtureSeeder {
    private val representativeDashboardKanji = setOf(
        "認", "改", "善", "統", "計", "復", "習", "集", "書", "字", "記", "録", "検", "索", "類", "似", "未", "熟", "弱", "点", "保", "留",
    )
    private val representativeStudyKanji = setOf("未", "弱", "点")

    @JvmStatic
    fun seedIfNeeded(context: Context, store: LocalStore) {
        val settings = benchmarkSettings()
        val result = createManualSyncEngine(
            context,
            store,
            FixtureGateway(representativeSnapshot(settings)),
            settings,
        ).run()
        check(result.success) { "Button-latency benchmark fixture sync failed: ${result.message}" }
        ensureDueBenchmarkStudyItem(store)
        Log.i(TAG, "seeded button latency fixture: dashboard=${store.dashboardRows().size}, study=${store.studyItems().size}")
    }

    @JvmStatic
    fun representativeDashboardKanji(): Set<String> = representativeDashboardKanji

    @JvmStatic
    fun representativeStudyKanji(): Set<String> = representativeStudyKanji

    @JvmStatic
    fun dueBenchmarkStudyKanji(items: List<RecordsStudyModels.StudyItem>, now: Long): Set<String> {
        return items
            .filter { it.kanji in representativeStudyKanji }
            .filter { it.state != StudyLadderRules.STATE_RETIRED }
            .filter { it.dueAtMillis <= now }
            .map { it.kanji }
            .toSet()
    }

    private fun ensureDueBenchmarkStudyItem(store: LocalStore) {
        val now = System.currentTimeMillis()
        val studyItems = store.studyItems()
        val normalized = studyItems.map { item ->
            if (item.kanji in representativeStudyKanji) {
                item.copyBuilder()
                    .state(StudyLadderRules.STATE_LEARNING)
                    .dueAtMillis((now - 60_000L).coerceAtLeast(0L))
                    .learningStep(0)
                    .suppressedByTaskType("")
                    .suppressedAtMillis(0L)
                    .activeToken(null)
                    .matureIntervalDays(0)
                    .realPassStreak(0)
                    .realAgainStreak(0)
                    .lastRealReviewDueAtMillis(0L)
                    .build()
            } else {
                item
            }
        }
        store.replaceStudyItems(normalized)
    }

    private fun benchmarkSettings(): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
            defaults.modelName,
            defaults.templateName,
            defaults.expressionField,
            defaults.readingField,
            defaults.meaningField,
            defaults.sentenceField,
            defaults.frequencyField,
            defaults.frequencySortField,
            defaults.matureDays,
            defaults.matureSupportThreshold,
            defaults.suspendedRankMin,
            defaults.suspendedRankMax,
            defaults.activeQueueCap,
            defaults.newPerDay,
            defaults.writingTriggerMissDays,
            defaults.recognitionPromotionPasses,
            defaults.realDueReviewsToMove,
            true,
            true,
            false,
            RecordsBase.parseImportTags(""),
            true,
            defaults.importWeakFsrsDifficultyThreshold,
            defaults.importWeakLapsesThreshold,
            1,
            true,
            "deck:Kiku",
        )
    }

    private fun representativeSnapshot(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
        val notes = listOf(
            note(1L, "確認", "かくにん", "confirmation", "確認した。"),
            note(2L, "改善", "かいぜん", "improvement", "速度を改善する。"),
            note(3L, "統計", "とうけい", "statistics", "統計を見る。"),
            note(4L, "復習", "ふくしゅう", "review", "毎日復習する。"),
            note(5L, "集中", "しゅうちゅう", "focus", "学習に集中する。"),
            note(6L, "書字", "しょじ", "handwriting", "書字を練習する。"),
            note(7L, "記録", "きろく", "record", "進捗を記録する。"),
            note(8L, "検索", "けんさく", "search", "漢字を検索する。"),
            note(9L, "類似", "るいじ", "similarity", "類似漢字を比べる。"),
            note(10L, "未熟", "みじゅく", "immature", "未熟なカード。"),
            note(11L, "弱点", "じゃくてん", "weak point", "弱点を直す。"),
            note(12L, "保留", "ほりゅう", "suspended", "保留カード。"),
        )
        val cards = listOf(
            activeCard(101L, 1L, settings.matureDays + 12, 0),
            activeCard(102L, 2L, settings.matureDays + 8, 1),
            activeCard(103L, 3L, settings.matureDays + 6, 0),
            activeCard(104L, 4L, settings.matureDays + 3, 2),
            activeCard(105L, 5L, settings.matureDays + 1, 0),
            activeCard(106L, 6L, settings.matureDays + 2, 1),
            activeCard(107L, 7L, settings.matureDays + 9, 0),
            activeCard(108L, 8L, settings.matureDays + 4, 0),
            activeCard(109L, 9L, settings.matureDays + 7, 1),
            learningCard(110L, 10L),
            activeCard(111L, 11L, 3, 4),
            suspendedCard(112L, 12L),
        )
        return RecordsSyncModels.CollectionSnapshot(notes, cards)
    }

    private fun note(
        id: Long,
        expression: String,
        reading: String,
        meaning: String,
        sentence: String,
    ): RecordsSyncModels.Note {
        val fields = linkedMapOf(
            "Expression" to expression,
            "ExpressionReading" to reading,
            "MainDefinition" to meaning,
            "Sentence" to sentence,
            "Frequency" to "1000",
            "FreqSort" to "1000",
        )
        return RecordsSyncModels.Note(id, "Kiku", fields, emptyList())
    }

    private fun activeCard(cardId: Long, noteId: Long, interval: Int, lapses: Int): RecordsSyncModels.Card {
        return RecordsSyncModels.Card(cardId, noteId, 0, "Kiku", 2, 2, 0, interval, 12, lapses, false)
    }

    private fun learningCard(cardId: Long, noteId: Long): RecordsSyncModels.Card {
        return RecordsSyncModels.Card(cardId, noteId, 0, "Kiku", 1, 1, 0, 1, 12, 1, false)
    }

    private fun suspendedCard(cardId: Long, noteId: Long): RecordsSyncModels.Card {
        return RecordsSyncModels.Card(cardId, noteId, 0, "Kiku", -1, 0, 0, 0, 0, 0, true)
    }

    private class FixtureGateway(
        private val snapshot: RecordsSyncModels.CollectionSnapshot,
    ) : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot = snapshot

        override fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot): AnkiDroidGateway.RemovalSummary {
            return AnkiDroidGateway.RemovalSummary(0, 0, 0, "button latency fixture")
        }
    }

    private const val TAG = "KaniBenchmarkSeed"
}
