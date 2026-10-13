package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels

/** Durable description of the answered card that must remain visible until Continue. */
data class StudyPendingAnswerSnapshot(
    val feedback: StudyAnswerFeedbackSnapshot,
    val kanji: String,
    val taskType: String,
    val writingRequired: Boolean,
    val prompt: String,
    /** Null only for a legacy v1 snapshot written before exact family binding. */
    val answerSignature: String? = null,
    /** Pre-review revision; the committed canonical item must be exactly one revision newer. */
    val schedulerRevision: Long? = null,
    /** Exact legacy writing-repair row, retained so a crash can prove that submission committed. */
    val repairId: Long? = null,
    /** Attempt count before the repair submission represented by this snapshot. */
    val repairAttempts: Int? = null,
) {
    fun restoreSession(
        item: RecordsStudyModels.StudyItem,
        row: RecordsImportModels.DashboardRow?,
    ): RecordsSchedulerModels.StudySession {
        val restoredItem = item.copyBuilder()
            .activeToken(feedback.sessionToken)
            .build()
        return RecordsSchedulerModels.StudySession(
            restoredItem,
            row,
            feedback.sessionToken,
            taskType,
            writingRequired,
            prompt,
        )
    }
}

enum class StudyPromptSource {
    REASON_TEXT,
    PRIMARY_MEANING,
}

/**
 * Minimal, answer-free UI state for one ungraded canonical flashcard.
 *
 * The database remains authoritative for the card, prompt, examples, mnemonic and correct answer.
 * These identity scalars are only enough to prove that the same persisted card is still current.
 */
data class StudyActiveSessionSnapshot(
    val sessionToken: String,
    val kanji: String,
    val answerSignatureDigest: String,
    val schedulerRevision: Long,
    val routingVersion: Int,
    val taskType: String,
    val promptSource: StudyPromptSource,
    val sourceSyncFinishedAtMillis: Long,
    /** Digest of the canonical similar-kanji candidate set; null for flashcard routes. */
    val similarChoiceSignatureDigest: String? = null,
    val typedDraft: String = "",
    val revealed: Boolean = false,
)

sealed class StoredStudyRecovery {
    abstract val resumeOnOrdinaryLaunch: Boolean
    abstract val raw: String
}

data class StoredActiveStudyRecovery(
    val snapshot: StudyActiveSessionSnapshot,
    val writeEpoch: String,
    override val resumeOnOrdinaryLaunch: Boolean,
    override val raw: String,
) : StoredStudyRecovery()

data class StoredPendingStudyRecovery(
    val snapshot: StudyPendingAnswerSnapshot,
    val fallbackActive: StudyActiveSessionSnapshot?,
    val fallbackWriteEpoch: String?,
    override val resumeOnOrdinaryLaunch: Boolean,
    override val raw: String,
) : StoredStudyRecovery()
