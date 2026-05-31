package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class FocusQueueCopyTest {
    @Test
    public void sourceEvidenceTextPrefersFirstActiveAndSuspendedExamples() {
        RecordsImportModels.Example active = example("active", "active-one");
        RecordsImportModels.Example laterActive = example("active", "active-two");
        RecordsImportModels.Example suspended = example("suspended", "suspended-one");
        RecordsImportModels.Example laterSuspended = example("suspended", "suspended-two");

        assertEquals(
                "From active-one · missed suspended-one",
                FocusQueueCopy.sourceEvidenceText(row("x", 0, 0, "reason", Arrays.asList(active, laterActive, suspended, laterSuspended)))
        );
        assertEquals("From active-one", FocusQueueCopy.sourceEvidenceText(row("x", 0, 0, "reason", Collections.singletonList(active))));
        assertEquals("Missed suspended-one", FocusQueueCopy.sourceEvidenceText(row("x", 0, 0, "reason", Collections.singletonList(suspended))));
        assertEquals("From your AnkiDroid sync", FocusQueueCopy.sourceEvidenceText(row("x", 0, 0, "reason", Collections.emptyList())));
    }

    @Test
    public void queueCardBodyPreservesFallbackSimilarAndRawReasonText() {
        assertEquals("Needs focused kanji practice.", FocusQueueCopy.queueCardBody(row("x", 0, 0, "")));
        assertEquals(
                "Shape mix-up made this a writing-practice target.",
                FocusQueueCopy.queueCardBody(row("similar", 0, 0, "Similar-kanji choice missed"))
        );
        assertEquals("Specific reason", FocusQueueCopy.queueCardBody(row("reason", 0, 0, "Specific reason")));
    }

    @Test
    public void focusReasonLineIncludesWeaknessSupportStageAndDueState() {
        long now = 5_000L;

        assertEquals(
                "weakness 42 · support 1/3 · kanji -> meaning · due now",
                FocusQueueCopy.focusReasonLine(
                        row("弱", 42, 1, "reason"),
                        item("弱", RecordsBase.LadderRung.KANJI_MEANING, StudyLadderRules.STATE_REVIEW, now, 1),
                        now,
                        3)
        );
        assertEquals(
                "write kanji · learning",
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
        return row(kanji, weaknessScore, matureSupportCount, reasonText, Collections.emptyList());
    }

    private static RecordsImportModels.DashboardRow row(
            String kanji,
            int weaknessScore,
            int matureSupportCount,
            String reasonText,
            java.util.List<RecordsImportModels.Example> examples
    ) {
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
                examples
        );
    }

    private static RecordsImportModels.Example example(String sourceType, String expression) {
        return new RecordsImportModels.Example(
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
                null
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
