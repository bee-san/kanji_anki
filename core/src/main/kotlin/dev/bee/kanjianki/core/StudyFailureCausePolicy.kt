package dev.bee.kanjianki.core

/**
 * Which core checks ask the learner for a failure cause, and which causes they
 * may pick. Recognition checks offer meaning-vs-shape. Contextual reading checks
 * offer reading-vs-shape: a kanji in the terminal core can still decay in shape,
 * and without a shape cause the scheduler could only ever prescribe reading
 * drills for it.
 *
 * Exactly two choices are offered per task so the same two-button dialog serves
 * both cores. Dismissing the dialog submits nothing.
 */
object StudyFailureCausePolicy {
    /** True when a real-due Fail on this task must first collect a cause. */
    @JvmStatic
    fun requiresCause(taskType: String?, phase: RecordsBase.SchedulerPhase?): Boolean {
        if (phase != RecordsBase.SchedulerPhase.REVIEW) return false
        return choices(taskType).isNotEmpty()
    }

    /** Ordered causes to offer for a core task; empty for repair tasks. */
    @JvmStatic
    fun choices(taskType: String?): List<FailureKind> = when (taskType) {
        StudyTaskTypes.KANJI_MEANING,
        StudyTaskTypes.FONT_MEANING,
        -> listOf(FailureKind.MEANING_UNKNOWN, FailureKind.VISUAL_CONFUSION)

        StudyTaskTypes.WORD_READING,
        StudyTaskTypes.SENTENCE_READING,
        -> listOf(FailureKind.WRONG_READING, FailureKind.VISUAL_CONFUSION)

        else -> emptyList()
    }

    @JvmStatic
    fun label(kind: FailureKind): String = when (kind) {
        FailureKind.MEANING_UNKNOWN -> StudyTextCopy.recognitionFailureMeaningChoice()
        FailureKind.VISUAL_CONFUSION -> StudyTextCopy.recognitionFailureVisualChoice()
        FailureKind.WRONG_READING -> StudyTextCopy.contextualFailureReadingChoice()
        FailureKind.HOMOPHONE_CONFUSION -> StudyTextCopy.contextualFailureReadingChoice()
        FailureKind.WRITING_SHAPE -> StudyTextCopy.recognitionFailureVisualChoice()
        FailureKind.UNKNOWN -> StudyTextCopy.recognitionFailureTitle()
    }
}
