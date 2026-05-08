package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class KanjiImpactAnalyzerTest {
    @Test
    public void bucketsHelpedWhenSameCardsImprove() {
        KanjiImpactAnalyzer.Report report = new KanjiImpactAnalyzer().analyze(Collections.singletonList(
                history(
                        "裂",
                        metric(2, 0, 1, 20.0, 20, 7, 7.2, 0.62),
                        metric(4, 0, 4, 45.0, 40, 5, 5.8, 0.84),
                        metric(2, 0, 1, 20.0, 20, 7, 7.2, 0.62),
                        metric(2, 0, 2, 42.0, 32, 4, 5.8, 0.84),
                        2,
                        2,
                        5
                )
        ));

        assertEquals(1, report.helpedCount);
        assertEquals(KanjiImpactAnalyzer.BUCKET_HELPED, report.rows.get(0).bucket);
        assertTrue(report.rows.get(0).summary().contains("裂: difficulty 7.2 -> 5.8"));
        assertTrue(report.rows.get(0).summary().contains("retention 62% -> 84%"));
        assertEquals(2, report.rows.get(0).newCardCount);
    }

    @Test
    public void isolatesNewCardsFromSameCardImpact() {
        KanjiImpactAnalyzer.Report report = new KanjiImpactAnalyzer().analyze(Collections.singletonList(
                history(
                        "謎",
                        metric(1, 0, 0, 3.0, 10, 2, 6.0, 0.80),
                        metric(3, 0, 2, 40.0, 20, 2, 4.0, 0.92),
                        metric(1, 0, 0, 3.0, 10, 2, 6.0, 0.80),
                        metric(1, 0, 0, 3.0, 10, 2, 6.0, 0.80),
                        1,
                        2,
                        3
                )
        ));

        assertEquals(0, report.helpedCount);
        assertEquals(1, report.notHelpingCount);
        assertEquals(KanjiImpactAnalyzer.BUCKET_NOT_HELPING, report.rows.get(0).bucket);
        assertEquals(2, report.rows.get(0).newCardCount);
    }

    @Test
    public void fallsBackWhenFsrsIsMissing() {
        KanjiImpactAnalyzer.Report report = new KanjiImpactAnalyzer().analyze(Collections.singletonList(
                history(
                        "拉",
                        metricWithoutFsrs(2, 0, 0, 3.0, 10, 4),
                        metricWithoutFsrs(2, 0, 1, 35.0, 20, 1),
                        metricWithoutFsrs(2, 0, 0, 3.0, 10, 4),
                        metricWithoutFsrs(2, 0, 1, 35.0, 20, 1),
                        2,
                        0,
                        4
                )
        ));

        assertEquals(1, report.helpedCount);
        assertTrue(report.rows.get(0).currentRetention > report.rows.get(0).baselineRetention);
        assertTrue(report.rows.get(0).currentDifficulty < report.rows.get(0).baselineDifficulty);
    }

    @Test
    public void sparseDataNeedsMoreCardsAdvice() {
        KanjiImpactAnalyzer.Report report = new KanjiImpactAnalyzer().analyze(Collections.singletonList(
                history(
                        "麺",
                        metric(1, 0, 0, 2.0, 1, 0, 5.0, 0.70),
                        metric(1, 0, 0, 2.0, 1, 0, 5.0, 0.70),
                        null,
                        null,
                        0,
                        1,
                        1
                )
        ));

        assertEquals(1, report.needsMoreCardsCount);
        assertEquals(KanjiImpactAnalyzer.BUCKET_NEEDS_MORE_CARDS, report.rows.get(0).bucket);
        assertEquals("Immerse and mine more flashcards for this kanji before judging Kani.", report.rows.get(0).advice);
    }

    @Test
    public void negativeDataSaysNotHelpingYet() {
        KanjiImpactAnalyzer.Report report = new KanjiImpactAnalyzer().analyze(Arrays.asList(
                history(
                        "提",
                        metric(2, 0, 1, 20.0, 20, 2, 5.0, 0.85),
                        metric(2, 0, 1, 20.0, 20, 5, 6.2, 0.70),
                        metric(2, 0, 1, 20.0, 20, 2, 5.0, 0.85),
                        metric(2, 0, 1, 20.0, 20, 5, 6.2, 0.70),
                        2,
                        0,
                        6
                )
        ));

        assertEquals(1, report.notHelpingCount);
        assertEquals("Kani is not moving the needle yet.", report.rows.get(0).advice);
    }

    private static KanjiImpactAnalyzer.KanjiHistory history(
            String kanji,
            KanjiImpactAnalyzer.MetricSnapshot baseline,
            KanjiImpactAnalyzer.MetricSnapshot current,
            KanjiImpactAnalyzer.MetricSnapshot sameBaseline,
            KanjiImpactAnalyzer.MetricSnapshot sameCurrent,
            int commonCards,
            int newCards,
            int reviews
    ) {
        return new KanjiImpactAnalyzer.KanjiHistory(kanji, baseline, current, sameBaseline, sameCurrent, commonCards, newCards, reviews);
    }

    private static KanjiImpactAnalyzer.MetricSnapshot metric(int active, int suspended, int mature, double interval, int reps, int lapses, Double difficulty, Double retention) {
        return new KanjiImpactAnalyzer.MetricSnapshot(active, suspended, mature, interval, reps, lapses, null, difficulty, retention);
    }

    private static KanjiImpactAnalyzer.MetricSnapshot metricWithoutFsrs(int active, int suspended, int mature, double interval, int reps, int lapses) {
        return new KanjiImpactAnalyzer.MetricSnapshot(active, suspended, mature, interval, reps, lapses, null, null, null);
    }
}
