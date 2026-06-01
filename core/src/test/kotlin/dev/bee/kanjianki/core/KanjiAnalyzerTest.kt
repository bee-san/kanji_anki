package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import java.io.StringReader
import java.util.LinkedHashMap

class KanjiAnalyzerTest {
    @Test
    fun buildsRowsFromActiveAndSuspendedCollectionEvidence() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = JitenKanjiRanks.parseCsv(StringReader("裂,1500\n謎,1600\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(
                SuspendedKanjiImporterTest.note(1, "裂ける", "さける"),
                SuspendedKanjiImporterTest.note(2, "謎", "なぞ")
            ),
            listOf(
                SuspendedKanjiImporterTest.card(10, 1, false),
                SuspendedKanjiImporterTest.card(20, 2, true)
            )
        )
        val imports = KanjiImportSelector(ranks, settings.suspendedRankMin, settings.suspendedRankMax).importFrom(snapshot, settings)

        val rows = KanjiAnalyzer().rebuild(snapshot, imports, ranks, settings)

        val top = rows[0]
        assertEquals("謎", top.kanji)
        assertEquals(1, top.suspendedExampleCount)
        assertEquals("suspended_archive", top.reasonCode)
        assertTrue(top.reasonText.contains("writing-practice target"))
        assertEquals("note:Kiku Expression:*謎*", top.browserSearch)
    }

    @Test
    fun activeMatureSupportReducesWeakness() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = JitenKanjiRanks.parseCsv(StringReader("裂,3600\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(SuspendedKanjiImporterTest.note(1, "裂ける", "さける")),
            listOf(SuspendedKanjiImporterTest.card(10, 1, false))
        )

        val row = KanjiAnalyzer().rebuild(snapshot, emptyList<RecordsImportModels.SuspendedImport>(), ranks, settings)[0]

        assertEquals(1, row.matureSupportCount)
        assertEquals("weak_support", row.reasonCode)
    }

    @Test
    fun rowsWithMissingRankSortAfterRankedRows() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = JitenKanjiRanks.parseCsv(StringReader("裂,3600\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(
                SuspendedKanjiImporterTest.note(1, "裂ける", "さける"),
                SuspendedKanjiImporterTest.note(2, "謎", "なぞ")
            ),
            listOf(
                SuspendedKanjiImporterTest.card(10, 1, false),
                SuspendedKanjiImporterTest.card(20, 2, false)
            )
        )

        val rows = KanjiAnalyzer().rebuild(snapshot, emptyList<RecordsImportModels.SuspendedImport>(), ranks, settings)

        assertEquals("裂", rows[0].kanji)
        assertEquals("謎", rows[1].kanji)
    }

    @Test
    fun rowsWithEqualScoresAndRanksSortByKanji() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = JitenKanjiRanks.parseCsv(StringReader("謎,3600\n裂,3600\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(
                SuspendedKanjiImporterTest.note(1, "謎", "なぞ"),
                SuspendedKanjiImporterTest.note(2, "裂ける", "さける")
            ),
            listOf(
                SuspendedKanjiImporterTest.card(10, 1, false),
                SuspendedKanjiImporterTest.card(20, 2, false)
            )
        )

        val rows = KanjiAnalyzer().rebuild(snapshot, emptyList<RecordsImportModels.SuspendedImport>(), ranks, settings)

        assertEquals("裂", rows[0].kanji)
        assertEquals("謎", rows[1].kanji)
    }

    @Test
    fun fsrsWeakMemoryRaisesAnalyzerRanking() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = JitenKanjiRanks.parseCsv(StringReader("弱,3600\n強,3700\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(
                SuspendedKanjiImporterTest.note(1, "弱点", "じゃくてん"),
                SuspendedKanjiImporterTest.note(2, "強み", "つよみ")
            ),
            listOf(
                card(10, 1, 30, 12, 0, 3.0, 8.0, 0.35),
                card(20, 2, 30, 12, 0, 60.0, 3.0, 0.95)
            )
        )

        val rows = KanjiAnalyzer().rebuild(snapshot, emptyList<RecordsImportModels.SuspendedImport>(), ranks, settings)
        val weak = find(rows, "弱")
        val strong = find(rows, "強")

        assertEquals("弱", rows[0].kanji)
        assertEquals("fsrs_weak_memory", weak.reasonCode)
        assertTrue(weak.weaknessScore > strong.weaknessScore)
    }

    @Test
    fun missingFsrsFallsBackToSchedulerWeaknessSignals() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = JitenKanjiRanks.parseCsv(StringReader("浅,3600\n深,3700\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(
                SuspendedKanjiImporterTest.note(1, "浅い", "あさい"),
                SuspendedKanjiImporterTest.note(2, "深い", "ふかい")
            ),
            listOf(
                card(10, 1, 5, 12, 1, null, null, null),
                card(20, 2, 30, 12, 0, null, null, null)
            )
        )

        val rows = KanjiAnalyzer().rebuild(snapshot, emptyList<RecordsImportModels.SuspendedImport>(), ranks, settings)
        val shallow = find(rows, "浅")
        val deep = find(rows, "深")

        assertEquals("浅", rows[0].kanji)
        assertTrue(shallow.weaknessScore > deep.weaknessScore)
    }

    @Test
    fun fallsBackToSentenceKanjiAndSkipsDuplicateSuspendedImports() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = JitenKanjiRanks.parseCsv(StringReader("裂,1500\n外,1600\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(
                note(1, "かな", "かな", "meaning", "裂を見た。"),
                SuspendedKanjiImporterTest.note(2, "外", "そと")
            ),
            listOf(
                card(10, 1, 30, 8, 0, null, null, null),
                SuspendedKanjiImporterTest.card(20, 2, true),
                SuspendedKanjiImporterTest.card(999, 999, false)
            )
        )
        val imports = listOf(
            RecordsImportModels.SuspendedImport(
                "外",
                1600,
                true,
                3000,
                listOf(
                    source("外", 20, 2),
                    source("外", 30, 3)
                )
            )
        )

        val rows = KanjiAnalyzer().rebuild(snapshot, imports, ranks, settings)
        val sentenceFallback = find(rows, "裂")
        val imported = find(rows, "外")

        assertEquals(1, sentenceFallback.activeExampleCount)
        assertEquals("かな", sentenceFallback.examples[0].expression)
        assertEquals(2, imported.suspendedExampleCount)
    }

    @Test
    fun activeReasonCodesPreferSchedulerWeaknessThenLapses() {
        val supportSatisfied = settingsWithMatureSupport(0)
        val oneMatureRequired = settingsWithMatureSupport(1)
        val ranks = JitenKanjiRanks.parseCsv(StringReader("浅,1500\n深,1600\n"))
        val schedulerWeak = KanjiAnalyzer().rebuild(
            RecordsSyncModels.CollectionSnapshot(
                listOf(SuspendedKanjiImporterTest.note(1, "浅い", "あさい")),
                listOf(card(10, 1, 5, 12, 0, null, null, null))
            ),
            emptyList<RecordsImportModels.SuspendedImport>(),
            ranks,
            supportSatisfied
        )[0]
        val lapsed = KanjiAnalyzer().rebuild(
            RecordsSyncModels.CollectionSnapshot(
                listOf(SuspendedKanjiImporterTest.note(2, "深い", "ふかい")),
                listOf(card(20, 2, 30, 12, 2, null, null, null))
            ),
            emptyList<RecordsImportModels.SuspendedImport>(),
            ranks,
            oneMatureRequired
        )[0]

        assertEquals("anki_scheduler_weakness", schedulerWeak.reasonCode)
        assertEquals("anki_lapses", lapsed.reasonCode)
    }

    @Test
    fun fullySupportedCleanActiveRowsAreOmitted() {
        val settings = settingsWithMatureSupport(1)
        val ranks = JitenKanjiRanks.parseCsv(StringReader("深,1600\n"))

        val rows = KanjiAnalyzer().rebuild(
            RecordsSyncModels.CollectionSnapshot(
                listOf(SuspendedKanjiImporterTest.note(1, "深い", "ふかい")),
                listOf(card(10, 1, 45, 12, 0, 60.0, 3.0, 0.95))
            ),
            emptyList<RecordsImportModels.SuspendedImport>(),
            ranks,
            settings
        )

        assertTrue(rows.isEmpty())
    }

    @Test
    fun selectedTaggedActiveSourceCanForceCleanPracticeRow() {
        val settings = settingsWithMatureSupport(1)
        val ranks = JitenKanjiRanks.parseCsv(StringReader("深,1600\n"))
        val imports = listOf(
            RecordsImportModels.SuspendedImport(
                "深",
                1600,
                true,
                3000,
                listOf(
                    RecordsImportModels.SuspendedSource(
                        "深",
                        10,
                        1,
                        "深い",
                        "ふかい",
                        "deep",
                        RecordsImportModels.SuspendedSourceDetails.builder("深い。")
                            .sourceType(RecordsBase.SOURCE_ACTIVE)
                            .suspended(false)
                            .forcePractice(true)
                            .mature(true)
                            .reviewStats(0, 45, 12)
                            .fsrs(60.0, 3.0, 0.95)
                            .build()
                    )
                )
            )
        )

        val rows = KanjiAnalyzer().rebuildSelectedSources(
            RecordsSyncModels.CollectionSnapshot(
                listOf(note(1, "深い", "ふかい", "deep", "深い。")),
                listOf(card(10, 1, 45, 12, 0, 60.0, 3.0, 0.95))
            ),
            imports,
            ranks,
            settings
        )

        assertEquals(1, rows.size)
        assertEquals("深", rows[0].kanji)
        assertEquals(1, rows[0].activeExampleCount)
        assertEquals("watch", rows[0].reasonCode)
    }

    @Test
    fun selectedSourcesPreserveExistingForcePracticeAcrossMultipleCards() {
        val settings = settingsWithMatureSupport(1)
        val ranks = JitenKanjiRanks.parseCsv(StringReader("深,1600\n"))
        val sources = listOf(
            RecordsImportModels.SuspendedSource("深", 10, 1, "深い", "ふかい", "deep", activePracticeDetails("深い。")),
            RecordsImportModels.SuspendedSource("深", 20, 2, "深み", "ふかみ", "depth", activePracticeDetails("深み。"))
        )

        val rows = KanjiAnalyzer().rebuildSelectedSources(
            RecordsSyncModels.CollectionSnapshot(
                listOf(
                    note(1, "深い", "ふかい", "deep", "深い。"),
                    note(2, "深み", "ふかみ", "depth", "深み。")
                ),
                listOf(
                    card(10, 1, 45, 12, 0, 60.0, 3.0, 0.95),
                    card(20, 2, 45, 12, 0, 60.0, 3.0, 0.95)
                )
            ),
            listOf(RecordsImportModels.SuspendedImport("深", 1600, true, 3000, sources)),
            ranks,
            settings
        )

        assertEquals(1, rows.size)
        assertEquals(2, rows[0].activeExampleCount)
    }

    @Test
    fun selectedSourcesPreserveBrowserQuerySourceTypeForMatchedActiveCards() {
        val settings = settingsWithMatureSupport(1)
        val ranks = JitenKanjiRanks.parseCsv(StringReader("橋,1600\n"))
        val source = RecordsImportModels.SuspendedSource(
            "橋",
            10,
            1,
            "橋",
            "はし",
            "bridge",
            RecordsImportModels.SuspendedSourceDetails.builder("橋を渡る。")
                .sourceType(RecordsBase.SOURCE_BROWSER_QUERY)
                .suspended(false)
                .forcePractice(true)
                .mature(true)
                .reviewStats(0, 45, 12)
                .fsrs(60.0, 3.0, 0.95)
                .build()
        )

        val rows = KanjiAnalyzer().rebuildSelectedSources(
            RecordsSyncModels.CollectionSnapshot(
                listOf(note(1, "橋", "はし", "bridge", "橋を渡る。")),
                listOf(card(10, 1, 45, 12, 0, 60.0, 3.0, 0.95))
            ),
            listOf(RecordsImportModels.SuspendedImport("橋", 1600, true, 3000, listOf(source))),
            ranks,
            settings
        )

        assertEquals(1, rows.size)
        assertEquals(RecordsBase.SOURCE_BROWSER_QUERY, rows[0].examples[0].sourceType)
        assertEquals(1, rows[0].activeExampleCount)
    }

    @Test
    fun selectedSourcesSkipUnselectedCardsAndUnimportedKanji() {
        val settings = settingsWithMatureSupport(0)
        val ranks = JitenKanjiRanks.parseCsv(StringReader("深,1500\n外,1600\n"))
        val imports = listOf(
            RecordsImportModels.SuspendedImport(
                "深",
                1500,
                true,
                3000,
                listOf(
                    RecordsImportModels.SuspendedSource(
                        "深",
                        10,
                        1,
                        "深外",
                        "ふかい",
                        "deep",
                        activePracticeDetails("深外。")
                    )
                )
            )
        )

        val rows = KanjiAnalyzer().rebuildSelectedSources(
            RecordsSyncModels.CollectionSnapshot(
                listOf(
                    note(1, "深外", "ふかい", "deep", "深外。"),
                    note(2, "外", "そと", "outside", "外。")
                ),
                listOf(
                    card(10, 1, 45, 12, 0, 60.0, 3.0, 0.95),
                    card(20, 2, 3, 1, 0, null, null, null)
                )
            ),
            imports,
            ranks,
            settings
        )

        assertEquals(1, rows.size)
        assertEquals("深", rows[0].kanji)
    }

    @Test
    fun selectedSourceWithoutForcePracticeDoesNotKeepCleanRows() {
        val settings = settingsWithMatureSupport(1)
        val ranks = JitenKanjiRanks.parseCsv(StringReader("深,1500\n"))
        val source = RecordsImportModels.SuspendedSource(
            "深",
            10,
            1,
            "深い",
            "ふかい",
            "deep",
            RecordsImportModels.SuspendedSourceDetails.builder("深い。")
                .sourceType(RecordsBase.SOURCE_ACTIVE)
                .suspended(false)
                .forcePractice(false)
                .mature(true)
                .reviewStats(0, 45, 12)
                .fsrs(60.0, 3.0, 0.95)
                .build()
        )

        val rows = KanjiAnalyzer().rebuildSelectedSources(
            RecordsSyncModels.CollectionSnapshot(
                listOf(note(1, "深い", "ふかい", "deep", "深い。")),
                listOf(card(10, 1, 45, 12, 0, 60.0, 3.0, 0.95))
            ),
            listOf(RecordsImportModels.SuspendedImport("深", 1500, true, 3000, listOf(source))),
            ranks,
            settings
        )

        assertTrue(rows.isEmpty())
    }

    @Test
    fun importedRowsTrimExamplesAndUseFirstNonEmptyMeaningReading() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = JitenKanjiRanks.parseCsv(StringReader("集,1500\n"))
        val sources = mutableListOf<RecordsImportModels.SuspendedSource>()
        sources.add(RecordsImportModels.SuspendedSource("集", 1L, 1L, "集合", "", "", suspendedDetails("集合。", false, 0)))
        sources.add(RecordsImportModels.SuspendedSource("集", 1L, 1L, "集まる", "あつまる", "gather", suspendedDetails("集まる。", true, 1)))
        for (i in 2..10) {
            sources.add(RecordsImportModels.SuspendedSource("集", i.toLong(), i.toLong(), "集$i", "よみ$i", "meaning$i", suspendedDetails("文$i", false, 0)))
        }

        val rows = KanjiAnalyzer().rebuild(
            RecordsSyncModels.CollectionSnapshot(emptyList<RecordsSyncModels.Note>(), emptyList<RecordsSyncModels.Card>()),
            listOf(RecordsImportModels.SuspendedImport("集", 1500, true, 3000, sources)),
            ranks,
            settings
        )

        val row = rows[0]
        assertEquals("集", row.kanji)
        assertEquals("あつまる", row.reading)
        assertEquals("gather", row.primaryMeaning)
        assertEquals(8, row.examples.size)
        assertEquals("suspended_archive", row.reasonCode)
    }

    @Test
    fun cleansDictionaryMetadataFromAnkiMeanings() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = JitenKanjiRanks.parseCsv(StringReader("動,1500\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(
                note(
                    1,
                    "動く",
                    "うごく",
                    "Meaning: Jitendex (noun) movement|word-level fallback",
                    "動く。"
                )
            ),
            listOf(card(10, 1, 0, 0, 0, null, null, null))
        )

        val row = KanjiAnalyzer().rebuild(snapshot, emptyList<RecordsImportModels.SuspendedImport>(), ranks, settings)[0]

        assertEquals("movement", row.primaryMeaning)
        assertEquals("movement", row.examples[0].meaning)
    }

    @Test
    fun fsrsRetrievabilityNormalizesPercentAndRejectsInvalidValues() {
        val settings = settingsWithMatureSupport(2)
        val ranks = JitenKanjiRanks.parseCsv(StringReader("弱,1500\n中,1550\n百,1600\n過,1700\n負,1800\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(
                SuspendedKanjiImporterTest.note(1, "弱い", "よわい"),
                SuspendedKanjiImporterTest.note(2, "中", "なか"),
                SuspendedKanjiImporterTest.note(3, "百", "ひゃく"),
                SuspendedKanjiImporterTest.note(4, "過ぎる", "すぎる"),
                SuspendedKanjiImporterTest.note(5, "負ける", "まける")
            ),
            listOf(
                card(10, 1, 30, 12, 0, 3.0, 4.0, 45.0),
                card(20, 2, 30, 12, 0, 3.0, 4.0, 0.60),
                card(30, 3, 30, 12, 0, 30.0, 4.0, 75.0),
                card(40, 4, 30, 12, 0, 50.0, 4.0, 101.0),
                card(50, 5, 30, 12, 0, 50.0, 4.0, -0.1)
            )
        )

        val rows = KanjiAnalyzer().rebuild(snapshot, emptyList<RecordsImportModels.SuspendedImport>(), ranks, settings)

        assertEquals("fsrs_weak_memory", find(rows, "弱").reasonCode)
        assertEquals("fsrs_weak_memory", find(rows, "中").reasonCode)
        assertEquals("weak_support", find(rows, "百").reasonCode)
        assertEquals("weak_support", find(rows, "過").reasonCode)
        assertEquals("weak_support", find(rows, "負").reasonCode)
    }

    @Test
    fun fsrsStabilityPressureHonorsRepsAndExistingPressureGuards() {
        val settings = settingsWithMatureSupport(2)
        val ranks = JitenKanjiRanks.parseCsv(StringReader("少,1500\n難,1600\n守,1700\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(
                SuspendedKanjiImporterTest.note(1, "少ない", "すくない"),
                SuspendedKanjiImporterTest.note(2, "難しい", "むずかしい"),
                SuspendedKanjiImporterTest.note(3, "守る", "まもる")
            ),
            listOf(
                card(10, 1, 30, 4, 0, 3.0, 4.0, 0.95),
                card(20, 2, 30, 12, 0, 50.0, 8.0, 0.95),
                card(30, 3, 30, 12, 0, 50.0, 4.0, 0.95)
            )
        )

        val rows = KanjiAnalyzer().rebuild(snapshot, emptyList<RecordsImportModels.SuspendedImport>(), ranks, settings)

        assertEquals("weak_support", find(rows, "少").reasonCode)
        assertEquals("fsrs_weak_memory", find(rows, "難").reasonCode)
        assertEquals("weak_support", find(rows, "守").reasonCode)
    }

    @Test
    fun singularLapseReasonTextIsReadable() {
        val oneMatureRequired = settingsWithMatureSupport(1)
        val ranks = JitenKanjiRanks.parseCsv(StringReader("深,1600\n"))
        val lapsed = KanjiAnalyzer().rebuild(
            RecordsSyncModels.CollectionSnapshot(
                listOf(SuspendedKanjiImporterTest.note(2, "深い", "ふかい")),
                listOf(card(20, 2, 30, 12, 1, null, null, null))
            ),
            emptyList<RecordsImportModels.SuspendedImport>(),
            ranks,
            oneMatureRequired
        )[0]

        assertTrue(lapsed.reasonText.contains("1 lapse"))
    }

    private fun note(id: Long, expression: String, reading: String, meaning: String, sentence: String): RecordsSyncModels.Note {
        val fields = LinkedHashMap<String, String>()
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        fields[settings.expressionField] = expression
        fields[settings.readingField] = reading
        fields[settings.meaningField] = meaning
        fields[settings.sentenceField] = sentence
        fields[settings.frequencyField] = "9999"
        fields[settings.frequencySortField] = "9999"
        return RecordsSyncModels.Note(id, "Kiku", fields, emptyList())
    }

    private fun source(kanji: String, cardId: Long, noteId: Long): RecordsImportModels.SuspendedSource {
        return RecordsImportModels.SuspendedSource(kanji, cardId, noteId, kanji, "reading", "meaning", "$kanji sentence")
    }

    private fun activePracticeDetails(sentence: String): RecordsImportModels.SuspendedSourceDetails {
        return RecordsImportModels.SuspendedSourceDetails.builder(sentence)
            .sourceType(RecordsBase.SOURCE_ACTIVE)
            .suspended(false)
            .forcePractice(true)
            .mature(true)
            .reviewStats(0, 45, 12)
            .fsrs(60.0, 3.0, 0.95)
            .build()
    }

    private fun suspendedDetails(sentence: String, forcePractice: Boolean, lapses: Int): RecordsImportModels.SuspendedSourceDetails {
        return RecordsImportModels.SuspendedSourceDetails.builder(sentence)
            .sourceType(RecordsBase.SOURCE_SUSPENDED)
            .suspended(true)
            .forcePractice(forcePractice)
            .reviewStats(lapses, 0, 0)
            .build()
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
            defaults.writingTriggerMissDays
        )
    }

    private fun card(
        cardId: Long,
        noteId: Long,
        intervalDays: Int,
        reps: Int,
        lapses: Int,
        fsrsStability: Double?,
        fsrsDifficulty: Double?,
        fsrsRetrievability: Double?
    ): RecordsSyncModels.Card {
        return RecordsSyncModels.Card(cardId, noteId, 0, "Kiku", 2, 2, 0, intervalDays, reps, lapses, false, fsrsStability, fsrsDifficulty, fsrsRetrievability)
    }

    private fun find(rows: List<RecordsImportModels.DashboardRow>, kanji: String): RecordsImportModels.DashboardRow {
        for (row in rows) {
            if (row.kanji == kanji) {
                return row
            }
        }
        throw AssertionError("Missing row for $kanji")
    }
}