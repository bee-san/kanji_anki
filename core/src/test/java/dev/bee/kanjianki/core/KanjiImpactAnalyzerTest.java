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

    @Test
    public void retentionScoreFallsBackFromFsrsToReviewsThenMaturityDefaults() {
        assertEquals(0.0, metricWithoutFsrs(1, 0, 0, 0.0, 5, 9).retentionScore(), 0.001);
        assertEquals(0.88, metricWithoutFsrs(1, 0, 1, 30.0, 0, 0).retentionScore(), 0.001);
        assertEquals(0.50, metricWithoutFsrs(1, 0, 0, 0.0, 0, 0).retentionScore(), 0.001);
        assertEquals(1.0, metric(1, 0, 0, 0.0, 0, 0, null, 150.0).retentionScore(), 0.001);
        assertEquals(0.0, metric(1, 0, 0, 0.0, 0, 0, null, -0.2).retentionScore(), 0.001);
    }

    @Test
    public void analyzeSkipsNullAndBlankHistoriesAndHandlesNullInput() {
        KanjiImpactAnalyzer analyzer = new KanjiImpactAnalyzer();

        KanjiImpactAnalyzer.Report nullReport = analyzer.analyze(null);
        KanjiImpactAnalyzer.Report emptyReport = analyzer.analyze(Collections.emptyList());
        KanjiImpactAnalyzer.Report report = analyzer.analyze(Arrays.asList(
                null,
                history("", metric(1, 0, 0, 1.0, 1, 0, 5.0, 0.80), metric(1, 0, 0, 1.0, 1, 0, 5.0, 0.80), null, null, 1, 0, 1),
                history("拉", metric(2, 0, 0, 1.0, 4, 1, 5.0, 0.75), metric(2, 0, 0, 1.0, 4, 1, 5.0, 0.75), null, null, 2, 0, 1)
        ));

        assertTrue(nullReport.empty());
        assertTrue(emptyReport.empty());
        assertEquals(1, report.notHelpingCount);
        assertEquals(1, report.rows.size());
        assertEquals("拉", report.rows.get(0).kanji);
    }

    @Test
    public void reportConstructorNormalizesNegativeCountsAndNullRows() {
        KanjiImpactAnalyzer.Report report = new KanjiImpactAnalyzer.Report(-1, -2, -3, null);

        assertTrue(report.empty());
        assertTrue(report.rows.isEmpty());
    }

    @Test
    public void missingCurrentMetricsProduceNeedsMoreCardsRowWithZeroScores() {
        KanjiImpactAnalyzer.Report report = new KanjiImpactAnalyzer().analyze(Collections.singletonList(
                history("欠", metric(2, 0, 1, 20.0, 10, 1, 6.0, 0.80), null, null, null, 1, 0, 2)
        ));

        KanjiImpactAnalyzer.Row row = report.rows.get(0);
        assertEquals(KanjiImpactAnalyzer.BUCKET_NEEDS_MORE_CARDS, row.bucket);
        assertEquals(6.0, row.baselineDifficulty, 0.001);
        assertEquals(0.0, row.currentDifficulty, 0.001);
        assertEquals(0.80, row.baselineRetention, 0.001);
        assertEquals(0.0, row.currentRetention, 0.001);
        assertEquals(0, row.currentCardCount);
    }

    @Test
    public void rowsSortByBucketRetentionDeltaThenKanji() {
        KanjiImpactAnalyzer.Report report = new KanjiImpactAnalyzer().analyze(Arrays.asList(
                history("歩", metric(2, 0, 0, 20.0, 10, 3, 6.0, 0.60), metric(2, 0, 0, 20.0, 10, 2, 5.9, 0.62), null, null, 2, 0, 4),
                history("亜", metric(2, 0, 0, 20.0, 10, 3, 6.0, 0.60), metric(2, 0, 0, 20.0, 10, 3, 6.0, 0.60), null, null, 2, 0, 4),
                history("腕", metric(2, 0, 0, 20.0, 10, 3, 6.0, 0.60), metric(2, 0, 0, 20.0, 10, 0, 4.0, 0.90), null, null, 2, 0, 4)
        ));

        assertEquals("腕", report.rows.get(0).kanji);
        assertEquals("歩", report.rows.get(1).kanji);
        assertEquals("亜", report.rows.get(2).kanji);
    }

    @Test
    public void constructorsClampCountsAndHandleMissingVarargs() {
        KanjiImpactAnalyzer.KanjiHistory history = new KanjiImpactAnalyzer.KanjiHistory(null, null, null, null, null, (int[]) null);
        KanjiImpactAnalyzer.MetricSnapshot snapshot = new KanjiImpactAnalyzer.MetricSnapshot(-1, -2, -3, -4.0, -5, -6, (Double[]) null);
        KanjiImpactAnalyzer.KanjiHistory partialCounts = new KanjiImpactAnalyzer.KanjiHistory("片", null, null, null, null, 1);
        KanjiImpactAnalyzer.MetricSnapshot partialFsrs = new KanjiImpactAnalyzer.MetricSnapshot(1, 0, 0, 0.0, 0, 0, 2.0);

        assertEquals("", history.kanji);
        assertEquals(0, history.commonCards);
        assertEquals(1, partialCounts.commonCards);
        assertEquals(0, partialCounts.newCards);
        assertEquals(0, snapshot.totalCards());
        assertEquals(5.0, snapshot.difficultyScore(), 0.001);
        assertEquals(Double.valueOf(2.0), partialFsrs.fsrsStability);
        assertEquals(null, partialFsrs.fsrsDifficulty);
    }

    @Test
    public void bucketGuardsAndHelpSignalsCoverBoundaryCases() {
        KanjiImpactAnalyzer.Report report = new KanjiImpactAnalyzer().analyze(Arrays.asList(
                history("少", metric(1, 0, 0, 1.0, 1, 0, 5.0, 0.80), metric(1, 0, 0, 1.0, 1, 0, 5.0, 0.80), null, null, 1, 0, 1),
                history("共", metric(2, 0, 0, 1.0, 1, 0, 5.0, 0.80), metric(2, 0, 0, 1.0, 1, 0, 5.0, 0.80), null, null, 0, 0, 1),
                history("基", null, metric(2, 0, 0, 1.0, 1, 0, 5.0, 0.80), null, null, 1, 0, 1),
                history("熟", metric(2, 0, 0, 1.0, 1, 0, 5.0, 0.80), metric(2, 0, 1, 1.0, 1, 0, 5.0, 0.80), null, null, 1, 0, 1),
                history("易", metric(2, 0, 0, 1.0, 1, 0, 6.0, 0.80), metric(2, 0, 0, 1.0, 1, 0, 5.6, 0.80), null, null, 1, 0, 1),
                history("覚", metric(2, 0, 0, 1.0, 1, 0, 5.0, 0.80), metric(2, 0, 0, 1.0, 1, 0, 5.0, 0.90), null, null, 1, 0, 1)
        ));

        assertEquals(3, report.helpedCount);
        assertEquals(3, report.needsMoreCardsCount);
        assertEquals(false, new KanjiImpactAnalyzer.Report(1, 0, 0, Collections.emptyList()).empty());
        assertEquals(false, new KanjiImpactAnalyzer.Report(0, 1, 0, Collections.emptyList()).empty());
        assertEquals(false, new KanjiImpactAnalyzer.Report(0, 0, 1, Collections.emptyList()).empty());
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
