package dev.bee.kanjianki.core;

public final class TimelineCopy {
    public static final String EVENT_MANUAL_OVERRIDE = "manual_override";
    public static final String EVENT_REVIEW_FAILED = "review_failed";
    public static final String EVENT_REVIEW_PASSED = "review_passed";
    public static final String EVENT_REOPENED = "reopened";
    private static final String RATING_AGAIN = "again";

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
        if (EVENT_REVIEW_FAILED.equals(eventType) || "support_dropped".equals(eventType) || EVENT_REOPENED.equals(eventType)) {
            return Tone.WARNING;
        }
        if (EVENT_REVIEW_PASSED.equals(eventType) || "support_improved".equals(eventType) || StudyLadderRules.STATE_RETIRED.equals(eventType)) {
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

    public static String studyStateDetail(boolean retired, Integer matureSupportCount, int target) {
        if (retired) {
            return matureSupportCount == null
                    ? "No weak Anki evidence remained after sync, so Kani retired this repair."
                    : supportDetail("Mature Anki support met the target", matureSupportCount, target);
        }
        return matureSupportCount == null
                ? "Kani reopened this kanji after sync found weak evidence again."
                : supportDetail("Mature Anki support fell below target", matureSupportCount, target);
    }

    public static ReviewEvent reviewEvent(RecordsSchedulerModels.ReviewRequest request, String appliedRating) {
        String eventType;
        String title;
        if (request.manualOverride) {
            eventType = EVENT_MANUAL_OVERRIDE;
            title = "Manual override";
        } else if (RATING_AGAIN.equals(appliedRating) || (request.writingRequired && !request.writingPassed)) {
            eventType = EVENT_REVIEW_FAILED;
            title = "Review failed";
        } else {
            eventType = EVENT_REVIEW_PASSED;
            title = "Review passed";
        }
        return new ReviewEvent(eventType, title, reviewDetail(request, appliedRating));
    }

    public static String reviewDetail(RecordsSchedulerModels.ReviewRequest request, String appliedRating) {
        if (request.manualOverride) {
            return "Saved as " + appliedRating + " after manual confirmation.";
        }
        if (RATING_AGAIN.equals(appliedRating)) {
            return request.writingRequired
                    ? "Writing missed; Kani scheduled another try."
                    : "Recall missed; Kani scheduled another try.";
        }
        if (request.writingRequired) {
            return request.writingPassed
                    ? "Writing passed and was rated " + appliedRating + "."
                    : "Writing was not passed and was rated " + appliedRating + ".";
        }
        return "Recall review was rated " + appliedRating + ".";
    }

    public static String supportDetail(String prefix, int matureSupportCount, int target) {
        return prefix + ": mature support " + matureSupportCount + " / target " + target + ".";
    }

    public enum Tone {
        POSITIVE,
        NEUTRAL,
        WARNING
    }

    public record ReviewEvent(String eventType, String title, String detail) {
    }
}
