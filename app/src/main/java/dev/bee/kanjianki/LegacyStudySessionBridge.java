package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.domain.scheduler.StudySessionSelector;

import java.util.List;
import java.util.Objects;
import java.util.Set;

final class LegacyStudySessionBridge {
    private final LegacyLoadNextStudySessionBridge bridge;

    LegacyStudySessionBridge() {
        this(new LegacyLoadNextStudySessionBridge());
    }

    LegacyStudySessionBridge(StudySessionSelector selector) {
        this(new LegacyLoadNextStudySessionBridge(selector));
    }

    private LegacyStudySessionBridge(LegacyLoadNextStudySessionBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge);
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
        return bridge.nextSession(
                items,
                rows,
                nowMillis,
                studyAheadMillis,
                allowedKanji,
                settings,
                ladder
        );
    }
}
