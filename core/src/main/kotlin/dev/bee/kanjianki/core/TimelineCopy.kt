package dev.bee.kanjianki.core

object TimelineCopy {
    const val EVENT_MANUAL_OVERRIDE = "manual_override"
    const val EVENT_REVIEW_FAILED = "review_failed"
    const val EVENT_REVIEW_PASSED = "review_passed"
    const val EVENT_REOPENED = "reopened"

    private const val RATING_AGAIN = "again"

    @JvmStatic
    fun statusText(timeline: RecordsStudyModels.KanjiRecoveryTimeline, nowMillis: Long): String {
        val item = timeline.currentStudyItem
        if (item != null && StudyLadderRules.STATE_RETIRED == item.state) {
            return "Retired by Anki support"
        }
        if (item != null && item.dueAtMillis > nowMillis) {
            return "Resting until review"
        }
        if (timeline.currentRow == null) {
            return "Retired by Anki support"
        }
        return "Active repair"
    }

    @JvmStatic
    fun statusTone(timeline: RecordsStudyModels.KanjiRecoveryTimeline, nowMillis: Long): Tone {
        val item = timeline.currentStudyItem
        if (item != null && StudyLadderRules.STATE_RETIRED == item.state) {
            return Tone.POSITIVE
        }
        if (item != null && item.dueAtMillis > nowMillis) {
            return Tone.NEUTRAL
        }
        return Tone.WARNING
    }

    @JvmStatic
    fun eventTone(eventType: String): Tone {
        if (EVENT_REVIEW_FAILED == eventType || "support_dropped" == eventType || EVENT_REOPENED == eventType) {
            return Tone.WARNING
        }
        if (EVENT_REVIEW_PASSED == eventType || "support_improved" == eventType || StudyLadderRules.STATE_RETIRED == eventType) {
            return Tone.POSITIVE
        }
        return Tone.NEUTRAL
    }

    @JvmStatic
    fun sourceLine(event: RecordsImportModels.KanjiTimelineEvent): String {
        if (event.sourceExpression.isEmpty()) {
            return ""
        }
        if (event.sourceReading.isEmpty()) {
            return "Source: " + event.sourceExpression
        }
        return "Source: " + event.sourceExpression + "  " + event.sourceReading
    }

    @JvmStatic
    fun studyStateDetail(retired: Boolean, matureSupportCount: Int?, target: Int): String {
        if (retired) {
            return if (matureSupportCount == null) {
                "No weak Anki evidence remained after sync, so Kani retired this repair."
            } else {
                supportDetail("Mature Anki support met the target", matureSupportCount, target)
            }
        }
        return if (matureSupportCount == null) {
            "Kani reopened this kanji after sync found weak evidence again."
        } else {
            supportDetail("Mature Anki support fell below target", matureSupportCount, target)
        }
    }

    @JvmStatic
    fun reviewEvent(request: RecordsSchedulerModels.ReviewRequest, appliedRating: String?): ReviewEvent {
        val safeRating = appliedRating ?: RATING_AGAIN
        val eventType: String
        val title: String
        if (request.manualOverride) {
            eventType = EVENT_MANUAL_OVERRIDE
            title = "Manual override"
        } else if (RATING_AGAIN == safeRating || (request.writingRequired && !request.writingPassed)) {
            eventType = EVENT_REVIEW_FAILED
            title = "Review failed"
        } else {
            eventType = EVENT_REVIEW_PASSED
            title = "Review passed"
        }
        return ReviewEvent(eventType, title, reviewDetail(request, safeRating))
    }

    @JvmStatic
    fun reviewDetail(request: RecordsSchedulerModels.ReviewRequest, appliedRating: String?): String {
        val safeRating = appliedRating ?: RATING_AGAIN
        if (request.manualOverride) {
            return "Saved as " + safeRating + " after manual confirmation."
        }
        if (RATING_AGAIN == safeRating) {
            return if (request.writingRequired) {
                "Writing missed; Kani scheduled another try."
            } else {
                "Recall missed; Kani scheduled another try."
            }
        }
        if (request.writingRequired) {
            return if (request.writingPassed) {
                "Writing passed and was rated " + safeRating + "."
            } else {
                "Writing was not passed and was rated " + safeRating + "."
            }
        }
        return "Recall review was rated " + safeRating + "."
    }

    @JvmStatic
    fun supportDetail(prefix: String, matureSupportCount: Int, target: Int): String {
        return prefix + ": mature support " + matureSupportCount + " / target " + target + "."
    }

    enum class Tone {
        POSITIVE,
        NEUTRAL,
        WARNING
    }

    class ReviewEvent(
        private val eventTypeValue: String,
        private val titleValue: String,
        private val detailValue: String,
    ) {
        fun eventType(): String = eventTypeValue

        fun title(): String = titleValue

        fun detail(): String = detailValue
    }
}
