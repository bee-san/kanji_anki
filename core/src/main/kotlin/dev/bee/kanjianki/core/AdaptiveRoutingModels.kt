package dev.bee.kanjianki.core

/** The two long-term memories owned by adaptive study routing. */
enum class CoreSkill(private val wireNameValue: String) {
    RECOGNITION("recognition"),
    CONTEXTUAL_READING("contextual_reading");

    fun wireName(): String = wireNameValue

    companion object {
        @JvmStatic
        fun fromWireName(wireName: String?): CoreSkill? = entries.firstOrNull { it.wireNameValue == wireName }
    }
}

/** The evidence-backed reason a core validation failed. */
enum class FailureKind(private val wireNameValue: String) {
    MEANING_UNKNOWN("meaning_unknown"),
    VISUAL_CONFUSION("visual_confusion"),
    WRONG_READING("wrong_reading"),
    HOMOPHONE_CONFUSION("homophone_confusion"),
    WRITING_SHAPE("writing_shape"),
    UNKNOWN("unknown");

    fun wireName(): String = wireNameValue

    companion object {
        @JvmStatic
        fun fromWireName(wireName: String?): FailureKind? = entries.firstOrNull { it.wireNameValue == wireName }
    }
}

/** How Kani learned the failure cause recorded with a review. */
enum class EvidenceSource(private val wireNameValue: String) {
    SELF_REPORT("self_report"),
    OBJECTIVE_CHOICE("objective_choice"),
    WRITING_EVALUATOR("writing_evaluator"),
    INFERRED("inferred");

    fun wireName(): String = wireNameValue

    companion object {
        @JvmStatic
        fun fromWireName(wireName: String?): EvidenceSource? {
            // Pre-v31 prototypes used the less precise "objective" value.
            if (wireName == "objective") {
                return OBJECTIVE_CHOICE
            }
            return entries.firstOrNull { it.wireNameValue == wireName }
        }
    }
}

/** A deterministic rendering variant that shares its core skill's memory. */
enum class PresentationVariant(private val wireNameValue: String) {
    STANDARD_GLYPH("standard_glyph"),
    FONT_GLYPH("font_glyph"),
    PLAIN_WORD("plain_word"),
    SENTENCE_CONTEXT("sentence_context");

    fun wireName(): String = wireNameValue

    companion object {
        @JvmStatic
        fun fromWireName(wireName: String?): PresentationVariant? =
            entries.firstOrNull { it.wireNameValue == wireName }
    }
}

/**
 * Objective and inferred details captured at the instant an answer is rated.
 *
 * The scalar values that are useful to SQL (`core_skill`, `failure_cause`,
 * `evidence_source`, `selected_answer`, and `correct_answer`) are deliberately
 * present here too. This makes the JSON payload a self-contained audit record
 * while the duplicated columns remain cheap to query.
 */
data class AnswerEvidence(
    @JvmField val coreSkill: CoreSkill? = null,
    @JvmField val failureKind: FailureKind? = null,
    @JvmField val evidenceSource: EvidenceSource? = null,
    @JvmField val presentationVariant: PresentationVariant? = null,
    @JvmField val selectedAnswer: String = "",
    @JvmField val correctAnswer: String = "",
    @JvmField val renderedExpression: String = "",
    @JvmField val renderedReading: String = "",
    @JvmField val confusedWith: String = "",
)

/**
 * Persisted state for routing version 2.
 *
 * A card still owns one study item. [activeRepairTasks] and [repairTaskIndex]
 * describe the current presentation of that item; they are not a side queue.
 * The configured relearning delays are snapshotted in [repairStepMinutes] so a
 * settings change cannot mutate a repair already in progress.
 */
data class AdaptiveRouteState(
    @JvmField val activeCore: CoreSkill = CoreSkill.RECOGNITION,
    @JvmField val recognitionReviewCount: Int = 0,
    @JvmField val contextualReadingReviewCount: Int = 0,
    @JvmField val activeRepairTasks: List<String> = emptyList(),
    @JvmField val repairTaskIndex: Int = 0,
    @JvmField val repairStepMinutes: List<Int> = emptyList(),
    @JvmField val repairDueAtMillis: Long = 0L,
    @JvmField val coreDueAtMillis: Long = 0L,
    @JvmField val recurringFailure: FailureKind? = null,
    @JvmField val recurringFailureCount: Int = 0,
    @JvmField val repairAttemptCount: Int = 0,
    @JvmField val repairStartedAtMillis: Long = 0L,
    @JvmField val revalidationPending: Boolean = false,
    @JvmField val answerEvidence: AnswerEvidence? = null,
) {
    fun reviewCount(skill: CoreSkill): Int = when (skill) {
        CoreSkill.RECOGNITION -> recognitionReviewCount
        CoreSkill.CONTEXTUAL_READING -> contextualReadingReviewCount
    }

    fun activeRepairTask(): String? = activeRepairTasks.getOrNull(repairTaskIndex)

    fun isRepairActive(): Boolean = activeRepairTask() != null
}

/** Stable mapping from the legacy task wires to the memory they now train. */
object AdaptiveCorePolicy {
    @JvmStatic
    fun coreForTaskType(taskType: String?): CoreSkill? = when (taskType) {
        StudyTaskTypes.WRITE_KANJI,
        StudyTaskTypes.WRITING_REMEDIATION,
        StudyTaskTypes.TYPE_MEANING,
        StudyTaskTypes.TYPING_MEANING,
        StudyTaskTypes.SIMILAR_KANJI,
        StudyTaskTypes.MEANING_KANJI,
        StudyTaskTypes.KANJI_MEANING,
        StudyTaskTypes.FONT_MEANING -> CoreSkill.RECOGNITION

        StudyTaskTypes.WORD_READING,
        StudyTaskTypes.KANJI_READING,
        StudyTaskTypes.READING_KANJI,
        StudyTaskTypes.SENTENCE_READING,
        StudyTaskTypes.TYPE_READING -> CoreSkill.CONTEXTUAL_READING

        else -> null
    }

    @JvmStatic
    fun coreForRung(rung: RecordsBase.LadderRung?): CoreSkill? = when (rung) {
        RecordsBase.LadderRung.WRITE_KANJI,
        RecordsBase.LadderRung.TYPE_MEANING,
        RecordsBase.LadderRung.SIMILAR_KANJI,
        RecordsBase.LadderRung.MEANING_KANJI,
        RecordsBase.LadderRung.KANJI_MEANING,
        RecordsBase.LadderRung.FONT_MEANING -> CoreSkill.RECOGNITION

        RecordsBase.LadderRung.KANJI_READING,
        RecordsBase.LadderRung.READING_KANJI,
        RecordsBase.LadderRung.WORD_READING,
        RecordsBase.LadderRung.SENTENCE_READING -> CoreSkill.CONTEXTUAL_READING

        null -> null
    }

    @JvmStatic
    fun memoryOwnerTaskType(skill: CoreSkill): String = when (skill) {
        CoreSkill.RECOGNITION -> StudyTaskTypes.KANJI_MEANING
        CoreSkill.CONTEXTUAL_READING -> StudyTaskTypes.WORD_READING
    }

    @JvmStatic
    fun memoryOwnerRung(skill: CoreSkill): RecordsBase.LadderRung = when (skill) {
        CoreSkill.RECOGNITION -> RecordsBase.LadderRung.KANJI_MEANING
        CoreSkill.CONTEXTUAL_READING -> RecordsBase.LadderRung.WORD_READING
    }
}

/** Selects a core presentation without randomness or a separate memory slot. */
object AdaptivePresentationPolicy {
    @JvmStatic
    fun variant(
        skill: CoreSkill,
        completedCoreReviews: Int,
        alternateEnabled: Boolean,
        alternateAvailable: Boolean,
    ): PresentationVariant {
        val useAlternate = completedCoreReviews.coerceAtLeast(0) % 2 == 1 &&
            alternateEnabled && alternateAvailable
        return when (skill) {
            CoreSkill.RECOGNITION -> if (useAlternate) {
                PresentationVariant.FONT_GLYPH
            } else {
                PresentationVariant.STANDARD_GLYPH
            }

            CoreSkill.CONTEXTUAL_READING -> if (useAlternate) {
                PresentationVariant.SENTENCE_CONTEXT
            } else {
                PresentationVariant.PLAIN_WORD
            }
        }
    }

    @JvmStatic
    fun taskType(variant: PresentationVariant): String = when (variant) {
        PresentationVariant.STANDARD_GLYPH -> StudyTaskTypes.KANJI_MEANING
        PresentationVariant.FONT_GLYPH -> StudyTaskTypes.FONT_MEANING
        PresentationVariant.PLAIN_WORD -> StudyTaskTypes.WORD_READING
        PresentationVariant.SENTENCE_CONTEXT -> StudyTaskTypes.SENTENCE_READING
    }
}
