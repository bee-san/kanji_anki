package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections

class TimelineCopyTest {
    @Test
    fun statusTextPreservesActiveRestingAndRetiredCases() {
        val now = 5_000L

        assertEquals("Active repair", TimelineCopy.statusText(timeline(row(), studyItem("review", now)), now))
        assertEquals("Resting until review", TimelineCopy.statusText(timeline(row(), studyItem("review", now + 1L)), now))
        assertEquals("Retired by Anki support", TimelineCopy.statusText(timeline(row(), studyItem("retired", now - 1L)), now))
        assertEquals("Retired by Anki support", TimelineCopy.statusText(timeline(null, studyItem("review", now)), now))
    }

    @Test
    fun statusToneMapsRetiredRestingAndActiveCases() {
        val now = 5_000L

        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.statusTone(timeline(row(), studyItem("retired", now - 1L)), now))
        assertEquals(TimelineCopy.Tone.NEUTRAL, TimelineCopy.statusTone(timeline(row(), studyItem("review", now + 1L)), now))
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.statusTone(timeline(row(), studyItem("review", now)), now))
    }

    @Test
    fun eventToneClassifiesKnownTimelineEvents() {
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.eventTone(TimelineCopy.EVENT_REVIEW_FAILED))
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.eventTone("support_dropped"))
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.eventTone(TimelineCopy.EVENT_REOPENED))
        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.eventTone(TimelineCopy.EVENT_REVIEW_PASSED))
        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.eventTone("support_improved"))
        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.eventTone("retired"))
        assertEquals(TimelineCopy.Tone.NEUTRAL, TimelineCopy.eventTone("sync"))
    }

    @Test
    fun sourceLineFormatsMissingExpressionReadingAndFullSource() {
        assertEquals("", TimelineCopy.sourceLine(event("", "")))
        assertEquals("Source: expr", TimelineCopy.sourceLine(event("expr", "")))
        assertEquals("Source: expr  reading", TimelineCopy.sourceLine(event("expr", "reading")))
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
        assertEquals("Review failed", recallFail.title())
        assertEquals("Recall missed; Kani scheduled another try.", recallFail.detail())
        assertEquals(TimelineCopy.EVENT_REVIEW_FAILED, writingMiss.eventType())
        assertEquals("Writing was not passed and was rated hard.", writingMiss.detail())
        assertEquals(TimelineCopy.EVENT_REVIEW_PASSED, writingPass.eventType())
        assertEquals("Review passed", writingPass.title())
        assertEquals("Writing passed and was rated good.", writingPass.detail())
        assertEquals("Recall review was rated good.", recallPass.detail())
    }

    private fun timeline(
        row: RecordsImportModels.DashboardRow?,
        item: RecordsStudyModels.StudyItem,
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
