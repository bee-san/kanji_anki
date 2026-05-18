package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StatsTextCopy {
    private StatsTextCopy() {
    }

    public static boolean verdictWorking(int weakKanjiImproved, int matureSupportGained) {
        return weakKanjiImproved > 0 || matureSupportGained > 0;
    }

    public static boolean verdictHasLadder(int totalActiveItems) {
        return totalActiveItems > 0;
    }

    public static String verdictTitle(boolean working) {
        return working ? "Kani is working for you" : "Kani is not currently working for you";
    }

    public static String verdictBody(
            boolean hasStats,
            boolean working,
            boolean hasLadder,
            int weakKanjiImproved,
            int matureSupportGained,
            int promotionReadyCount,
            int demotionRiskCount,
            int totalActiveItems
    ) {
        if (!hasStats) {
            return "No Kani evidence is available yet. Study weak kanji, then sync AnkiDroid so this page can compare before and after.";
        }
        if (working) {
            return workingVerdictBody(weakKanjiImproved, matureSupportGained, promotionReadyCount, demotionRiskCount);
        }
        if (hasLadder) {
            return "Kani is tracking "
                    + StudyTextCopy.countText(totalActiveItems, "active kanji", "active kanji")
                    + ", but no weakness burn-down or mature Anki support conversion has landed yet. Study due reviews, then sync AnkiDroid.";
        }
        return "No before-and-after evidence yet. Do Kani reviews, then sync AnkiDroid so this page can compare weak kanji and mature support.";
    }

    public static String ladderHealthBody(
            int totalActiveItems,
            int promotionReadyCount,
            int demotionRiskCount,
            int demotionReadyCount,
            int ladderPromotionIntervalDays,
            int ladderDemotionFailStreak
    ) {
        if (totalActiveItems == 0) {
            return "No active ladder items yet. Sync AnkiDroid or study imported weak kanji to fill the ladder.";
        }
        String body = StudyTextCopy.countText(promotionReadyCount, "FSRS-mature review item", "FSRS-mature review items")
                + " · "
                + StudyTextCopy.countText(demotionRiskCount, "demotion-risk review item", "demotion-risk review items");
        if (demotionReadyCount > 0) {
            body += " · " + StudyTextCopy.countText(demotionReadyCount, "at the demotion threshold", "at the demotion threshold");
        }
        return body
                + ". Thresholds: climb when FSRS schedules more than "
                + ladderPromotionIntervalDays
                + " days; demote after "
                + ladderDemotionFailStreak
                + " real due-review fails.";
    }

    public static String ladderDistributionRow(RecordsBase.LadderRung rung, int count) {
        return ladderRungLabel(rung) + ": " + count;
    }

    public static String ladderRungLabel(RecordsBase.LadderRung rung) {
        return switch (rung) {
            case WRITE_KANJI -> "Write kanji";
            case TYPE_MEANING -> "Type meaning";
            case SIMILAR_KANJI -> "Similar kanji";
            case MEANING_KANJI -> "Meaning kanji";
            case KANJI_MEANING -> "Kanji meaning";
            case FONT_MEANING -> "Font meaning";
            case WORD_READING -> "Word reading";
        };
    }

    public static String weaknessImprovementBody(int improvedCount, double averageBeforeWeakness, double averageAfterWeakness) {
        if (improvedCount == 0) {
            return "Weakness improvements will show after Kani reviews are followed by a successful AnkiDroid sync.";
        }
        return "Average weakness: "
                + formatWeakness(averageBeforeWeakness)
                + " -> "
                + formatWeakness(averageAfterWeakness)
                + " after Kani practice.";
    }

    public static String weaknessImprovementExample(String kanji, double beforeWeakness, double afterWeakness) {
        return clean(kanji) + "  " + formatWeakness(beforeWeakness) + " -> " + formatWeakness(afterWeakness);
    }

    public static String supportGainExample(String kanji, int beforeMatureSupport, int afterMatureSupport) {
        return clean(kanji) + "  " + beforeMatureSupport + " -> " + afterMatureSupport + " mature cards";
    }

    public static String notHelpingRowText(String kanji, int reviewCount, int sameCardCount, double retentionDelta, double difficultyDelta) {
        return clean(kanji)
                + "  "
                + reviewCount
                + " Kani reviews · "
                + sameCardCount
                + " same-card checks · retention "
                + formatSignedPercent(retentionDelta)
                + " · difficulty "
                + formatSignedDecimal(difficultyDelta);
    }

    public static String notHelpingBody(boolean noImpactEvidence, boolean hasNotHelpingRows) {
        if (noImpactEvidence) {
            return "No Kani impact evidence yet. Review in Kani, then sync AnkiDroid so this page can compare before and after.";
        }
        if (!hasNotHelpingRows) {
            return "No sufficiently proven not-helping kanji right now. Sparse cases stay out of this list until Kani has enough reviews and synced Anki evidence.";
        }
        return "Only kanji with at least 3 Kani reviews, 2 current Anki cards, and same-card before/after evidence appear here.";
    }

    public static String formatWeakness(double weakness) {
        return String.format(Locale.ROOT, "%.2f", weakness);
    }

    public static String formatSignedPercent(double value) {
        return String.format(Locale.ROOT, "%+.0f%%", value * 100.0);
    }

    public static String formatSignedDecimal(double value) {
        return String.format(Locale.ROOT, "%+.1f", value);
    }

    public static String formatStudyTime(long millis) {
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

    private static String workingVerdictBody(
            int weakKanjiImproved,
            int matureSupportGained,
            int promotionReadyCount,
            int demotionRiskCount
    ) {
        List<String> signals = new ArrayList<>();
        if (weakKanjiImproved > 0) {
            signals.add(StudyTextCopy.countText(weakKanjiImproved, "weak kanji is burning down", "weak kanji are burning down"));
        }
        if (matureSupportGained > 0) {
            signals.add(StudyTextCopy.countText(matureSupportGained, "mature Anki card has been gained", "mature Anki cards have been gained"));
        }
        if (promotionReadyCount > 0) {
            signals.add(StudyTextCopy.countText(promotionReadyCount, "review-phase item crossed the FSRS climb threshold", "review-phase items crossed the FSRS climb threshold"));
        }
        String body = String.join(". ", signals) + ".";
        if (demotionRiskCount > 0) {
            body += " Watch " + StudyTextCopy.countText(demotionRiskCount, "review-phase item with a miss streak", "review-phase items with miss streaks") + ".";
        }
        return body;
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }
}
