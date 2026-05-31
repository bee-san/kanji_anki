package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class TimelineCopyTest {
    @Test
    public void statusTextPreservesActiveRestingAndRetiredCases() {
        long now = 5_000L;

        assertEquals("Active repair", TimelineCopy.statusText(timeline(row(), studyItem("review", now)), now));
        assertEquals("Resting until review", TimelineCopy.statusText(timeline(row(), studyItem("review", now + 1L)), now));
        assertEquals("Retired by Anki support", TimelineCopy.statusText(timeline(row(), studyItem("retired", now - 1L)), now));
        assertEquals("Retired by Anki support", TimelineCopy.statusText(timeline(null, studyItem("review", now)), now));
    }

    @Test
    public void statusToneMapsRetiredRestingAndActiveCases() {
        long now = 5_000L;

        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.statusTone(timeline(row(), studyItem("retired", now - 1L)), now));
        assertEquals(TimelineCopy.Tone.NEUTRAL, TimelineCopy.statusTone(timeline(row(), studyItem("review", now + 1L)), now));
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.statusTone(timeline(row(), studyItem("review", now)), now));
    }

    @Test
    public void eventToneClassifiesKnownTimelineEvents() {
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.eventTone(TimelineCopy.EVENT_REVIEW_FAILED));
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.eventTone("support_dropped"));
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.eventTone(TimelineCopy.EVENT_REOPENED));
        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.eventTone(TimelineCopy.EVENT_REVIEW_PASSED));
        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.eventTone("support_improved"));
        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.eventTone("retired"));
        assertEquals(TimelineCopy.Tone.NEUTRAL, TimelineCopy.eventTone("sync"));
    }

    @Test
    public void sourceLineFormatsMissingExpressionReadingAndFullSource() {
        assertEquals("", TimelineCopy.sourceLine(event("", "")));
        assertEquals("Source: expr", TimelineCopy.sourceLine(event("expr", "")));
        assertEquals("Source: expr  reading", TimelineCopy.sourceLine(event("expr", "reading")));
    }

    @Test
    public void studyStateDetailPreservesRetiredAndReopenedCopy() {
        assertEquals(
                "No weak Anki evidence remained after sync, so Kani retired this repair.",
                TimelineCopy.studyStateDetail(true, null, 3)
        );
        assertEquals(
                "Kani reopened this kanji after sync found weak evidence again.",
                TimelineCopy.studyStateDetail(false, null, 3)
        );
        assertEquals(
                "Mature Anki support met the target: mature support 3 / target 3.",
                TimelineCopy.studyStateDetail(true, 3, 3)
        );
        assertEquals(
                "Mature Anki support fell below target: mature support 1 / target 3.",
                TimelineCopy.studyStateDetail(false, 1, 3)
        );
    }

    @Test
    public void reviewEventPreservesTypeTitleAndDetailMapping() {
        TimelineCopy.ReviewEvent manual = TimelineCopy.reviewEvent(review("good", false, false, true), "good");
        TimelineCopy.ReviewEvent recallFail = TimelineCopy.reviewEvent(review("again", false, false, false), "again");
        TimelineCopy.ReviewEvent writingMiss = TimelineCopy.reviewEvent(review("hard", true, false, false), "hard");
        TimelineCopy.ReviewEvent writingPass = TimelineCopy.reviewEvent(review("good", true, true, false), "good");
        TimelineCopy.ReviewEvent recallPass = TimelineCopy.reviewEvent(review("good", false, false, false), "good");

        assertEquals(TimelineCopy.EVENT_MANUAL_OVERRIDE, manual.eventType());
        assertEquals("Manual override", manual.title());
        assertEquals("Saved as good after manual confirmation.", manual.detail());
        assertEquals(TimelineCopy.EVENT_REVIEW_FAILED, recallFail.eventType());
        assertEquals("Review failed", recallFail.title());
        assertEquals("Recall missed; Kani scheduled another try.", recallFail.detail());
        assertEquals(TimelineCopy.EVENT_REVIEW_FAILED, writingMiss.eventType());
        assertEquals("Writing was not passed and was rated hard.", writingMiss.detail());
        assertEquals(TimelineCopy.EVENT_REVIEW_PASSED, writingPass.eventType());
        assertEquals("Review passed", writingPass.title());
        assertEquals("Writing passed and was rated good.", writingPass.detail());
        assertEquals("Recall review was rated good.", recallPass.detail());
    }

    private static RecordsStudyModels.KanjiRecoveryTimeline timeline(
            RecordsImportModels.DashboardRow row,
            RecordsStudyModels.StudyItem item
    ) {
        return new RecordsStudyModels.KanjiRecoveryTimeline(row, item, Collections.emptyList());
    }

    private static RecordsStudyModels.StudyItem studyItem(String state, long dueAtMillis) {
        return new RecordsStudyModels.StudyItem("x", state, dueAtMillis, 1.0, 5.0, 1, 0, 0, 1, null, 0L);
    }

    private static RecordsImportModels.DashboardRow row() {
        return new RecordsImportModels.DashboardRow(
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
                Collections.emptyList()
        );
    }

    private static RecordsImportModels.KanjiTimelineEvent event(String expression, String reading) {
        return new RecordsImportModels.KanjiTimelineEvent(
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
                "key"
        );
    }

    private static RecordsSchedulerModels.ReviewRequest review(
            String rating,
            boolean writingRequired,
            boolean writingPassed,
            boolean manualOverride
    ) {
        return new RecordsSchedulerModels.ReviewRequest(
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
                "prompt"
        );
    }
}
