package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class KaniOutcomePolicyTest {
    @Test
    public void outcomeStatsUseOnlyKanjiWithBothSnapshots() {
        KaniOutcomePolicy.OutcomeStats stats = KaniOutcomePolicy.summarize(
                Arrays.asList(
                        outcome("弱", snapshot(90, 0), snapshot(45, 2)),
                        outcome("支", snapshot(70, 1), snapshot(64, 3)),
                        outcome("前", null, snapshot(30, 1)),
                        outcome("後", snapshot(60, 0), null),
                        outcome("浅", snapshot(60, 0), snapshot(57, 1)),
                        outcome("悪", snapshot(30, 2), snapshot(50, 1)),
                        null
                ),
                LadderHealthPolicy.Metric.empty()
        );

        assertEquals(2, stats.weakKanjiImproved().improvedCount());
        assertEquals(0.80, stats.weakKanjiImproved().averageBeforeWeakness(), 0.001);
        assertEquals(0.545, stats.weakKanjiImproved().averageAfterWeakness(), 0.001);
        assertEquals("弱", stats.weakKanjiImproved().examples().get(0).kanji());
        assertEquals("支", stats.weakKanjiImproved().examples().get(1).kanji());

        assertEquals(3, stats.matureSupportGained().gainedSupportCount());
        assertEquals(5, stats.matureSupportGained().matureSupportGained());
        assertEquals(2, stats.matureSupportGained().firstSupportCount());
        assertEquals("弱", stats.matureSupportGained().examples().get(0).kanji());
        assertEquals("支", stats.matureSupportGained().examples().get(1).kanji());
        assertEquals("浅", stats.matureSupportGained().examples().get(2).kanji());
    }

    @Test
    public void outcomeExamplesAreRankedByImpactThenKanjiAndLimitedToThree() {
        KaniOutcomePolicy.OutcomeStats stats = KaniOutcomePolicy.summarize(
                Arrays.asList(
                        outcome("丁", snapshot(80, 1), snapshot(30, 3)),
                        outcome("一", snapshot(80, 0), snapshot(30, 2)),
                        outcome("七", snapshot(90, 0), snapshot(60, 1)),
                        outcome("二", snapshot(60, 0), snapshot(50, 1)),
                        outcome("万", snapshot(70, 5), snapshot(66, 4))
                ),
                null
        );

        assertEquals(4, stats.weakKanjiImproved().improvedCount());
        assertEquals(3, stats.weakKanjiImproved().examples().size());
        assertEquals("一", stats.weakKanjiImproved().examples().get(0).kanji());
        assertEquals("丁", stats.weakKanjiImproved().examples().get(1).kanji());
        assertEquals("七", stats.weakKanjiImproved().examples().get(2).kanji());

        assertEquals(4, stats.matureSupportGained().gainedSupportCount());
        assertEquals(6, stats.matureSupportGained().matureSupportGained());
        assertEquals(3, stats.matureSupportGained().examples().size());
        assertEquals("一", stats.matureSupportGained().examples().get(0).kanji());
        assertEquals("丁", stats.matureSupportGained().examples().get(1).kanji());
        assertEquals("七", stats.matureSupportGained().examples().get(2).kanji());
        assertEquals(0, stats.ladderHealth().totalActiveItems());
    }

    @Test
    public void publicMetricsClampAndDefaultUnsafeInput() {
        KaniOutcomePolicy.OutcomeStats empty = KaniOutcomePolicy.OutcomeStats.empty();
        assertEquals(0, empty.weakKanjiImproved().improvedCount());
        assertEquals(0, empty.matureSupportGained().gainedSupportCount());

        KaniOutcomePolicy.OutcomeStats nullBacked = new KaniOutcomePolicy.OutcomeStats(null, null, null);
        assertTrue(nullBacked.weakKanjiImproved().examples().isEmpty());
        assertTrue(nullBacked.matureSupportGained().examples().isEmpty());
        assertEquals(0, nullBacked.ladderHealth().totalActiveItems());

        KaniOutcomePolicy.WeakKanjiImprovedMetric weak =
                new KaniOutcomePolicy.WeakKanjiImprovedMetric(-1, -2.0, -3.0, null);
        assertEquals(0, weak.improvedCount());
        assertEquals(0.0, weak.averageBeforeWeakness(), 0.0);
        assertEquals(0.0, weak.averageAfterWeakness(), 0.0);
        assertTrue(weak.examples().isEmpty());

        KaniOutcomePolicy.MatureSupportGainedMetric support =
                new KaniOutcomePolicy.MatureSupportGainedMetric(-1, -2, -3, null);
        assertEquals(0, support.gainedSupportCount());
        assertEquals(0, support.matureSupportGained());
        assertEquals(0, support.firstSupportCount());
        assertTrue(support.examples().isEmpty());

        KaniOutcomePolicy.MatureSupportGainedMetric legacySupport =
                new KaniOutcomePolicy.MatureSupportGainedMetric(2, 1, Collections.emptyList());
        assertEquals(2, legacySupport.gainedSupportCount());
        assertEquals(2, legacySupport.matureSupportGained());
        assertEquals(1, legacySupport.firstSupportCount());
    }

    @Test
    public void evidenceAndExampleValuesNormalizeUnsafeInput() {
        KaniOutcomePolicy.OutcomeEvidence evidence = outcome(null, snapshot(-1, -2), snapshot(-3, -4));
        assertEquals("", evidence.kanji());
        assertEquals(0, evidence.before().weaknessScore());
        assertEquals(0, evidence.before().matureSupportCount());
        assertEquals(0, evidence.after().weaknessScore());
        assertEquals(0, evidence.after().matureSupportCount());

        KaniOutcomePolicy.KanjiImprovement improvement =
                new KaniOutcomePolicy.KanjiImprovement(null, -0.2, -0.1);
        assertEquals("", improvement.kanji());
        assertEquals(0.0, improvement.beforeWeakness(), 0.0);
        assertEquals(0.0, improvement.afterWeakness(), 0.0);

        KaniOutcomePolicy.KanjiSupportGain gain = new KaniOutcomePolicy.KanjiSupportGain(null, -1, -2);
        assertEquals("", gain.kanji());
        assertEquals(0, gain.beforeMatureSupport());
        assertEquals(0, gain.afterMatureSupport());
    }

    private static KaniOutcomePolicy.OutcomeEvidence outcome(
            String kanji,
            KaniOutcomePolicy.OutcomeSnapshot before,
            KaniOutcomePolicy.OutcomeSnapshot after
    ) {
        return new KaniOutcomePolicy.OutcomeEvidence(kanji, before, after);
    }

    private static KaniOutcomePolicy.OutcomeSnapshot snapshot(int weaknessScore, int matureSupportCount) {
        return new KaniOutcomePolicy.OutcomeSnapshot(weaknessScore, matureSupportCount);
    }
}
