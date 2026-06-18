package dev.bee.kanjianki

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.sync.ManualSyncEngine
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
            val settings = benchmarkSettings()
            val result = ManualSyncEngine(
                context,
                store,
                FixtureGateway(representativeSnapshot(settings)),
                settings,
            ).run()

            assertTrue(result.success)
            assertTrue(store.dashboardRows().size >= 8)
            assertTrue(store.studyItems().isNotEmpty())
        }
        benchmarkHoldMillis()?.takeIf { it > 0L }?.let { Thread.sleep(it) }
    }

    private fun benchmarkHoldMillis(): Long? {
        return InstrumentationRegistry.getArguments().getString("hold_ms")?.toLongOrNull()
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
}
