package dev.bee.kanjianki.core

import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualKanjiAdmissionPolicyTest {
    @Test
    fun plansOnlyCandidatesWithMeaningAndSupportedReading() {
        val plan = ManualKanjiAdmissionPolicy.plan(
            listOf(
                candidate("水", listOf("water"), kun = listOf("みず")),
                candidate("火", emptyList(), on = listOf("カ")),
                candidate("風", listOf("wind")),
                candidate("not-kanji", listOf("invalid"), on = listOf("む")),
            ),
            "Offline dictionary source.",
        )

        assertEquals(listOf("水"), plan.acceptedCandidates.map { it.literal })
        assertEquals(setOf("火"), plan.missingMeaningLiterals)
        assertEquals(setOf("風"), plan.missingReadingLiterals)
        assertEquals(setOf("not-kanji"), plan.invalidLiterals)
        assertEquals(setOf("火", "風", "not-kanji"), plan.skippedLiterals)
        assertEquals(0, plan.duplicateCount)
    }

    @Test
    fun dictionaryRowUsesLiteralAndNormalizedRealReadingWithoutAnkiEvidence() {
        val row = ManualKanjiAdmissionPolicy.plan(
            listOf(candidate("行", listOf("go"), kun = listOf("-おこな.う"), on = listOf("コウ"))),
            "Offline dictionary source.",
        ).rows.single()

        assertEquals("行", row.kanji)
        assertEquals("おこな", row.reading)
        assertEquals(0, row.activeExampleCount)
        assertEquals(0, row.suspendedExampleCount)
        assertEquals(0, row.matureSupportCount)
        assertEquals(ManualKanjiAdmissionPolicy.SOURCE_DICTIONARY, row.examples.single().sourceType)
        assertEquals("行", row.examples.single().expression)
        assertEquals("おこな", row.examples.single().reading)
    }

    @Test
    fun providerEvidenceWinsWhileDictionaryProvenanceRemainsExplicit() {
        val providerExample = example(RecordsBase.SOURCE_ACTIVE, "水泳", "すいえい", "swimming")
        val provider = row("水", "swimming", "すいえい", listOf(providerExample))

        val merged = ManualKanjiAdmissionPolicy.mergeRows(
            listOf(provider),
            listOf(candidate("水", listOf("water"), kun = listOf("みず"))),
            "Offline dictionary source.",
        ).single()

        assertEquals("swimming", merged.primaryMeaning)
        assertEquals(providerExample, StudyExampleSelector.wordReadingExample(merged))
        assertTrue(ManualKanjiAdmissionPolicy.hasDictionarySource(merged))
        assertFalse(ManualKanjiAdmissionPolicy.isDictionaryOnly(merged))
        assertEquals(
            listOf(RecordsBase.SOURCE_ACTIVE, ManualKanjiAdmissionPolicy.SOURCE_DICTIONARY),
            merged.examples.map { it.sourceType },
        )
    }

    @Test
    fun duplicateCandidatesAreCountedAndACompleteDuplicateWins() {
        val plan = ManualKanjiAdmissionPolicy.plan(
            listOf(
                candidate("水", emptyList(), kun = listOf("みず")),
                candidate("水", listOf("water"), kun = listOf("みず")),
            ),
            "Offline dictionary source.",
        )

        assertEquals(1, plan.duplicateCount)
        assertEquals(listOf("水"), plan.acceptedCandidates.map { it.literal })
        assertTrue(plan.missingMeaningLiterals.isEmpty())
        assertTrue(ManualKanjiAdmissionPolicy.isDictionaryOnly(plan.rows.single()))
    }

    @Test
    fun additionPlanTreatsActiveAndRetiredFamiliesAsIdempotent() {
        val plan = ManualKanjiAdmissionPolicy.planAddition(
            candidates = listOf(
                candidate("水", listOf("water"), kun = listOf("みず")),
                candidate("火", listOf("fire"), on = listOf("カ")),
                candidate("風", listOf("wind"), kun = listOf("かぜ")),
            ),
            existingStudyLiterals = setOf("水"),
            activeManualLiterals = setOf("火"),
        )

        assertEquals(listOf("風"), plan.candidatesToAdd.map { it.literal })
        assertEquals(setOf("水", "火"), plan.alreadyInKaniLiterals)
    }

    @Test
    fun freshManualSourceDoesNotReopenRetiredHistory() {
        val row = ManualKanjiAdmissionPolicy.plan(
            listOf(candidate("水", listOf("water"), kun = listOf("みず"))),
            "Offline dictionary source.",
        ).rows.single()
        val retired = RecordsStudyModels.StudyItem(
            "水",
            StudyLadderRules.STATE_RETIRED,
            9_000L,
            40.0,
            4.0,
            8,
            1,
            0,
            0,
            0,
            0,
            0L,
            false,
            null,
            0L,
            30,
            StudyQueueSeeder.answerSignature(row),
            null,
            123L,
        )

        val seeded = StudyQueueSeeder().seedQueue(
            listOf(row),
            listOf(retired),
            RecordsSyncModels.Settings.kikuDefaults(),
            10_000L,
            0L,
            ladder = null,
        ).single()

        assertEquals(StudyLadderRules.STATE_RETIRED, seeded.state)
        assertEquals(8, seeded.totalReviews)
        assertEquals(123L, seeded.createdAtMillis)
    }

    @Test
    fun providerAppearanceDoesNotResetReviewedManualItem() {
        val manual = ManualKanjiAdmissionPolicy.plan(
            listOf(candidate("水", listOf("water"), kun = listOf("みず"))),
            "Offline dictionary source.",
        ).rows.single()
        val existing = RecordsStudyModels.StudyItem(
            "水",
            StudyLadderRules.STATE_REVIEW,
            9_000L,
            40.0,
            4.0,
            8,
            1,
            0,
            0,
            0,
            0,
            0L,
            false,
            null,
            0L,
            30,
            StudyQueueSeeder.answerSignature(manual),
            null,
            123L,
        )
        val provider = row(
            "水",
            "swimming",
            "すいえい",
            listOf(example(RecordsBase.SOURCE_ACTIVE, "水泳", "すいえい", "swimming")),
        )
        val merged = ManualKanjiAdmissionPolicy.mergeRows(
            listOf(provider),
            listOf(candidate("水", listOf("water"), kun = listOf("みず"))),
            "Offline dictionary source.",
        )

        val seeded = StudyQueueSeeder().seedQueue(
            merged,
            listOf(existing),
            RecordsSyncModels.Settings.kikuDefaults(),
            10_000L,
            0L,
            ladder = null,
        ).single()

        assertEquals(8, seeded.totalReviews)
        assertEquals(40.0, seeded.stability, 0.0)
        assertEquals(123L, seeded.createdAtMillis)
        assertFalse(seeded.answerSignature == existing.answerSignature)
    }

    @Test
    fun fiveThousandCandidatesPlanAndMergeWithinRegressionBudget() {
        val candidates = (1..5_000).map { index ->
            MissingKanjiCandidate(
                literal = String(Character.toChars(0x4E00 + index)),
                meanings = listOf("meaning $index"),
                onReadings = listOf("オン$index"),
                jitenRank = index,
            )
        }
        lateinit var rows: List<RecordsImportModels.DashboardRow>
        fun merge() {
            rows = ManualKanjiAdmissionPolicy.mergeRows(
                emptyList(),
                candidates,
                "Offline dictionary source.",
            )
        }

        // One discarded warmup, then the fastest of three. This previously timed a
        // single cold run, so the number included JIT compilation of the whole merge
        // path — it failed twice on Windows CI while Linux and macOS passed the same
        // commits, and passed on re-run without any code changing. A budget that fails
        // for reasons that are not Kani's gets bumped or deleted rather than believed.
        //
        // The 2s budget is unchanged: it is meant to catch an order-of-magnitude
        // regression in admission planning, and it still does. What changed is that the
        // measurement now describes the code rather than the JIT.
        merge()

        var fastestElapsed = Long.MAX_VALUE
        repeat(3) {
            fastestElapsed = minOf(fastestElapsed, measureTimeMillis { merge() })
        }

        assertEquals(5_000, rows.size)
        assertTrue(
            "fastest 5,000-row admission planning took ${fastestElapsed}ms",
            fastestElapsed < 2_000L,
        )
    }

    private fun candidate(
        literal: String,
        meanings: List<String>,
        on: List<String> = emptyList(),
        kun: List<String> = emptyList(),
    ): MissingKanjiCandidate = MissingKanjiCandidate(
        literal = literal,
        meanings = meanings,
        onReadings = on,
        kunReadings = kun,
        jitenRank = 100,
    )

    private fun row(
        literal: String,
        meaning: String,
        reading: String,
        examples: List<RecordsImportModels.Example>,
    ): RecordsImportModels.DashboardRow = RecordsImportModels.DashboardRow(
        literal,
        100,
        meaning,
        reading,
        literal,
        10,
        "active",
        "Provider evidence",
        1,
        0,
        0,
        examples,
    )

    private fun example(
        source: String,
        expression: String,
        reading: String,
        meaning: String,
    ): RecordsImportModels.Example = RecordsImportModels.Example(
        source,
        1L,
        1L,
        expression,
        reading,
        meaning,
        "",
        false,
        0,
    )
}
