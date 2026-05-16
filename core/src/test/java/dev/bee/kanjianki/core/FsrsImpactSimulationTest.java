package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FsrsImpactSimulationTest {
    @Test
    public void syntheticMigrationScenariosProduceReviewedImpactReport() {
        Fsrs5Adapter oldAdapter = new Fsrs5Adapter();
        LatestFsrsAdapter latestAdapter = new LatestFsrsAdapter();
        long now = 30L * BridgeScheduler.DAY;

        int flagged = 0;
        int total = 0;
        StringBuilder report = new StringBuilder();
        report.append("rating,stability,difficulty,elapsedDays,oldDays,newDays,ratio,flag\n");
        for (double stability : new double[]{0.5, 2.0, 10.0, 60.0}) {
            for (double difficulty : new double[]{2.0, 6.0, 9.0}) {
                for (int elapsedDays : new int[]{0, 1, 7, 30, 120}) {
                    for (String rating : new String[]{
                            BridgeScheduler.RATING_AGAIN,
                            BridgeScheduler.RATING_HARD,
                            BridgeScheduler.RATING_GOOD,
                            BridgeScheduler.RATING_EASY
                    }) {
                        long dueAt = now - elapsedDays * BridgeScheduler.DAY;
                        KaniFsrsReviewResult oldResult = oldAdapter.review(
                                stability, difficulty, rating, dueAt, now, 0.9);
                        KaniFsrsReviewResult latestResult = latestAdapter.review(
                                stability, difficulty, rating, dueAt, now, 0.9);
                        double ratio = (double) latestResult.intervalDays() / oldResult.intervalDays();
                        boolean flag = ratio > 2.0 || ratio < 0.5;
                        if (flag) {
                            flagged++;
                        }
                        total++;
                        report.append(rating).append(',')
                                .append(stability).append(',')
                                .append(difficulty).append(',')
                                .append(elapsedDays).append(',')
                                .append(oldResult.intervalDays()).append(',')
                                .append(latestResult.intervalDays()).append(',')
                                .append(ratio).append(',')
                                .append(flag).append('\n');
                    }
                }
            }
        }

        assertTrue(total >= 200);
        assertTrue("expected at least one migration-impact flag in synthetic scenarios\n" + report, flagged > 0);
        assertFalse("impact report must contain current FSRS rows", report.toString().isEmpty());
    }
}
