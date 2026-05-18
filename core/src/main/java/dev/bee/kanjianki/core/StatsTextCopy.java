package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.List;

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
}
