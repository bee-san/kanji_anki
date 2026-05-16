package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Public helpers for ladder movement and rung availability. */
public final class StudyLadderRules {
    public static final long DAY = 86_400_000L;

    static final long MINUTE = 60_000L;
    static final int MIN_RECOGNITION_STAGE = -1;
    static final int MAX_RECOGNITION_STAGE = 2;
    static final String STATE_NEW = "new";
    static final String STATE_LEARNING = "learning";
    static final String STATE_REVIEW = "review";
    static final String STATE_RETIRED = "retired";

    private StudyLadderRules() {
    }

    public static Records.LadderRung promoteRung(Records.LadderRung current, boolean hasSimilarKanji) {
        return promoteRung(current, hasSimilarKanji, Records.StudyLadderSettings.defaults());
    }

    public static Records.LadderRung promoteRung(
            Records.LadderRung current,
            boolean hasSimilarKanji,
            Records.StudyLadderSettings ladder
    ) {
        return safeLadder(ladder).nextRung(current, hasSimilarKanji);
    }

    public static Records.LadderRung demoteRung(Records.LadderRung current, boolean hasSimilarKanji) {
        return demoteRung(current, hasSimilarKanji, Records.StudyLadderSettings.defaults());
    }

    public static Records.LadderRung demoteRung(
            Records.LadderRung current,
            boolean hasSimilarKanji,
            Records.StudyLadderSettings ladder
    ) {
        return safeLadder(ladder).previousRung(current, hasSimilarKanji);
    }

    public static List<Records.LadderRung> rungsForItem(Records.StudyItem item) {
        return rungsForItem(item, Records.StudyLadderSettings.defaults());
    }

    public static List<Records.LadderRung> rungsForItem(
            Records.StudyItem item,
            Records.StudyLadderSettings ladder
    ) {
        List<Records.LadderRung> out = new ArrayList<>();
        Records.StudyLadderSettings safeLadder = safeLadder(ladder);
        for (Records.LadderRung rung : safeLadder.orderedRungs) {
            if (safeLadder.isValidForItem(rung, item.hasSimilarKanji)) {
                out.add(rung);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static Records.StudyLadderSettings safeLadder(Records.StudyLadderSettings ladder) {
        return ladder == null ? Records.StudyLadderSettings.defaults() : ladder;
    }

    static Records.StudyItem alignRungToLadder(Records.StudyItem item, Records.StudyLadderSettings ladder) {
        Records.LadderRung effective = safeLadder(ladder).effectiveRung(item.rung, item.hasSimilarKanji);
        return effective == item.rung ? item : item.withRung(effective);
    }

    static long stepDelayMillis(int minutes) {
        return Math.max(1L, Math.max(1, minutes)) * MINUTE;
    }

    static long clampStudyAheadMillis(long studyAheadMillis) {
        if (studyAheadMillis <= 0L) {
            return 0L;
        }
        return Math.min(studyAheadMillis, DAY);
    }

    static int rungToLegacyStage(Records.LadderRung rung) {
        return switch (rung) {
            case TYPE_MEANING -> MIN_RECOGNITION_STAGE;
            case FONT_MEANING -> 1;
            case WORD_READING -> MAX_RECOGNITION_STAGE;
            default -> 0;
        };
    }
}
