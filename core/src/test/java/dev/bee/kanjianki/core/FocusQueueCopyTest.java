package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class FocusQueueCopyTest {
    @Test
    public void queueCardBodyPreservesFallbackSimilarAndRawReasonText() {
        assertEquals("Needs focused kanji practice.", FocusQueueCopy.queueCardBody(row("字", 0, 0, "")));
        assertEquals(
                "Shape mix-up made this a writing-practice target.",
                FocusQueueCopy.queueCardBody(row("似", 0, 0, "Similar-kanji choice missed"))
        );
        assertEquals("Specific reason", FocusQueueCopy.queueCardBody(row("理", 0, 0, "Specific reason")));
    }

    @Test
    public void focusReasonLineIncludesWeaknessSupportStageAndDueState() {
        long now = 5_000L;

        assertEquals(
                "Why: weakness 42 · support 1/3 · kanji -> meaning · due now",
                FocusQueueCopy.focusReasonLine(
                        row("弱", 42, 1, "reason"),
                        item("弱", RecordsBase.LadderRung.KANJI_MEANING, StudyLadderRules.STATE_REVIEW, now, 1),
                        now,
                        3)
        );
        assertEquals(
                "Why: write kanji · learning",
                FocusQueueCopy.focusReasonLine(
                        row("書", 0, 3, "reason"),
                        item("書", RecordsBase.LadderRung.WRITE_KANJI, StudyLadderRules.STATE_LEARNING, now + 1L, 1),
                        now,
                        3)
        );
    }

    @Test
    public void recognitionStageLabelNamesEveryRung() {
        assertEquals("kanji -> meaning", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.KANJI_MEANING)));
        assertEquals("write kanji", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.WRITE_KANJI)));
        assertEquals("type meaning", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.TYPE_MEANING)));
        assertEquals("similar kanji", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.SIMILAR_KANJI)));
        assertEquals("meaning -> kanji", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.MEANING_KANJI)));
        assertEquals("font -> meaning", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.FONT_MEANING)));
        assertEquals("word -> reading", FocusQueueCopy.recognitionStageLabel(item(RecordsBase.LadderRung.WORD_READING)));
    }

    private static RecordsImportModels.DashboardRow row(String kanji, int weaknessScore, int matureSupportCount, String reasonText) {
        return new RecordsImportModels.DashboardRow(
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
                Collections.emptyList()
        );
    }

    private static RecordsStudyModels.StudyItem item(RecordsBase.LadderRung rung) {
        return item("字", rung, StudyLadderRules.STATE_REVIEW, 0L, 1);
    }

    private static RecordsStudyModels.StudyItem item(
            String kanji,
            RecordsBase.LadderRung rung,
            String state,
            long dueAtMillis,
            int totalReviews
    ) {
        return new RecordsStudyModels.StudyItem(kanji, state, dueAtMillis, 1.0, 5.0, totalReviews, 0, 0, 1, null, 0L)
                .copyBuilder()
                .rung(rung)
                .build();
    }
}
