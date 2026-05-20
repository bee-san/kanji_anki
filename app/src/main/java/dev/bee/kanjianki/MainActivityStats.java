package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;

import dev.bee.kanjianki.core.KanjiImpactAnalyzer;
import dev.bee.kanjianki.core.StatsTextCopy;
import dev.bee.kanjianki.data.StudyStatsStore;

import java.util.ArrayList;
import java.util.List;

abstract class MainActivityStats extends MainActivityGames {
    void renderStats() {
        base("stats");
        content.addView(MainActivityStatsCompose.statsScreenView(this));
    }

    List<KanjiImpactAnalyzer.Row> notHelpingRows(KanjiImpactAnalyzer.Report report) {
        return KanjiImpactAnalyzer.notHelpingRows(report);
    }

    List<String> ladderDistributionRows(StudyStatsStore.LadderHealthMetric metric) {
        List<String> rows = new ArrayList<>();
        for (RecordsBase.LadderRung rung : RecordsBase.LadderRung.values()) {
            rows.add(StatsTextCopy.ladderDistributionRow(rung, metric.countFor(rung)));
        }
        return rows;
    }

    List<String> weaknessImprovementExamples(StudyStatsStore.WeakKanjiImprovedMetric metric) {
        List<String> examples = new ArrayList<>();
        int maxExamples = Math.min(3, metric.examples.size());
        for (int i = 0; i < maxExamples; i++) {
            StudyStatsStore.KanjiImprovement example = metric.examples.get(i);
            examples.add(StatsTextCopy.weaknessImprovementExample(example.kanji, example.beforeWeakness, example.afterWeakness));
        }
        return examples;
    }

    List<String> supportGainExamples(StudyStatsStore.MatureSupportGainedMetric metric) {
        List<String> examples = new ArrayList<>();
        for (StudyStatsStore.KanjiSupportGain example : metric.examples) {
            examples.add(StatsTextCopy.supportGainExample(example.kanji, example.beforeMatureSupport, example.afterMatureSupport));
        }
        return examples;
    }
}
