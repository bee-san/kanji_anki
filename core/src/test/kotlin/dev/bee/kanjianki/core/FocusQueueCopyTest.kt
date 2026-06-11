package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

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
        assertEquals("From AnkiDroid", FocusQueueCopy.sourceEvidenceText(row("x", 0, 0, "reason", emptyList())))
    }

    @Test
    fun queueCardBodyPreservesFallbackSimilarAndRawReasonText() {
        assertEquals("Needs kanji practice.", FocusQueueCopy.queueCardBody(row("x", 0, 0, "")))
        assertEquals(
            "Shape mix-up; practice writing.",
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

    @Test
    fun japaneseLocaleTranslatesFocusQueueCopy() {
        withLocale(Locale.JAPANESE) {
            val active = example("active", "active-one")
            val laterActive = example("active", "active-two")
            val suspended = example("suspended", "suspended-one")
            val laterSuspended = example("suspended", "suspended-two")

            assertEquals(
                "出典 active-one · 見逃し suspended-one",
                FocusQueueCopy.sourceEvidenceText(row("x", 0, 0, "reason", listOf(active, laterActive, suspended, laterSuspended))),
            )
            assertEquals("出典 active-one", FocusQueueCopy.sourceEvidenceText(row("x", 0, 0, "reason", listOf(active))))
            assertEquals("見逃し suspended-one", FocusQueueCopy.sourceEvidenceText(row("x", 0, 0, "reason", listOf(suspended))))
            assertEquals("AnkiDroidから", FocusQueueCopy.sourceEvidenceText(row("x", 0, 0, "reason", emptyList())))

            assertEquals("漢字の練習が必要です。", FocusQueueCopy.queueCardBody(row("x", 0, 0, "")))
            assertEquals(
                "字形の取り違えです。書いて練習しましょう。",
                FocusQueueCopy.queueCardBody(row("similar", 0, 0, "Similar-kanji choice missed")),
            )

            val now = 5_000L
            assertEquals(
                "弱点 42 · 支え 1/3 · 漢字→意味 · 今すぐ",
                FocusQueueCopy.focusReasonLine(
                    row("弱", 42, 1, "reason"),
                    item("弱", RecordsBase.LadderRung.KANJI_MEANING, StudyLadderRules.STATE_REVIEW, now, 1),
                    now,
                    3,
                ),
            )
            assertEquals(
                "漢字を書く · 学習中",
                FocusQueueCopy.focusReasonLine(
                    row("書", 0, 3, "reason"),
                    item("書", RecordsBase.LadderRung.WRITE_KANJI, StudyLadderRules.STATE_LEARNING, now + 1L, 1),
                    now,
                    3,
                ),
            )

            assertEquals("漢字→意味", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.KANJI_MEANING)))
            assertEquals("漢字を書く", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.WRITE_KANJI)))
            assertEquals("意味を入力", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.TYPE_MEANING)))
            assertEquals("似た漢字", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.SIMILAR_KANJI)))
            assertEquals("意味→漢字", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.MEANING_KANJI)))
            assertEquals("フォント→意味", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.FONT_MEANING)))
            assertEquals("単語→読み", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.WORD_READING)))
        }
    }

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun row(
        kanji: String,
        weaknessScore: Int,
        matureSupportCount: Int,
        reasonText: String,
        examples: List<RecordsImportModels.Example> = emptyList(),
    ): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
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
    }

    private fun example(sourceType: String, expression: String): RecordsImportModels.Example {
        return RecordsImportModels.Example(
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
    }

    private fun item(rung: RecordsBase.LadderRung): RecordsStudyModels.StudyItem {
        return item("字", rung, StudyLadderRules.STATE_REVIEW, 0L, 1)
    }

    private fun item(
        kanji: String,
        rung: RecordsBase.LadderRung,
        state: String,
        dueAtMillis: Long,
        totalReviews: Int,
    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, state, dueAtMillis, 1.0, 5.0, totalReviews, 0, 0, 1, null, 0L)
            .copyBuilder()
            .rung(rung)
            .build()
    }
}
