package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import java.io.StringReader
import java.util.LinkedHashMap

class KanjiImportSelectorTest {
    @Test
    fun defaultsIgnoreActiveCardsInsideRankRange() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = ranks("裂,1500\n謎,1600\n")
        val snapshot = snapshot(
            listOf(note(1, "裂ける", "さける"), note(2, "謎", "なぞ")),
            listOf(card(10, 1, false), card(20, 2, true))
        )

        val imports = KanjiImportSelector(ranks, settings.suspendedRankMin, settings.suspendedRankMax).importFrom(snapshot, settings)

        assertEquals(listOf("謎"), kanjiList(imports))
        assertTrue(imports[0].sources[0].forcePractice)
    }

    @Test
    fun defaultsImportSuspendedCardsInsideRankRange() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = ranks("謎,1600\n遅,3001\n")
        val snapshot = snapshot(
            listOf(note(1, "謎", "なぞ"), note(2, "遅い", "おそい")),
            listOf(card(10, 1, true), card(20, 2, true))
        )

        val imports = KanjiImportSelector(ranks, settings.suspendedRankMin, settings.suspendedRankMax).importFrom(snapshot, settings)

        assertEquals(listOf("謎"), kanjiList(imports))
        assertEquals(1600, imports[0].jitenRank)
    }

    @Test
    fun suspendedOnlyExcludesActiveCards() {
        val settings = settings(false, true, false, "", false, 7.0, 2, 1)
        val ranks = ranks("裂,1500\n謎,1600\n")
        val snapshot = snapshot(
            listOf(note(1, "裂ける", "さける"), note(2, "謎", "なぞ")),
            listOf(card(10, 1, false), card(20, 2, true))
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("謎"), kanjiList(imports))
    }

    @Test
    fun taggedCardsCombineWithSuspendedCardsUsingAnyMatchLogic() {
        val settings = settings(false, true, true, "wani target", false, 7.0, 2, 1)
        val ranks = ranks("裂,1500\n謎,1600\n外,1700\n")
        val snapshot = snapshot(
            listOf(
                note(1, "裂ける", "さける", "target"),
                note(2, "謎", "なぞ"),
                note(3, "外", "そと")
            ),
            listOf(card(10, 1, false), card(20, 2, true), card(30, 3, false))
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("裂", "謎"), kanjiList(imports))
        assertTrue(imports[0].sources[0].forcePractice)
        assertEquals(listOf(RecordsBase.SOURCE_TAGGED), imports[0].sources[0].ruleTypes)
    }

    @Test
    fun weakCardsMatchByFsrsDifficulty() {
        val settings = settings(false, false, false, "", true, 7.0, 2, 1)
        val ranks = ranks("弱,1500\n強,1600\n")
        val snapshot = snapshot(
            listOf(note(1, "弱点", "じゃくてん"), note(2, "強い", "つよい")),
            listOf(card(10, 1, false, 0, 8.0), card(20, 2, false, 0, 4.0))
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("弱"), kanjiList(imports))
        assertEquals(listOf(RecordsBase.SOURCE_WEAK), imports[0].sources[0].ruleTypes)
    }

    @Test
    fun activeOptInMatchesKeepActiveSourceTypeAndRuleProvenance() {
        val settings = settings(true, false, true, "focus", true, 7.0, 2, 1)
        val ranks = ranks("裂,1500\n")
        val snapshot = snapshot(
            listOf(note(1, "裂ける", "さける", "focus")),
            listOf(card(10, 1, false, 2, 8.0))
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("裂"), kanjiList(imports))
        val source = imports[0].sources[0]
        assertEquals(RecordsBase.SOURCE_ACTIVE, source.sourceType)
        assertFalse(source.suspended)
        assertTrue(source.forcePractice)
        assertEquals(
            listOf(
                RecordsBase.SOURCE_ACTIVE,
                RecordsBase.SOURCE_TAGGED,
                RecordsBase.SOURCE_WEAK
            ),
            source.ruleTypes
        )
    }

    @Test
    fun weakCardsMatchByLapses() {
        val settings = settings(false, false, false, "", true, 9.0, 2, 1)
        val ranks = ranks("浅,1500\n深,1600\n")
        val snapshot = snapshot(
            listOf(note(1, "浅い", "あさい"), note(2, "深い", "ふかい")),
            listOf(card(10, 1, false, 2, 3.0), card(20, 2, false, 1, 3.0))
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("浅"), kanjiList(imports))
    }

    @Test
    fun weakCardsIgnoreMissingDifficultyAndLowLapses() {
        val settings = settings(false, false, false, "", true, 7.0, 2, 1)
        val ranks = ranks("浅,1500\n深,1600\n")
        val snapshot = snapshot(
            listOf(note(1, "浅い", "あさい"), note(2, "深い", "ふかい")),
            listOf(card(10, 1, false, 1, null), card(20, 2, false, 0, 3.0))
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertTrue(imports.isEmpty())
    }

    @Test
    fun rankRangeFiltersImportedKanji() {
        val settings = settings(true, true, false, "", false, 7.0, 2, 1)
        val ranks = ranks("日,1\n示,100\n裂,3000\n遅,3001\n")
        val snapshot = snapshot(
            listOf(note(1, "日示裂遅", "にち")),
            listOf(card(10, 1, false))
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("示", "裂"), kanjiList(imports))
    }

    @Test
    fun equalRanksSortByKanji() {
        val settings = settings(true, false, false, "", false, 7.0, 2, 1)
        val ranks = ranks("謎,1500\n裂,1500\n")
        val snapshot = snapshot(
            listOf(note(1, "謎裂", "なぞ")),
            listOf(card(10, 1, false))
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("裂", "謎"), kanjiList(imports))
    }

    @Test
    fun minimumMatchingCardsCountsUniqueSourceCardsPerKanji() {
        val settings = settings(true, true, false, "", false, 7.0, 2, 2)
        val ranks = ranks("裂,1500\n謎,1600\n")
        val snapshot = snapshot(
            listOf(note(1, "裂ける謎", "さける"), note(2, "裂傷", "れっしょう")),
            listOf(card(10, 1, false), card(20, 2, false))
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("裂"), kanjiList(imports))
        assertEquals(2, imports[0].sources.size)
    }

    @Test
    fun constructorsNormalizeBoundsAndOneArgUsesDefaultMinimum() {
        val settings = settings(true, false, false, "", false, 7.0, 2, 1)
        val ranks = ranks("示,100\n裂,1500\n遅,3001\n")
        val snapshot = snapshot(
            listOf(note(1, "示裂遅", "しめす")),
            listOf(card(10, 1, false))
        )

        val swapped = KanjiImportSelector(ranks, 3000, 100).importFrom(snapshot, settings)
        val oneArg = KanjiImportSelector(ranks, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("示", "裂"), kanjiList(swapped))
        assertEquals(listOf("示", "裂"), kanjiList(oneArg))
    }

    @Test
    fun nullDisabledMissingAndUnmatchedInputsReturnNoImports() {
        val ranks = ranks("裂,1500\n外,1600\n")
        val disabled = settings(false, false, false, "", false, 7.0, 2, 1)
        val taggedOnly = settings(false, false, true, "target", false, 7.0, 2, 1)
        val selector = KanjiImportSelector(ranks, 100, 3000)

        assertTrue(selector.importFrom(null, RecordsSyncModels.Settings.kikuDefaults()).isEmpty())
        assertTrue(selector.importFrom(snapshot(emptyList(), emptyList()), null).isEmpty())
        assertTrue(selector.importFrom(snapshot(listOf(note(1, "裂", "れつ")), listOf(card(10, 1, false))), disabled).isEmpty())
        assertTrue(selector.importFrom(snapshot(listOf(note(1, "裂", "れつ")), listOf(card(10, 99, false))), RecordsSyncModels.Settings.kikuDefaults()).isEmpty())
        assertTrue(selector.importFrom(snapshot(listOf(note(1, "裂", "れつ")), listOf(card(10, 1, false))), taggedOnly).isEmpty())
        assertTrue(selector.importFrom(snapshot(listOf(note(1, "裂", "れつ", "other")), listOf(card(10, 1, false))), taggedOnly).isEmpty())
        assertTrue(selector.importFrom(snapshot(listOf(note(1, "裂", "れつ")), listOf(card(10, 1, false).withBrowserQueryMatched(true))), settingsWithBrowserQuery(false, false, false, "query")).isEmpty())
        assertTrue(selector.importFrom(snapshot(listOf(note(1, "裂", "れつ")), listOf(card(10, 1, false).withBrowserQueryMatched(true))), settingsWithBrowserQuery(false, false, false, "query", 2)).isEmpty())
    }

    @Test
    fun defaultSettingsIgnoreBrowserQueryMatchedActiveCard() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = ranks("裂,1500\n")
        val queryMatchedActive = card(10, 1, false).withBrowserQueryMatched(true)
        val snapshot = snapshot(
            listOf(note(1, "裂ける", "さける")),
            listOf(queryMatchedActive)
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertTrue(imports.isEmpty())
    }

    @Test
    fun activeOnlyImportDoesNotForcePractice() {
        val settings = settings(true, false, false, "", false, 7.0, 2, 1)
        val ranks = ranks("裂,1500\n")
        val snapshot = snapshot(
            listOf(note(1, "裂ける", "さける")),
            listOf(card(10, 1, false))
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("裂"), kanjiList(imports))
        val source = imports[0].sources[0]
        assertEquals(RecordsBase.SOURCE_ACTIVE, source.sourceType)
        assertFalse(source.suspended)
        assertFalse(source.forcePractice)
        assertEquals(listOf(RecordsBase.SOURCE_ACTIVE), source.ruleTypes)
    }

    @Test
    fun browserQueryEnabledImportsActiveCardMarkedAsMatched() {
        val settings = settingsWithBrowserQuery(false, false, true, "tag:kani")
        val ranks = ranks("裂,1500\n")
        val queryMatchedActive = card(10, 1, false).withBrowserQueryMatched(true)
        val snapshot = snapshot(
            listOf(note(1, "裂ける", "さける")),
            listOf(queryMatchedActive)
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("裂"), kanjiList(imports))
        assertEquals(RecordsBase.SOURCE_BROWSER_QUERY, imports[0].sources[0].sourceType)
        assertEquals(listOf(RecordsBase.SOURCE_BROWSER_QUERY), imports[0].sources[0].ruleTypes)
        assertTrue(imports[0].sources[0].forcePractice)
        assertFalse(imports[0].sources[0].suspended)
    }

    @Test
    fun activeBrowserQueryAndTaggedDedupeSingleCardWithBrowserSourceType() {
        val settings = settingsWithActiveTaggedAndBrowserQuery("target")
        val ranks = ranks("裂,1500\n")
        val queryMatchedActive = card(10, 1, false).withBrowserQueryMatched(true)
        val snapshot = snapshot(
            listOf(note(1, "裂ける", "さける", "target")),
            listOf(queryMatchedActive)
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("裂"), kanjiList(imports))
        assertEquals(1, imports[0].sources.size)
        val source = imports[0].sources[0]
        assertEquals(RecordsBase.SOURCE_BROWSER_QUERY, source.sourceType)
        assertTrue(source.forcePractice)
        assertEquals(
            listOf(
                RecordsBase.SOURCE_ACTIVE,
                RecordsBase.SOURCE_TAGGED,
                RecordsBase.SOURCE_BROWSER_QUERY
            ),
            source.ruleTypes
        )
    }

    @Test
    fun browserQueryMatchedSuspendedCardRetainsSuspendedSourceType() {
        val settings = settingsWithBrowserQuery(false, true, true, "tag:kani")
        val ranks = ranks("謎,1600\n")
        val queryMatchedSuspended = card(20, 2, true).withBrowserQueryMatched(true)
        val snapshot = snapshot(
            listOf(note(2, "謎", "なぞ")),
            listOf(queryMatchedSuspended)
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("謎"), kanjiList(imports))
        assertEquals(RecordsBase.SOURCE_SUSPENDED, imports[0].sources[0].sourceType)
        assertTrue(imports[0].sources[0].suspended)
        assertTrue(imports[0].sources[0].forcePractice)
    }

    @Test
    fun browserQueryMatchedCardsCountTowardMinimumThreshold() {
        val settings = settingsWithBrowserQuery(false, false, true, "tag:kani", 2)
        val ranks = ranks("裂,1500\n")
        val queryMatched1 = card(10, 1, false).withBrowserQueryMatched(true)
        val queryMatched2 = card(20, 2, false).withBrowserQueryMatched(true)
        val snapshot = snapshot(
            listOf(note(1, "裂ける", "さける"), note(2, "裂傷", "れっしょう")),
            listOf(queryMatched1, queryMatched2)
        )

        val imports = KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings)

        assertEquals(listOf("裂"), kanjiList(imports))
        assertEquals(2, imports[0].sources.size)
    }

    private fun ranks(csv: String): JitenKanjiRanks {
        return JitenKanjiRanks.parseCsv(StringReader(csv))
    }

    private fun snapshot(notes: List<RecordsSyncModels.Note>, cards: List<RecordsSyncModels.Card>): RecordsSyncModels.CollectionSnapshot {
        return RecordsSyncModels.CollectionSnapshot(notes, cards)
    }

    private fun note(id: Long, expression: String, reading: String, vararg tags: String): RecordsSyncModels.Note {
        val fields = LinkedHashMap<String, String>()
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        fields[settings.expressionField] = expression
        fields[settings.readingField] = reading
        fields[settings.meaningField] = "meaning"
        fields[settings.sentenceField] = expression + " sentence"
        fields[settings.frequencyField] = "9999"
        fields[settings.frequencySortField] = "9999"
        return RecordsSyncModels.Note(id, "Kiku", fields, tags.toList())
    }

    private fun card(cardId: Long, noteId: Long, suspended: Boolean): RecordsSyncModels.Card {
        return card(cardId, noteId, suspended, 0, null)
    }

    private fun card(cardId: Long, noteId: Long, suspended: Boolean, lapses: Int, fsrsDifficulty: Double?): RecordsSyncModels.Card {
        return RecordsSyncModels.Card(
            cardId,
            noteId,
            0,
            "例文マイニング",
            if (suspended) -1 else 2,
            if (suspended) 3 else 2,
            0,
            if (suspended) 0 else 30,
            3,
            lapses,
            suspended,
            null,
            fsrsDifficulty,
            null
        )
    }

    private fun settings(
        active: Boolean,
        suspended: Boolean,
        tagged: Boolean,
        tags: String,
        weak: Boolean,
        weakDifficulty: Double,
        weakLapses: Int,
        minMatching: Int
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
            tagged,
            RecordsBase.parseImportTags(tags),
            weak,
            weakDifficulty,
            weakLapses,
            minMatching
        )
    }

    private fun settingsWithBrowserQuery(
        active: Boolean,
        suspended: Boolean,
        browserQueryCards: Boolean,
        browserQuery: String
    ): RecordsSyncModels.Settings {
        return settingsWithBrowserQuery(active, suspended, browserQueryCards, browserQuery, 1)
    }

    private fun settingsWithBrowserQuery(
        active: Boolean,
        suspended: Boolean,
        browserQueryCards: Boolean,
        browserQuery: String,
        minMatching: Int
    ): RecordsSyncModels.Settings {
        return settingsWithBrowserQuery(active, suspended, false, "", browserQueryCards, browserQuery, minMatching)
    }

    private fun settingsWithActiveTaggedAndBrowserQuery(tags: String): RecordsSyncModels.Settings {
        return settingsWithBrowserQuery(true, false, true, tags, true, "tag:kani", 1)
    }

    private fun settingsWithBrowserQuery(
        active: Boolean,
        suspended: Boolean,
        tagged: Boolean,
        tags: String,
        browserQueryCards: Boolean,
        browserQuery: String,
        minMatching: Int
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
            tagged,
            RecordsBase.parseImportTags(tags),
            false,
            7.0,
            2,
            minMatching,
            browserQueryCards,
            browserQuery
        )
    }

    private fun kanjiList(imports: List<RecordsImportModels.SuspendedImport>): List<String> {
        val out = ArrayList<String>()
        for (item in imports) {
            out.add(item.kanji)
        }
        return out
    }
}