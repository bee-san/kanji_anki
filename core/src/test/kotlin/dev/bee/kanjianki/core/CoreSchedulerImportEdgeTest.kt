package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class CoreSchedulerImportEdgeTest {
    @Test
    fun extraNewCardsOnlyReopensRetiredRowsBelowMatureSupportThreshold() {
        val scheduler = BridgeScheduler()
        val settings = settingsWithMatureSupport(2)
        val rows = listOf(
            row("裂", 1),
            row("語", 2),
            row("新", 0)
        )
        val existing = listOf(
            retiredItem("裂"),
            retiredItem("語")
        )

        val result = scheduler.seedExtraNewCards(
            rows,
            existing,
            settings,
            1000L,
            0L,
            5
        )

        assertEquals(2, result.availableCount)
        assertEquals(listOf("裂", "新"), result.admittedKanji)
        assertEquals("new", studyItem(result.items, "裂").state)
        assertEquals("retired", studyItem(result.items, "語").state)
        assertEquals("new", studyItem(result.items, "新").state)
    }

    @Test
    fun activeQueueFiltersRetiredAndDisallowedItemsButKeepsStaleSuppressionFlags() {
        val scheduler = BridgeScheduler()
        val rows = listOf(
            row("裂", 0),
            row("語", 0),
            row("退", 0),
            row("外", 0)
        )
        val items = listOf(
            reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING),
            reviewItem("語", RecordsBase.LadderRung.KANJI_MEANING).withSuppression(BridgeScheduler.TASK_FONT_MEANING, 1000L, 21),
            retiredItem("退"),
            reviewItem("外", RecordsBase.LadderRung.KANJI_MEANING)
        )

        val unrestricted = scheduler.activeQueueItems(items, rows, 2000L, null)
        // Legacy suppression flags are inert: the flagged item stays visible.
        assertEquals(listOf("外", "裂", "語"), sortedKanji(unrestricted))

        val restricted = scheduler.activeQueueItems(items, rows, 2000L, setOf("裂"))
        assertEquals(listOf("裂"), sortedKanji(restricted))
    }

    @Test
    fun importSelectorRequiresActiveCardsToBeUnsuspendedAndBrowserCardsToMatchQuery() {
        val ranks = JitenKanjiRanks.parseCsv(StringReader("裂,1500\n問,1600\n"))
        val selector = KanjiImportSelector(ranks, 100, 3000)

        val suspendedActiveSource = snapshot(
            listOf(note(1L, "裂ける", "さける")),
            listOf(card(10L, 1L, true))
        )
        assertTrue(selector.importFrom(suspendedActiveSource, settings(true, false, false, false, "")).isEmpty())

        val unmatchedBrowserSource = snapshot(
            listOf(note(2L, "問題", "もんだい")),
            listOf(card(20L, 2L, false).withBrowserQueryMatched(false))
        )
        assertTrue(selector.importFrom(unmatchedBrowserSource, settings(false, false, false, true, "tag:kani")).isEmpty())
    }

    private fun studyItem(items: List<RecordsStudyModels.StudyItem>, kanji: String): RecordsStudyModels.StudyItem {
        for (item in items) {
            if (item.kanji == kanji) {
                return item
            }
        }
        throw AssertionError("Missing study item for $kanji")
    }

    private fun studyItem(
        items: List<RecordsStudyModels.StudyItem>,
        kanji: String,
        rung: RecordsBase.LadderRung
    ): RecordsStudyModels.StudyItem {
        for (item in items) {
            if (item.kanji == kanji && item.rung == rung) {
                return item
            }
        }
        throw AssertionError("Missing study item for $kanji / $rung")
    }

    private fun sortedKanji(items: List<RecordsStudyModels.StudyItem>): List<String> {
        return items.map { it.kanji }.sorted()
    }

    private fun retiredItem(kanji: String): RecordsStudyModels.StudyItem {
        return baseItem(kanji)
            .copyBuilder()
            .state("retired")
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .build()
    }

    private fun reviewItem(kanji: String, rung: RecordsBase.LadderRung): RecordsStudyModels.StudyItem {
        return baseItem(kanji)
            .copyBuilder()
            .state("review")
            .dueAtMillis(0L)
            .stability(1.0)
            .difficulty(5.0)
            .totalReviews(1)
            .rung(rung)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .build()
    }

    private fun baseItem(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            "new",
            0L,
            0.4,
            5.0,
            0,
            0,
            0,
            0,
            0,
            0,
            0L,
            false,
            "",
            0L,
            0,
            "",
            "",
            100L
        )
    }

    private fun row(kanji: String, matureSupportCount: Int): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            1000,
            "meaning",
            "reading",
            kanji,
            1,
            "reason",
            "reason",
            1,
            0,
            matureSupportCount,
            emptyList<RecordsImportModels.Example>()
        )
    }

    private fun settingsWithMatureSupport(matureSupportThreshold: Int): RecordsSyncModels.Settings {
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
            matureSupportThreshold,
            defaults.suspendedRankMin,
            defaults.suspendedRankMax,
            defaults.activeQueueCap,
            defaults.newPerDay,
            defaults.writingTriggerMissDays,
            defaults.recognitionPromotionPasses,
            defaults.realDueReviewsToMove,
            defaults.importActiveCards,
            defaults.importSuspendedCards,
            defaults.importTaggedCards,
            emptyList<String>(),
            defaults.importWeakCards,
            defaults.importWeakFsrsDifficultyThreshold,
            defaults.importWeakLapsesThreshold,
            defaults.importMinMatchingCardsPerKanji,
            defaults.importBrowserQueryCards,
            defaults.importBrowserQuery,
            defaults.newCardSortMode,
            defaults.ladderPromotionIntervalDays,
            defaults.ladderDemotionFailStreak
        )
    }

    private fun snapshot(notes: List<RecordsSyncModels.Note>, cards: List<RecordsSyncModels.Card>): RecordsSyncModels.CollectionSnapshot {
        return RecordsSyncModels.CollectionSnapshot(notes, cards)
    }

    private fun note(id: Long, expression: String, reading: String): RecordsSyncModels.Note {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        val fields = linkedMapOf<String, String>()
        fields[defaults.expressionField] = expression
        fields[defaults.readingField] = reading
        fields[defaults.meaningField] = "meaning"
        fields[defaults.sentenceField] = expression + " sentence"
        fields[defaults.frequencyField] = "9999"
        fields[defaults.frequencySortField] = "9999"
        return RecordsSyncModels.Note(id, defaults.modelName, fields, emptyList<String>())
    }

    private fun card(cardId: Long, noteId: Long, suspended: Boolean): RecordsSyncModels.Card {
        return RecordsSyncModels.Card(
            cardId,
            noteId,
            0,
            "Deck",
            if (suspended) -1 else 2,
            if (suspended) 3 else 2,
            0,
            if (suspended) 0 else 30,
            3,
            0,
            suspended,
            null,
            null,
            null
        )
    }

    private fun settings(
        active: Boolean,
        suspended: Boolean,
        weak: Boolean,
        browserQuery: Boolean,
        query: String
    ): RecordsSyncModels.Settings {
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
            active,
            suspended,
            false,
            emptyList<String>(),
            weak,
            defaults.importWeakFsrsDifficultyThreshold,
            defaults.importWeakLapsesThreshold,
            defaults.importMinMatchingCardsPerKanji,
            browserQuery,
            query
        )
    }
}
