package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsStudyModels

internal object SqlStudyItemMapper {
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
