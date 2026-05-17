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

    public static RecordsBase.LadderRung promoteRung(RecordsBase.LadderRung current, boolean hasSimilarKanji) {
        return promoteRung(current, hasSimilarKanji, RecordsBase.StudyLadderSettings.defaults());
    }

    public static RecordsBase.LadderRung promoteRung(
            RecordsBase.LadderRung current,
            boolean hasSimilarKanji,
            RecordsBase.StudyLadderSettings ladder
    ) {
        return safeLadder(ladder).nextRung(current, hasSimilarKanji);
    }

    public static RecordsBase.LadderRung demoteRung(RecordsBase.LadderRung current, boolean hasSimilarKanji) {
        return demoteRung(current, hasSimilarKanji, RecordsBase.StudyLadderSettings.defaults());
    }

    public static RecordsBase.LadderRung demoteRung(
            RecordsBase.LadderRung current,
            boolean hasSimilarKanji,
            RecordsBase.StudyLadderSettings ladder
    ) {
        return safeLadder(ladder).previousRung(current, hasSimilarKanji);
    }

    public static List<RecordsBase.LadderRung> rungsForItem(RecordsStudyModels.StudyItem item) {
        return rungsForItem(item, RecordsBase.StudyLadderSettings.defaults());
    }

    public static List<RecordsBase.LadderRung> rungsForItem(
            RecordsStudyModels.StudyItem item,
            RecordsBase.StudyLadderSettings ladder
    ) {
        List<RecordsBase.LadderRung> out = new ArrayList<>();
        RecordsBase.StudyLadderSettings safeLadder = safeLadder(ladder);
        for (RecordsBase.LadderRung rung : safeLadder.orderedRungs) {
            if (safeLadder.isValidForItem(rung, item.hasSimilarKanji)) {
                out.add(rung);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static RecordsBase.StudyLadderSettings safeLadder(RecordsBase.StudyLadderSettings ladder) {
        return ladder == null ? RecordsBase.StudyLadderSettings.defaults() : ladder;
    }

    static RecordsStudyModels.StudyItem alignRungToLadder(RecordsStudyModels.StudyItem item, RecordsBase.StudyLadderSettings ladder) {
        RecordsBase.LadderRung effective = safeLadder(ladder).effectiveRung(item.rung, item.hasSimilarKanji);
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

    static int rungToLegacyStage(RecordsBase.LadderRung rung) {
        return switch (rung) {
            case TYPE_MEANING -> MIN_RECOGNITION_STAGE;
            case FONT_MEANING -> 1;
            case WORD_READING -> MAX_RECOGNITION_STAGE;
            default -> 0;
        };
    }
}
