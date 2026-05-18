package dev.bee.kanjianki.core;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class StudySessionFocusPolicy {
    private StudySessionFocusPolicy() {
    }

    public static Set<String> allowedKanji(RecordsSchedulerModels.AdaptiveLoadPlan plan, boolean continueAllKanjiSession) {
        Objects.requireNonNull(plan, "plan");
        if (continueAllKanjiSession || plan.allKanjiMode) {
            return null;
        }
        return new HashSet<>(plan.focusKanji);
    }
}
