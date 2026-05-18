package dev.bee.kanjianki.core;

public final class TimelineCopy {
    private TimelineCopy() {
    }

    public static String statusText(RecordsStudyModels.KanjiRecoveryTimeline timeline, long nowMillis) {
        RecordsStudyModels.StudyItem item = timeline.currentStudyItem;
        if (item != null && StudyLadderRules.STATE_RETIRED.equals(item.state)) {
            return "Retired by Anki support";
        }
        if (item != null && item.dueAtMillis > nowMillis) {
            return "Resting until review";
        }
        if (timeline.currentRow == null) {
            return "Retired by Anki support";
        }
        return "Active repair";
    }

    public static Tone statusTone(RecordsStudyModels.KanjiRecoveryTimeline timeline, long nowMillis) {
        RecordsStudyModels.StudyItem item = timeline.currentStudyItem;
        if (item != null && StudyLadderRules.STATE_RETIRED.equals(item.state)) {
            return Tone.POSITIVE;
        }
        if (item != null && item.dueAtMillis > nowMillis) {
            return Tone.NEUTRAL;
        }
        return Tone.WARNING;
    }

    public static Tone eventTone(String eventType) {
        if ("review_failed".equals(eventType) || "support_dropped".equals(eventType) || "reopened".equals(eventType)) {
            return Tone.WARNING;
        }
        if ("review_passed".equals(eventType) || "support_improved".equals(eventType) || StudyLadderRules.STATE_RETIRED.equals(eventType)) {
            return Tone.POSITIVE;
        }
        return Tone.NEUTRAL;
    }

    public static String sourceLine(RecordsImportModels.KanjiTimelineEvent event) {
        if (event.sourceExpression.isEmpty()) {
            return "";
        }
        if (event.sourceReading.isEmpty()) {
            return "Source: " + event.sourceExpression;
        }
        return "Source: " + event.sourceExpression + "  " + event.sourceReading;
    }

    public enum Tone {
        POSITIVE,
        NEUTRAL,
        WARNING
    }
}
