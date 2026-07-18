package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.Locale

class TimelineCopyTest {
    @Test
    fun statusTextPreservesActiveRestingAndRetiredCases() {
        val now = 5_000L

        assertEquals("Active repair", TimelineCopy.statusText(timeline(row(), studyItem("review", now)), now))
        assertEquals("Resting until review", TimelineCopy.statusText(timeline(row(), studyItem("review", now + 1L)), now))
        assertEquals("Retired by Anki support", TimelineCopy.statusText(timeline(row(), studyItem("retired", now - 1L)), now))
        assertEquals("Active repair", TimelineCopy.statusText(timeline(null, studyItem("review", now)), now))
        assertEquals("No active repair", TimelineCopy.statusText(timeline(row(), null), now))
        assertEquals("No active repair", TimelineCopy.statusText(timeline(null, null), now))
    }

    @Test
    fun statusToneMapsRetiredRestingAndActiveCases() {
        val now = 5_000L

        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.statusTone(timeline(row(), studyItem("retired", now - 1L)), now))
        assertEquals(TimelineCopy.Tone.NEUTRAL, TimelineCopy.statusTone(timeline(row(), studyItem("review", now + 1L)), now))
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.statusTone(timeline(row(), studyItem("review", now)), now))
        assertEquals(TimelineCopy.Tone.NEUTRAL, TimelineCopy.statusTone(timeline(row(), null), now))
    }

    @Test
    fun eventToneClassifiesKnownTimelineEvents() {
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.eventTone(TimelineCopy.EVENT_REVIEW_FAILED))
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.eventTone("support_dropped"))
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.eventTone(TimelineCopy.EVENT_REOPENED))
        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.eventTone(TimelineCopy.EVENT_REVIEW_PASSED))
        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.eventTone("support_improved"))
        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.eventTone("retired"))
        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.eventTone(TimelineCopy.EVENT_REPAIR_TAGGED))
        assertEquals(TimelineCopy.Tone.NEUTRAL, TimelineCopy.eventTone("sync"))
    }

    @Test
    fun sourceLineFormatsMissingExpressionReadingAndFullSource() {
        assertEquals("", TimelineCopy.sourceLine(event("", "")))
        assertEquals("Source: expr", TimelineCopy.sourceLine(event("expr", "")))
        assertEquals("Source: expr  reading", TimelineCopy.sourceLine(event("expr", "reading")))
    }

    @Test
    fun eventStorageCopyPreservesEnglishDefaults() {
        assertEquals("Imported from suspended Anki", TimelineCopy.suspendedImportedTitle())
        assertEquals(
            "Kani recovered this kanji from a suspended AnkiDroid card.",
            TimelineCopy.suspendedImportedDetail(),
        )
        assertEquals("Kani started watching", TimelineCopy.firstSeenTitle())
        assertEquals(
            "This kanji entered Kani from local AnkiDroid evidence.",
            TimelineCopy.firstSeenAnkiEvidenceDetail(),
        )
        assertEquals(
            "This kanji has historical Kani study state.",
            TimelineCopy.firstSeenHistoricalStudyDetail(),
        )
        assertEquals("Weak support seen", TimelineCopy.weakSupportSeenTitle())
        assertEquals("Retired by Anki support", TimelineCopy.retiredByAnkiSupportTitle())
        assertEquals("Marked repaired in AnkiDroid", TimelineCopy.repairTaggedTitle())
        assertTrue(TimelineCopy.repairTaggedDetail().contains("tag:kani_repaired"))
        assertEquals(
            "Kani had already retired this repair before timeline tracking was added.",
            TimelineCopy.historicalRetiredDetail(),
        )
        assertEquals("Anki support improved", TimelineCopy.supportImprovedTitle())
        assertEquals("Mature support rose from 1 to 2.", TimelineCopy.supportImprovedDetail(1, 2))
        assertEquals("Anki support dropped", TimelineCopy.supportDroppedTitle())
        assertEquals("Mature support fell from 3 to 1.", TimelineCopy.supportDroppedDetail(3, 1))
        assertEquals("Repair reopened", TimelineCopy.repairReopenedTitle())
    }

    @Test
    fun studyStateDetailPreservesRetiredAndReopenedCopy() {
        assertEquals(
            "No weak Anki evidence remained after sync, so Kani retired this repair.",
            TimelineCopy.studyStateDetail(true, null, 3),
        )
        assertEquals(
            "Kani reopened this kanji after sync found weak evidence again.",
            TimelineCopy.studyStateDetail(false, null, 3),
        )
        assertEquals(
            "Mature Anki support met the target: mature support 3 / target 3.",
            TimelineCopy.studyStateDetail(true, 3, 3),
        )
        assertEquals(
            "Mature Anki support fell below target: mature support 1 / target 3.",
            TimelineCopy.studyStateDetail(false, 1, 3),
        )
    }

    @Test
    fun reviewEventPreservesTypeTitleAndDetailMapping() {
        val manual = TimelineCopy.reviewEvent(review("good", false, false, true), "good")
        val recallFail = TimelineCopy.reviewEvent(review("again", false, false, false), "again")
        val writingMiss = TimelineCopy.reviewEvent(review("hard", true, false, false), "hard")
        val writingPass = TimelineCopy.reviewEvent(review("good", true, true, false), "good")
        val recallPass = TimelineCopy.reviewEvent(review("good", false, false, false), "good")

        assertEquals(TimelineCopy.EVENT_MANUAL_OVERRIDE, manual.eventType())
        assertEquals("Manual override", manual.title())
        assertEquals("Saved as good after manual confirmation.", manual.detail())
        assertEquals(TimelineCopy.EVENT_REVIEW_FAILED, recallFail.eventType())
        assertEquals("Review missed", recallFail.title())
        assertEquals("Recall missed; Kani will show it again.", recallFail.detail())
        assertEquals(TimelineCopy.EVENT_REVIEW_FAILED, writingMiss.eventType())
        assertEquals("Review missed", writingMiss.title())
        assertEquals("Writing was not passed and was rated hard.", writingMiss.detail())
        assertEquals(TimelineCopy.EVENT_REVIEW_PASSED, writingPass.eventType())
        assertEquals("Review passed", writingPass.title())
        assertEquals("Writing passed and was rated good.", writingPass.detail())
        assertEquals("Recall review was rated good.", recallPass.detail())
    }

    @Test
    fun japaneseLocaleTranslatesTimelineHistoryCopy() {
        val now = 5_000L

        withLocale(Locale.JAPAN) {
            assertEquals("修復中", TimelineCopy.statusText(timeline(row(), studyItem("review", now)), now))
            assertEquals("復習まで休止中", TimelineCopy.statusText(timeline(row(), studyItem("review", now + 1L)), now))
            assertEquals("Ankiの支えで修了", TimelineCopy.statusText(timeline(row(), studyItem("retired", now - 1L)), now))
            assertEquals("修復中", TimelineCopy.statusText(timeline(null, studyItem("review", now)), now))
            assertEquals("修復なし", TimelineCopy.statusText(timeline(row(), null), now))

            assertEquals("", TimelineCopy.sourceLine(event("", "")))
            assertEquals("出典: expr", TimelineCopy.sourceLine(event("expr", "")))
            assertEquals("出典: expr  reading", TimelineCopy.sourceLine(event("expr", "reading")))
            assertEquals("保留中のAnkiからインポート", TimelineCopy.suspendedImportedTitle())
            assertEquals("KaniはAnkiDroidの保留カードからこの漢字を復旧しました。", TimelineCopy.suspendedImportedDetail())
            assertEquals("Kaniが見守り開始", TimelineCopy.firstSeenTitle())
            assertEquals("この漢字はローカルAnkiDroidの証拠からKaniに入りました。", TimelineCopy.firstSeenAnkiEvidenceDetail())
            assertEquals("この漢字には過去のKani学習状態があります。", TimelineCopy.firstSeenHistoricalStudyDetail())
            assertEquals("弱いサポートを検出", TimelineCopy.weakSupportSeenTitle())
            assertEquals("Ankiの支えで修了", TimelineCopy.retiredByAnkiSupportTitle())
            assertEquals("AnkiDroidで修復済みに設定", TimelineCopy.repairTaggedTitle())
            assertEquals(
                "タイムライン記録が追加される前に、Kaniはすでにこの修復を完了していました。",
                TimelineCopy.historicalRetiredDetail(),
            )
            assertEquals("Ankiサポートが改善", TimelineCopy.supportImprovedTitle())
            assertEquals("成熟サポートが1から2に増えました。", TimelineCopy.supportImprovedDetail(1, 2))
            assertEquals("Ankiサポートが低下", TimelineCopy.supportDroppedTitle())
            assertEquals("成熟サポートが3から1に減りました。", TimelineCopy.supportDroppedDetail(3, 1))
            assertEquals("修復を再開", TimelineCopy.repairReopenedTitle())

            assertEquals(
                "同期後に弱いAnki証拠が残っていなかったため、この修復を完了しました。",
                TimelineCopy.studyStateDetail(true, null, 3),
            )
            assertEquals(
                "同期で弱い証拠が再び見つかったため、この漢字の修復を再開しました。",
                TimelineCopy.studyStateDetail(false, null, 3),
            )
            assertEquals(
                "成熟したAnkiの支えが目標に到達: 成熟サポート 3 / 目標 3。",
                TimelineCopy.studyStateDetail(true, 3, 3),
            )
            assertEquals(
                "成熟したAnkiの支えが目標を下回りました: 成熟サポート 1 / 目標 3。",
                TimelineCopy.studyStateDetail(false, 1, 3),
            )
            assertEquals(
                "Ankiの証拠はまだ修復が必要: 成熟サポート 1 / 目標 3。",
                TimelineCopy.supportDetail("Anki evidence still needs repair", 1, 3),
            )

            val manual = TimelineCopy.reviewEvent(review("good", false, false, true), "good")
            val recallFail = TimelineCopy.reviewEvent(review("again", false, false, false), "again")
            val writingAgain = TimelineCopy.reviewEvent(review("again", true, false, false), "again")
            val writingMiss = TimelineCopy.reviewEvent(review("hard", true, false, false), "hard")
            val writingPass = TimelineCopy.reviewEvent(review("good", true, true, false), "good")
            val recallPass = TimelineCopy.reviewEvent(review("good", false, false, false), "good")

            assertEquals("手動上書き", manual.title())
            assertEquals("手動確認後に「良い」として保存しました。", manual.detail())
            assertEquals("復習ミス", recallFail.title())
            assertEquals("思い出せなかったため、Kaniがもう一度出題します。", recallFail.detail())
            assertEquals("書き取りをミスしたため、Kaniがもう一度出題します。", writingAgain.detail())
            assertEquals("復習ミス", writingMiss.title())
            assertEquals("書き取りは不合格で、「難しい」と評価されました。", writingMiss.detail())
            assertEquals("復習成功", writingPass.title())
            assertEquals("書き取りに成功し、「良い」と評価されました。", writingPass.detail())
            assertEquals("思い出し復習は「良い」と評価されました。", recallPass.detail())
        }
    }

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        return try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun timeline(
        row: RecordsImportModels.DashboardRow?,
        item: RecordsStudyModels.StudyItem?,
    ): RecordsStudyModels.KanjiRecoveryTimeline {
        return RecordsStudyModels.KanjiRecoveryTimeline(row, item, Collections.emptyList())
    }

    private fun studyItem(state: String, dueAtMillis: Long): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem("x", state, dueAtMillis, 1.0, 5.0, 1, 0, 0, 1, null, 0L)
    }

    private fun row(): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            "x",
            900,
            "meaning",
            "reading",
            "search",
            1,
            "reason",
            "reason text",
            1,
            0,
            1,
            emptyList<RecordsImportModels.Example>(),
        )
    }

    private fun event(expression: String, reading: String): RecordsImportModels.KanjiTimelineEvent {
        return RecordsImportModels.KanjiTimelineEvent(
            1L,
            "x",
            10L,
            "sync",
            "title",
            "detail",
            expression,
            reading,
            "",
            false,
            false,
            false,
            0,
            0,
            1L,
            "key",
        )
    }

    private fun review(
        rating: String,
        writingRequired: Boolean,
        writingPassed: Boolean,
        manualOverride: Boolean,
    ): RecordsSchedulerModels.ReviewRequest {
        return RecordsSchedulerModels.ReviewRequest(
            "x",
            "token",
            rating,
            writingRequired,
            writingPassed,
            true,
            manualOverride,
            0,
            "task",
            "signature",
            "prompt",
        )
    }
}
