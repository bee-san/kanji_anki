package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.domain.model.study.StudyQueueItem;
import dev.bee.kanjianki.domain.scheduler.NextSessionInput;
import dev.bee.kanjianki.domain.scheduler.StudySessionSelector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class LegacyStudySessionBridge {
    private final StudySessionSelector selector;

    LegacyStudySessionBridge() {
        this(new StudySessionSelector());
    }

    LegacyStudySessionBridge(StudySessionSelector selector) {
        this.selector = Objects.requireNonNull(selector);
    }

    RecordsSchedulerModels.StudySession nextSession(
            List<RecordsStudyModels.StudyItem> items,
            List<RecordsImportModels.DashboardRow> rows,
            long nowMillis,
            long studyAheadMillis,
            Set<String> allowedKanji,
            RecordsSyncModels.Settings settings,
            RecordsBase.StudyLadderSettings ladder
    ) {
        dev.bee.kanjianki.domain.scheduler.StudySession session = selector.nextSession(
                new NextSessionInput(
                        LegacyStudyMappers.toDomainItems(items),
                        LegacyStudyMappers.toDomainRows(rows),
                        nowMillis,
                        studyAheadMillis,
                        allowedKanji,
                        LegacyStudyMappers.toDomain(settings, ladder),
                        LegacyStudyMappers.newCardSortMode(settings)
                )
        );
        if (session == null) {
            return null;
        }
        RecordsStudyModels.StudyItem original = originalItem(items, session.getItem());
        RecordsImportModels.DashboardRow row = originalRowByKanji(rows).get(session.getItem().getKanji());
        return new RecordsSchedulerModels.StudySession(
                LegacyStudyMappers.toLegacy(original, session.getItem()),
                row,
                session.getToken(),
                session.getTaskType(),
                session.getWritingRequired(),
                session.getPrompt()
        );
    }

    private static RecordsStudyModels.StudyItem originalItem(
            List<RecordsStudyModels.StudyItem> items,
            StudyQueueItem selected
    ) {
        for (RecordsStudyModels.StudyItem item : items) {
            if (item.kanji.equals(selected.getKanji())
                    && item.answerSignature.equals(selected.getAnswerSignature())) {
                return item;
            }
        }
        for (RecordsStudyModels.StudyItem item : items) {
            if (item.kanji.equals(selected.getKanji())) {
                return item;
            }
        }
        throw new IllegalStateException("Selected study item missing from legacy input: " + selected.getKanji());
    }

    private static Map<String, RecordsImportModels.DashboardRow> originalRowByKanji(
            List<RecordsImportModels.DashboardRow> rows
    ) {
        Map<String, RecordsImportModels.DashboardRow> out = new HashMap<>();
        for (RecordsImportModels.DashboardRow row : rows) {
            out.put(row.kanji, row);
        }
        return out;
    }
}
