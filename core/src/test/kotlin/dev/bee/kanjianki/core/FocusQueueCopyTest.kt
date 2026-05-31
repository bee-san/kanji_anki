package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusQueueCopyTest {
    @Test
    fun sourceEvidenceTextPrefersFirstActiveAndSuspendedExamples() {
        val active = example("active", "active-one")
        val laterActive = example("active", "active-two")
        val suspended = example("suspended", "suspended-one")
        val laterSuspended = example("suspended", "suspended-two")

        assertEquals(
            "From active-one · missed suspended-one",
            FocusQueueCopy.sourceEvidenceText(row("x", 0, 0, "reason", listOf(active, laterActive, suspended, laterSuspended))),
        )
        assertEquals("From active-one", FocusQueueCopy.sourceEvidenceText(row("x", 0, 0, "reason", listOf(active))))
        assertEquals("Missed suspended-one", FocusQueueCopy.sourceEvidenceText(row("x", 0, 0, "reason", listOf(suspended))))
        assertEquals("From your AnkiDroid sync", FocusQueueCopy.sourceEvidenceText(row("x", 0, 0, "reason", emptyList())))
    }

    @Test
    fun queueCardBodyPreservesFallbackSimilarAndRawReasonText() {
        assertEquals("Needs focused kanji practice.", FocusQueueCopy.queueCardBody(row("x", 0, 0, "")))
        assertEquals(
            "Shape mix-up made this a writing-practice target.",
            FocusQueueCopy.queueCardBody(row("similar", 0, 0, "Similar-kanji choice missed")),
        )
        assertEquals("Specific reason", FocusQueueCopy.queueCardBody(row("reason", 0, 0, "Specific reason")))
    }

    @Test
    fun focusReasonLineIncludesWeaknessSupportStageAndDueState() {
        val now = 5_000L

        assertEquals(
            "weakness 42 · support 1/3 · kanji -> meaning · due now",
            FocusQueueCopy.focusReasonLine(
                row("弱", 42, 1, "reason"),
                item("弱", RecordsBase.LadderRung.KANJI_MEANING, StudyLadderRules.STATE_REVIEW, now, 1),
                now,
                3,
            ),
        )
        assertEquals(
            "write kanji · learning",
            FocusQueueCopy.focusReasonLine(
                row("書", 0, 3, "reason"),
                item("書", RecordsBase.LadderRung.WRITE_KANJI, StudyLadderRules.STATE_LEARNING, now + 1L, 1),
                now,
                3,
            ),
        )
    }

    @Test
    fun recognitionStageLabelNamesEveryRung() {
        assertEquals("kanji -> meaning", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.KANJI_MEANING)))
        assertEquals("write kanji", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.WRITE_KANJI)))
        assertEquals("type meaning", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.TYPE_MEANING)))
        assertEquals("similar kanji", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.SIMILAR_KANJI)))
        assertEquals("meaning -> kanji", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.MEANING_KANJI)))
        assertEquals("font -> meaning", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.FONT_MEANING)))
        assertEquals("word -> reading", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.WORD_READING)))
    }

    private fun row(
        kanji: String,
        weaknessScore: Int,
        matureSupportCount: Int,
        reasonText: String,
        examples: List<RecordsImportModels.Example> = emptyList(),
    ): RecordsImportModels.DashboardRow = RecordsImportModels.DashboardRow(
        kanji,
        900,
        "meaning",
        "reading",
        "search",
        weaknessScore,
        "reason",
        reasonText,
        1,
        0,
        matureSupportCount,
        examples,
    )

    private fun example(sourceType: String, expression: String): RecordsImportModels.Example = RecordsImportModels.Example(
        sourceType,
        1L,
        2L,
        expression,
        "reading",
        "meaning",
        "sentence",
        false,
        0,
        0,
        0,
        null,
        null,
        null,
    )

    private fun item(rung: RecordsBase.LadderRung): RecordsStudyModels.StudyItem =
        item("字", rung, StudyLadderRules.STATE_REVIEW, 0L, 1)

    private fun item(
        kanji: String,
        rung: RecordsBase.LadderRung,
        state: String,
        dueAtMillis: Long,
        totalReviews: Int,
    ): RecordsStudyModels.StudyItem = RecordsStudyModels.StudyItem(kanji, state, dueAtMillis, 1.0, 5.0, totalReviews, 0, 0, 1, null, 0L)
        .copyBuilder()
        .rung(rung)
        .build()
}