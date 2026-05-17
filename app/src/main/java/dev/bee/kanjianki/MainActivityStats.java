package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import android.graphics.Color;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.KanjiImpactAnalyzer;
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
                countText(stats.weakKanjiImproved.improvedCount, "weak kanji improved", "weak kanji improved"),
                weaknessImprovementBody(stats.weakKanjiImproved),
                weaknessImprovementExamples(stats.weakKanjiImproved),
                TEAL
        ));
        content.addView(outcomePanel(
                "Anki Support Conversion",
                countText(stats.matureSupportGained.matureSupportGained, "mature card gained", "mature cards gained"),
                countText(stats.matureSupportGained.firstSupportCount, "kanji gained first mature support", "kanji gained first mature support") + ".",
                supportGainExamples(stats.matureSupportGained),
                BLUE
        ));
        content.addView(notHelpingPanel(store.kanjiImpactReport()));
        content.addView(ladderHealthPanel(stats.ladderHealth));
        content.addView(studyTimePanel(studyTime));
    }

    LinearLayout statsVerdictPanel(StudyStatsStore.KaniOutcomeStats stats) {
        boolean working = stats != null
                && (stats.weakKanjiImproved.improvedCount > 0 || stats.matureSupportGained.matureSupportGained > 0);
        boolean hasLadder = stats != null && stats.ladderHealth.totalActiveItems > 0;
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
        box.addView(text(working ? "Kani is working for you" : "Kani is not currently working for you", 24, working ? TEAL : MUTED, true));
        box.addView(text(statsVerdictBody(stats, working, hasLadder), 15, working ? INK : MUTED, false));
        return box;
    }

    String statsVerdictBody(StudyStatsStore.KaniOutcomeStats stats, boolean working, boolean hasLadder) {
        if (stats == null) {
            return "No Kani evidence is available yet. Study weak kanji, then sync AnkiDroid so this page can compare before and after.";
        }
        StudyStatsStore.LadderHealthMetric ladder = stats.ladderHealth;
        if (working) {
            List<String> signals = new ArrayList<>();
            if (stats.weakKanjiImproved.improvedCount > 0) {
                signals.add(countText(stats.weakKanjiImproved.improvedCount, "weak kanji is burning down", "weak kanji are burning down"));
            }
            if (stats.matureSupportGained.matureSupportGained > 0) {
                signals.add(countText(stats.matureSupportGained.matureSupportGained, "mature Anki card has been gained", "mature Anki cards have been gained"));
            }
            if (ladder.promotionReadyCount > 0) {
                signals.add(countText(ladder.promotionReadyCount, "review-phase item crossed the FSRS climb threshold", "review-phase items crossed the FSRS climb threshold"));
            }
            String body = String.join(". ", signals) + ".";
            if (ladder.demotionRiskCount > 0) {
                body += " Watch " + countText(ladder.demotionRiskCount, "review-phase item with a miss streak", "review-phase items with miss streaks") + ".";
            }
            return body;
        }
        if (hasLadder) {
            return "Kani is tracking "
                    + countText(ladder.totalActiveItems, "active kanji", "active kanji")
                    + ", but no weakness burn-down or mature Anki support conversion has landed yet. Study due reviews, then sync AnkiDroid.";
        }
        return "No before-and-after evidence yet. Do Kani reviews, then sync AnkiDroid so this page can compare weak kanji and mature support.";
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
                countText(metric.totalActiveItems, "active kanji on the ladder", "active kanji on the ladder"),
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
                countText(rows.size(), "kanji with enough evidence", "kanji with enough evidence"),
                notHelpingBody(report, rows),
                CORAL
        );
        int maxRows = Math.min(5, rows.size());
        for (int i = 0; i < maxRows; i++) {
            KanjiImpactAnalyzer.Row row = rows.get(i);
            box.addView(text(notHelpingRowText(row), 16, INK, true));
        }
        if (report != null && report.needsMoreCardsCount > 0) {
            box.addView(text(countText(report.needsMoreCardsCount, "kanji still needs more Anki evidence", "kanji still need more Anki evidence") + ".", 15, MUTED, false));
        }
        return box;
    }

    List<KanjiImpactAnalyzer.Row> notHelpingRows(KanjiImpactAnalyzer.Report report) {
        List<KanjiImpactAnalyzer.Row> rows = new ArrayList<>();
        if (report == null) {
            return rows;
        }
        for (KanjiImpactAnalyzer.Row row : report.rows) {
            if (KanjiImpactAnalyzer.BUCKET_NOT_HELPING.equals(row.bucket)) {
                rows.add(row);
            }
        }
        return rows;
    }

    String notHelpingBody(KanjiImpactAnalyzer.Report report, List<KanjiImpactAnalyzer.Row> rows) {
        if (report == null || report.empty()) {
            return "No Kani impact evidence yet. Review in Kani, then sync AnkiDroid so this page can compare before and after.";
        }
        if (rows.isEmpty()) {
            return "No sufficiently proven not-helping kanji right now. Sparse cases stay out of this list until Kani has enough reviews and synced Anki evidence.";
        }
        return "Only kanji with at least 3 Kani reviews, 2 current Anki cards, and same-card before/after evidence appear here.";
    }

    String notHelpingRowText(KanjiImpactAnalyzer.Row row) {
        return row.kanji
                + "  "
                + row.reviewCount
                + " Kani reviews · "
                + row.sameCardCount
                + " same-card checks · retention "
                + formatSignedPercent(row.retentionDelta)
                + " · difficulty "
                + formatSignedDecimal(row.difficultyDelta);
    }

    String ladderHealthBody(StudyStatsStore.LadderHealthMetric metric) {
        if (metric.totalActiveItems == 0) {
            return "No active ladder items yet. Sync AnkiDroid or study imported weak kanji to fill the ladder.";
        }
        String body = countText(metric.promotionReadyCount, "FSRS-mature review item", "FSRS-mature review items")
                + " · "
                + countText(metric.demotionRiskCount, "demotion-risk review item", "demotion-risk review items");
        if (metric.demotionReadyCount > 0) {
            body += " · " + countText(metric.demotionReadyCount, "at the demotion threshold", "at the demotion threshold");
        }
        return body
                + ". Thresholds: climb when FSRS schedules more than "
                + metric.ladderPromotionIntervalDays
                + " days; demote after "
                + metric.ladderDemotionFailStreak
                + " real due-review fails.";
    }

    List<String> ladderDistributionRows(StudyStatsStore.LadderHealthMetric metric) {
        List<String> rows = new ArrayList<>();
        for (RecordsBase.LadderRung rung : RecordsBase.LadderRung.values()) {
            rows.add(ladderRungLabel(rung) + ": " + metric.countFor(rung));
        }
        return rows;
    }

    String ladderRungLabel(RecordsBase.LadderRung rung) {
        return switch (rung) {
            case WRITE_KANJI -> "Write kanji";
            case TYPE_MEANING -> "Type meaning";
            case SIMILAR_KANJI -> LABEL_SIMILAR_KANJI;
            case MEANING_KANJI -> "Meaning kanji";
            case KANJI_MEANING -> "Kanji meaning";
            case FONT_MEANING -> "Font meaning";
            case WORD_READING -> "Word reading";
        };
    }

    String weaknessImprovementBody(StudyStatsStore.WeakKanjiImprovedMetric metric) {
        if (metric.improvedCount == 0) {
            return "Weakness improvements will show after Kani reviews are followed by a successful AnkiDroid sync.";
        }
        return "Average weakness: "
                + formatWeakness(metric.averageBeforeWeakness)
                + " -> "
                + formatWeakness(metric.averageAfterWeakness)
                + " after Kani practice.";
    }

    List<String> weaknessImprovementExamples(StudyStatsStore.WeakKanjiImprovedMetric metric) {
        List<String> examples = new ArrayList<>();
        int maxExamples = Math.min(3, metric.examples.size());
        for (int i = 0; i < maxExamples; i++) {
            StudyStatsStore.KanjiImprovement example = metric.examples.get(i);
            examples.add(example.kanji + "  " + formatWeakness(example.beforeWeakness) + " -> " + formatWeakness(example.afterWeakness));
        }
        return examples;
    }

    List<String> supportGainExamples(StudyStatsStore.MatureSupportGainedMetric metric) {
        List<String> examples = new ArrayList<>();
        for (StudyStatsStore.KanjiSupportGain example : metric.examples) {
            examples.add(example.kanji + "  " + example.beforeMatureSupport + " -> " + example.afterMatureSupport + " mature cards");
        }
        return examples;
    }

    String formatWeakness(double weakness) {
        return String.format(java.util.Locale.ROOT, "%.2f", weakness);
    }

    String formatSignedPercent(double value) {
        return String.format(java.util.Locale.ROOT, "%+.0f%%", value * 100.0);
    }

    String formatSignedDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%+.1f", value);
    }

    String formatStudyTime(long millis) {
        long seconds = Math.max(0L, Math.round(millis / 1000.0));
        if (seconds < 60L) {
            return seconds + " sec";
        }
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (minutes < 60L) {
            return remainingSeconds == 0L ? minutes + " min" : minutes + " min " + remainingSeconds + " sec";
        }
        long hours = minutes / 60L;
        long remainingMinutes = minutes % 60L;
        return remainingMinutes == 0L ? hours + " hr" : hours + " hr " + remainingMinutes + " min";
    }

    LinearLayout statPanel(String title, String value, String body, int stroke) {
        LinearLayout box = panelBox(Color.WHITE, stroke);
        box.addView(text(title, 18, MUTED, true));
        box.addView(text(value, 25, INK, true));
        box.addView(text(body, 15, MUTED, false));
        return box;
    }
}
