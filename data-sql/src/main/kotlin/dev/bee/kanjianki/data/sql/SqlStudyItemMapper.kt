package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsStudyModels

internal object SqlStudyItemMapper {
    /** Column list for `study_items`, in the canonical schema-v34 order. */
    val COLUMNS: List<String> = listOf(
        "kanji", "state", "due_at", "stability", "difficulty", "total_reviews", "lapses",
        "learning_step", "writing_level", "recognition_stage",
        "consecutive_failed_recognition_days", "last_failed_recognition_day",
        "writing_remediation_pending", "suppressed_by_task_type", "suppressed_at",
        "mature_interval_days", "answer_signature", "typing_meaning_memory",
        "meaning_kanji_memory", "kanji_meaning_memory", "font_meaning_memory",
        "word_reading_memory", "writing_remediation_memory", "rung", "phase",
        "real_pass_streak", "real_again_streak", "last_real_review_due_at",
        "similar_kanji_memory", "kanji_reading_memory", "reading_kanji_memory",
        "sentence_reading_memory", "scheduler_revision", "routing_version",
        "adaptive_route_state_json", "active_token", "created_at",
    )

    /**
     * Binds [item] into an upsert statement whose parameters are the [COLUMNS]
     * in order (one-based). Mirrors the legacy `studyItemValues` exactly.
     */
    fun bindUpsert(statement: SqlStatement, item: RecordsStudyModels.StudyItem) {
        statement.bindText(1, item.kanji)
        statement.bindText(2, item.state)
        statement.bindLong(3, item.dueAtMillis)
        statement.bindDouble(4, item.stability)
        statement.bindDouble(5, item.difficulty)
        statement.bindLong(6, item.totalReviews.toLong())
        statement.bindLong(7, item.lapses.toLong())
        statement.bindLong(8, item.learningStep.toLong())
        statement.bindLong(9, item.writingLevel.toLong())
        statement.bindLong(10, item.recognitionStage.toLong())
        statement.bindLong(11, item.consecutiveFailedRecognitionDays.toLong())
        statement.bindLong(12, item.lastFailedRecognitionDayMillis)
        statement.bindLong(13, if (item.writingRemediationPending) 1L else 0L)
        statement.bindText(14, item.suppressedByTaskType)
        statement.bindLong(15, item.suppressedAtMillis)
        statement.bindLong(16, item.matureIntervalDays.toLong())
        statement.bindText(17, item.answerSignature)
        statement.bindText(18, item.typingMeaningMemory.encode())
        statement.bindText(19, item.meaningKanjiMemory.encode())
        statement.bindText(20, item.kanjiMeaningMemory.encode())
        statement.bindText(21, item.fontMeaningMemory.encode())
        statement.bindText(22, item.wordReadingMemory.encode())
        statement.bindText(23, item.writingRemediationMemory.encode())
        statement.bindText(24, item.rung.wireName())
        statement.bindText(25, item.phase.wireName())
        statement.bindLong(26, item.realPassStreak.toLong())
        statement.bindLong(27, item.realAgainStreak.toLong())
        statement.bindLong(28, item.lastRealReviewDueAtMillis)
        statement.bindText(29, item.similarKanjiMemory.encode())
        statement.bindText(30, item.kanjiReadingMemory.encode())
        statement.bindText(31, item.readingKanjiMemory.encode())
        statement.bindText(32, item.sentenceReadingMemory.encode())
        statement.bindLong(33, item.schedulerRevision)
        statement.bindLong(34, item.routingVersion.toLong())
        statement.bindText(35, item.adaptiveRouteStateJson)
        val token = item.activeToken
        if (token == null) statement.bindNull(36) else statement.bindText(36, token)
        statement.bindLong(37, item.createdAtMillis)
    }

    fun read(row: SqlRow): RecordsStudyModels.StudyItem {
        val values = NamedSqlRow(row)
        val state = values.text("state")
        val dueAt = values.long("due_at")
        val stability = values.double("stability")
        val difficulty = values.double("difficulty")
        val totalReviews = values.int("total_reviews")
        val lapses = values.int("lapses")
        val learningStep = values.int("learning_step")
        val recognitionStage = values.int("recognition_stage")
        val writingRemediationPending = values.int("writing_remediation_pending") == 1
        val matureIntervalDays = values.int("mature_interval_days")
        val memoryFields = MemoryFields(
            state = state,
            dueAtMillis = dueAt,
            stability = stability,
            difficulty = difficulty,
            totalReviews = totalReviews,
            lapses = lapses,
            learningStep = learningStep,
            matureIntervalDays = matureIntervalDays,
        )
        val typingFallback = taskMemoryFallback(-1, recognitionStage, memoryFields)
        val kanjiFallback = taskMemoryFallback(0, recognitionStage, memoryFields)
        val fontFallback = taskMemoryFallback(1, recognitionStage, memoryFields)
        val wordFallback = taskMemoryFallback(2, recognitionStage, memoryFields)
        val writingFallback =
            if (writingRemediationPending) {
                memoryFields.toTaskMemory()
            } else {
                RecordsStudyModels.TaskMemory.initial()
            }

        return RecordsStudyModels.StudyItem(
            values.text("kanji"),
            state,
            dueAt,
            stability,
            difficulty,
            totalReviews,
            lapses,
            learningStep,
            values.int("writing_level"),
            recognitionStage,
            values.int("consecutive_failed_recognition_days"),
            values.long("last_failed_recognition_day"),
            writingRemediationPending,
            values.text("suppressed_by_task_type"),
            values.long("suppressed_at"),
            matureIntervalDays,
            values.text("answer_signature"),
            values.text("active_token"),
            values.long("created_at"),
            decodeMemory(values, "typing_meaning_memory", typingFallback),
            decodeMemory(values, "meaning_kanji_memory", RecordsStudyModels.TaskMemory.initial()),
            decodeMemory(values, "kanji_meaning_memory", kanjiFallback),
            decodeMemory(values, "font_meaning_memory", fontFallback),
            decodeMemory(values, "word_reading_memory", wordFallback),
            decodeMemory(values, "writing_remediation_memory", writingFallback),
            RecordsBase.LadderRung.fromWireName(values.text("rung")),
            RecordsBase.SchedulerPhase.fromWireName(values.text("phase")),
            values.int("real_pass_streak"),
            values.int("real_again_streak"),
            values.long("last_real_review_due_at"),
            false,
            decodeMemory(
                values,
                "similar_kanji_memory",
                RecordsStudyModels.TaskMemory.initial(),
            ),
            false,
            decodeMemory(
                values,
                "kanji_reading_memory",
                RecordsStudyModels.TaskMemory.initial(),
            ),
            false,
            decodeMemory(
                values,
                "reading_kanji_memory",
                RecordsStudyModels.TaskMemory.initial(),
            ),
            false,
            decodeMemory(
                values,
                "sentence_reading_memory",
                RecordsStudyModels.TaskMemory.initial(),
            ),
        ).copyBuilder()
            .schedulerRevision(values.long("scheduler_revision"))
            .routingVersion(values.int("routing_version"))
            .adaptiveRouteStateJson(values.text("adaptive_route_state_json"))
            .build()
    }

    private fun decodeMemory(
        values: NamedSqlRow,
        column: String,
        fallback: RecordsStudyModels.TaskMemory,
    ): RecordsStudyModels.TaskMemory =
        RecordsStudyModels.TaskMemory.decode(values.text(column), fallback)

    private fun taskMemoryFallback(
        memoryStage: Int,
        recognitionStage: Int,
        fields: MemoryFields,
    ): RecordsStudyModels.TaskMemory =
        if (recognitionStage.coerceIn(-1, 2) == memoryStage) {
            fields.toTaskMemory()
        } else {
            RecordsStudyModels.TaskMemory.initial()
        }

    private data class MemoryFields(
        val state: String,
        val dueAtMillis: Long,
        val stability: Double,
        val difficulty: Double,
        val totalReviews: Int,
        val lapses: Int,
        val learningStep: Int,
        val matureIntervalDays: Int,
    ) {
        fun toTaskMemory(): RecordsStudyModels.TaskMemory =
            RecordsStudyModels.TaskMemory.fromFields(
                RecordsStudyModels.TaskMemory.Fields(
                    state = state,
                    dueAtMillis = dueAtMillis,
                    stability = stability,
                    difficulty = difficulty,
                    totalReviews = totalReviews,
                    lapses = lapses,
                    learningStep = learningStep,
                    lastRating = "",
                    matureIntervalDays = matureIntervalDays,
                ),
            )
    }
}
