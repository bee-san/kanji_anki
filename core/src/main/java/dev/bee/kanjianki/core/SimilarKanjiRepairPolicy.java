package dev.bee.kanjianki.core;

public final class SimilarKanjiRepairPolicy {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_COMPLETE = "complete";

    private SimilarKanjiRepairPolicy() {
    }

    public static RepairDraft newRepair(
            RecordsImportModels.SimilarKanjiChoiceCard card,
            String repairKanji,
            String wrongSelection,
            long nowMillis
    ) {
        if (card == null) {
            return null;
        }
        String normalized = TextUtil.normalizeSingleKanji(repairKanji);
        if (normalized.isEmpty()) {
            return null;
        }
        return new RepairDraft(
                card.targetKanji,
                normalized,
                card.choiceSignature,
                wrongSelection == null ? "" : wrongSelection,
                card.primaryMeaning,
                STATUS_PENDING,
                nowMillis,
                "",
                0,
                nowMillis,
                nowMillis,
                0L
        );
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

    public record RepairDraft(
            String targetKanji,
            String repairKanji,
            String choiceSignature,
            String wrongSelection,
            String promptMeaning,
            String status,
            long dueAtMillis,
            String activeToken,
            int attempts,
            long createdAtMillis,
            long updatedAtMillis,
            long completedAtMillis
    ) {
    }
}
