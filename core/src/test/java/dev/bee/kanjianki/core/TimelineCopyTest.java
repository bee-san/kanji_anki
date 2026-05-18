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
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.eventTone("review_failed"));
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.eventTone("support_dropped"));
        assertEquals(TimelineCopy.Tone.WARNING, TimelineCopy.eventTone("reopened"));
        assertEquals(TimelineCopy.Tone.POSITIVE, TimelineCopy.eventTone("review_passed"));
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
}
