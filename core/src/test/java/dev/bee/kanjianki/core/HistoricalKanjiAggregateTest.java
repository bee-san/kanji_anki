package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class HistoricalKanjiAggregateTest {
    @Test
    public void cardMetricsClampNegativeCountsAndAverageOnlyPresentFsrsValues() {
        HistoricalKanjiAggregate aggregate = new HistoricalKanjiAggregate("拉");

        aggregate.addCard(-10, -2, -3, false, false, fsrs(2.0, null, 0.5));
        aggregate.addCard(30, 4, 1, true, true, fsrs(4.0, 8.0, null));

        assertEquals("拉", aggregate.kanji());
        assertEquals(1, aggregate.activeCards());
        assertEquals(1, aggregate.suspendedCards());
        assertEquals(1, aggregate.matureSupportCount());
        assertEquals(4, aggregate.totalReps());
        assertEquals(1, aggregate.totalLapses());
        assertEquals(15.0, aggregate.averageIntervalDays(), 0.0001);
        assertEquals(3.0, aggregate.averageStability(), 0.0001);
        assertEquals(8.0, aggregate.averageDifficulty(), 0.0001);
        assertEquals(0.5, aggregate.averageRetrievability(), 0.0001);

        KanjiImpactAnalyzer.MetricSnapshot impact = aggregate.impactMetricSnapshot();
        assertEquals(1, impact.activeCards);
        assertEquals(1, impact.suspendedCards);
        assertEquals(1, impact.matureCards);
        assertEquals(4, impact.reps);
        assertEquals(1, impact.lapses);
        assertEquals(15.0, impact.averageIntervalDays, 0.0001);
        assertEquals(3.0, impact.fsrsStability, 0.0001);
        assertEquals(8.0, impact.fsrsDifficulty, 0.0001);
        assertEquals(0.5, impact.fsrsRetrievability, 0.0001);
    }

    @Test
    public void emptyAggregateDefaultsToZeroesAndNullAverages() {
        HistoricalKanjiAggregate aggregate = new HistoricalKanjiAggregate(null);

        assertEquals("", aggregate.kanji());
        assertEquals(0, aggregate.activeCards());
        assertEquals(0, aggregate.suspendedCards());
        assertEquals(0, aggregate.matureSupportCount());
        assertEquals(0.0, aggregate.averageIntervalDays(), 0.0001);
        assertNull(aggregate.averageStability());
        assertNull(aggregate.averageDifficulty());
        assertNull(aggregate.averageRetrievability());
        assertEquals("", aggregate.reasonCode());
    }

    @Test
    public void dashboardEvidenceOverlaysWeaknessAndMaxSupportCounts() {
        HistoricalKanjiAggregate aggregate = new HistoricalKanjiAggregate("提");
        aggregate.addCard(10, 1, 0, false, false, null);

        aggregate.mergeDashboardEvidence(44, "weak_fsrs", 3, 2, 4);
        aggregate.mergeDashboardEvidence(12, null, 1, 8, 2);

        assertEquals(12, aggregate.weaknessScore());
        assertEquals("", aggregate.reasonCode());
        assertEquals(3, aggregate.activeExampleCount());
        assertEquals(8, aggregate.suspendedExampleCount());
        assertEquals(4, aggregate.matureSupportCount());
    }

    private static HistoricalKanjiAggregate.FsrsMemoryValues fsrs(Double stability, Double difficulty, Double retrievability) {
        return new HistoricalKanjiAggregate.FsrsMemoryValues(stability, difficulty, retrievability);
    }
}
