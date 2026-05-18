package dev.bee.kanjianki.core;

public final class SimilarKanjiRepairPolicy {
    public static final String STATUS_COMPLETE = "complete";

    private SimilarKanjiRepairPolicy() {
    }

    public static FinishUpdate finishUpdate(
            RecordsImportModels.SimilarKanjiWritingRepair current,
            boolean passed,
            long nowMillis
    ) {
        if (passed) {
            return new FinishUpdate("", nowMillis, STATUS_COMPLETE, nowMillis, null, null);
        }
        int attempts = current == null ? 1 : current.attempts + 1;
        return new FinishUpdate("", nowMillis, null, null, attempts, nowMillis);
    }

    public record FinishUpdate(
            String activeToken,
            long updatedAtMillis,
            String status,
            Long completedAtMillis,
            Integer attempts,
            Long dueAtMillis
    ) {
    }
}
