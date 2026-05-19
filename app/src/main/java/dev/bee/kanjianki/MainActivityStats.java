package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import android.graphics.Color;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.KanjiImpactAnalyzer;
import dev.bee.kanjianki.core.StatsTextCopy;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.data.StudyStatsStore;

import java.util.ArrayList;
import java.util.List;

abstract class MainActivityStats extends MainActivityGames {
    void renderStats() {
        base("stats");
        StudyStatsStore.KaniOutcomeStats stats = store.kaniOutcomeStats();
        StudyStatsStore.StudyTaskTimeStats studyTime = store.studyTaskTimeStats(System.currentTimeMillis());
        content.addView(fullWidthHomeButton());
        content.addView(text("Stats", 34, INK, true));
        content.addView(statsVerdictPanel(stats));
        content.addView(text("Kani does not replace Anki. It repairs weak kanji from your Anki reviews, then shows whether Anki evidence caught up afterward.", 16, MUTED, false));
        addSpace(10);

        content.addView(outcomePanel(
                "Weakness Burn-Down",
                StudyTextCopy.countText(stats.weakKanjiImproved.improvedCount, "weak kanji improved", "weak kanji improved"),
                weaknessImprovementBody(stats.weakKanjiImproved),
                weaknessImprovementExamples(stats.weakKanjiImproved),
                TEAL
        ));
        content.addView(outcomePanel(
                "Anki Support Conversion",
                StudyTextCopy.countText(stats.matureSupportGained.matureSupportGained, "mature card gained", "mature cards gained"),
                StudyTextCopy.countText(stats.matureSupportGained.firstSupportCount, "kanji gained first mature support", "kanji gained first mature support") + ".",
                supportGainExamples(stats.matureSupportGained),
                BLUE
        ));
        content.addView(notHelpingPanel(store.kanjiImpactReport()));
        content.addView(ladderHealthPanel(stats.ladderHealth));
        content.addView(studyTimePanel(studyTime));
    }

    LinearLayout statsVerdictPanel(StudyStatsStore.KaniOutcomeStats stats) {
        boolean working = stats != null && StatsTextCopy.verdictWorking(
                stats.weakKanjiImproved.improvedCount,
                stats.matureSupportGained.matureSupportGained
        );
        boolean hasLadder = stats != null && StatsTextCopy.verdictHasLadder(stats.ladderHealth.totalActiveItems);
        int stroke;
        int background;
        if (working) {
            stroke = TEAL;
            background = Color.rgb(238, 252, 250);
        } else if (hasLadder) {
            stroke = GOLD;
            background = Color.rgb(255, 250, 226);
        } else {
            stroke = Color.rgb(178, 178, 186);
            background = Color.rgb(246, 246, 248);
        }
        LinearLayout box = panelBox(background, stroke);
        box.addView(text(StatsTextCopy.verdictTitle(working), 24, working ? TEAL : MUTED, true));
        box.addView(text(statsVerdictBody(stats, working, hasLadder), 15, working ? INK : MUTED, false));
        return box;
    }

    String statsVerdictBody(StudyStatsStore.KaniOutcomeStats stats, boolean working, boolean hasLadder) {
        if (stats == null) {
            return StatsTextCopy.verdictBody(false, working, hasLadder, 0, 0, 0, 0, 0);
        }
        StudyStatsStore.LadderHealthMetric ladder = stats.ladderHealth;
        return StatsTextCopy.verdictBody(
                true,
                working,
                hasLadder,
                stats.weakKanjiImproved.improvedCount,
                stats.matureSupportGained.matureSupportGained,
                ladder.promotionReadyCount,
                ladder.demotionRiskCount,
                ladder.totalActiveItems
        );
    }

    LinearLayout studyTimePanel(StudyStatsStore.StudyTaskTimeStats stats) {
        LinearLayout box = panelBox(Color.WHITE, CORAL);
        box.addView(text("Answered study time", 18, MUTED, true));
        box.addView(text("Today: " + formatStudyTime(stats.todayMillis), 24, INK, true));
        box.addView(text("Last 7 days: " + formatStudyTime(stats.lastSevenDaysMillis), 16, MUTED, false));
        box.addView(text("Answered tasks: " + stats.answeredTasks, 16, MUTED, false));
        box.addView(text("Avg / task: " + formatStudyTime(stats.averageMillisPerTask()), 16, MUTED, false));
        return box;
    }

    LinearLayout outcomePanel(String title, String value, String body, List<String> examples, int stroke) {
        LinearLayout box = statPanel(title, value, body, stroke);
        for (String example : examples) {
            box.addView(text(example, 17, INK, true));
        }
        return box;
    }

    LinearLayout ladderHealthPanel(StudyStatsStore.LadderHealthMetric metric) {
        LinearLayout box = statPanel(
                "Ladder Health",
                StudyTextCopy.countText(metric.totalActiveItems, "active kanji on the ladder", "active kanji on the ladder"),
                ladderHealthBody(metric),
                GOLD
        );
        for (String row : ladderDistributionRows(metric)) {
            box.addView(text(row, 16, INK, false));
        }
        return box;
    }

    LinearLayout notHelpingPanel(KanjiImpactAnalyzer.Report report) {
        List<KanjiImpactAnalyzer.Row> rows = notHelpingRows(report);
        LinearLayout box = statPanel(
                "Kani Not Helping Yet",
                StudyTextCopy.countText(rows.size(), "kanji with enough evidence", "kanji with enough evidence"),
                notHelpingBody(report, rows),
                CORAL
        );
        int maxRows = Math.min(5, rows.size());
        for (int i = 0; i < maxRows; i++) {
            KanjiImpactAnalyzer.Row row = rows.get(i);
            box.addView(text(notHelpingRowText(row), 16, INK, true));
        }
        if (report != null && report.needsMoreCardsCount > 0) {
            box.addView(text(StudyTextCopy.countText(report.needsMoreCardsCount, "kanji still needs more Anki evidence", "kanji still need more Anki evidence") + ".", 15, MUTED, false));
        }
        return box;
    }

    List<KanjiImpactAnalyzer.Row> notHelpingRows(KanjiImpactAnalyzer.Report report) {
        return KanjiImpactAnalyzer.notHelpingRows(report);
    }

    String notHelpingBody(KanjiImpactAnalyzer.Report report, List<KanjiImpactAnalyzer.Row> rows) {
        return StatsTextCopy.notHelpingBody(report == null || report.empty(), !rows.isEmpty());
    }

    String notHelpingRowText(KanjiImpactAnalyzer.Row row) {
        return StatsTextCopy.notHelpingRowText(row.kanji, row.reviewCount, row.sameCardCount, row.retentionDelta, row.difficultyDelta);
    }

    String ladderHealthBody(StudyStatsStore.LadderHealthMetric metric) {
        return StatsTextCopy.ladderHealthBody(
                metric.totalActiveItems,
                metric.promotionReadyCount,
                metric.demotionRiskCount,
                metric.demotionReadyCount,
                metric.ladderPromotionIntervalDays,
                metric.ladderDemotionFailStreak
        );
    }

    List<String> ladderDistributionRows(StudyStatsStore.LadderHealthMetric metric) {
        List<String> rows = new ArrayList<>();
        for (RecordsBase.LadderRung rung : RecordsBase.LadderRung.values()) {
            rows.add(StatsTextCopy.ladderDistributionRow(rung, metric.countFor(rung)));
        }
        return rows;
    }

    String weaknessImprovementBody(StudyStatsStore.WeakKanjiImprovedMetric metric) {
        return StatsTextCopy.weaknessImprovementBody(
                metric.improvedCount,
                metric.averageBeforeWeakness,
                metric.averageAfterWeakness
        );
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

    String formatStudyTime(long millis) {
        return StatsTextCopy.formatStudyTime(millis);
    }

    LinearLayout statPanel(String title, String value, String body, int stroke) {
        LinearLayout box = panelBox(Color.WHITE, stroke);
        box.addView(text(title, 18, MUTED, true));
        box.addView(text(value, 25, INK, true));
        box.addView(text(body, 15, MUTED, false));
        return box;
    }
}
