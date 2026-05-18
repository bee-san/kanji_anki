package dev.bee.kanjianki.core;

import java.util.Collections;
import java.util.List;

final class TargetedStudySessionPolicy {
    RecordsSchedulerModels.StudySession targetedSession(
            List<RecordsStudyModels.StudyItem> seededItems,
            RecordsImportModels.DashboardRow row,
            long nowMillis,
            RecordsBase.StudyLadderSettings ladder
    ) {
        if (row == null) {
            return null;
        }
        RecordsStudyModels.StudyItem item = targetedStudyItem(seededItems, row.kanji, nowMillis, ladder);
        String token = StudyTokenPolicy.studyItem(item.kanji, item.activeToken);
        RecordsStudyModels.StudyItem effectiveItem = StudyLadderRules.alignRungToLadder(item, ladder).withToken(token);
        return new RecordsSchedulerModels.StudySession(
                effectiveItem,
                row,
                token,
                StudyTaskTypes.forRung(effectiveItem.rung),
                effectiveItem.rung == RecordsBase.LadderRung.WRITE_KANJI,
                promptFor(row)
        );
    }

    RecordsStudyModels.StudyItem targetedStudyItem(
            List<RecordsStudyModels.StudyItem> seededItems,
            String kanji,
            long nowMillis,
            RecordsBase.StudyLadderSettings ladder
    ) {
        RecordsStudyModels.StudyItem item = findStudyItem(seededItems, kanji);
        return item == null ? newTargetedStudyItem(kanji, nowMillis, ladder) : item;
    }

    RecordsStudyModels.StudyItem newTargetedStudyItem(
            String kanji,
            long nowMillis,
            RecordsBase.StudyLadderSettings ladder
    ) {
        RecordsBase.StudyLadderSettings safeLadder = StudyLadderRules.safeLadder(ladder);
        return new RecordsStudyModels.StudyItem(
                kanji,
                StudyLadderRules.STATE_NEW,
                nowMillis,
                0.4,
                5.0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                false,
                null,
                nowMillis
        ).withRung(safeLadder.startingRung(false));
    }

    private RecordsStudyModels.StudyItem findStudyItem(List<RecordsStudyModels.StudyItem> items, String kanji) {
        for (RecordsStudyModels.StudyItem item : safeItems(items)) {
            if (item.kanji.equals(kanji)) {
                return item;
            }
        }
        return null;
    }

    private List<RecordsStudyModels.StudyItem> safeItems(List<RecordsStudyModels.StudyItem> items) {
        return items == null ? Collections.emptyList() : items;
    }

    private String promptFor(RecordsImportModels.DashboardRow row) {
        return row.primaryMeaning.isEmpty() ? row.reasonText : row.primaryMeaning;
    }
}
