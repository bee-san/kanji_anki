package dev.bee.kanjianki.domain.model.study

data class TaskMemory(
    val state: String,
    val dueAtMillis: Long,
    val stability: Double,
    val difficulty: Double,
    val totalReviews: Int,
    val lapses: Int,
    val learningStep: Int,
    val lastRating: String,
    val matureIntervalDays: Int,
    val consecutivePasses: Int = 0,
    val lastPassedDueAtMillis: Long = 0L,
) {
    fun withDueAtMillis(dueAtMillis: Long): TaskMemory =
        copy(dueAtMillis = dueAtMillis.coerceAtLeast(0L))

    fun encode(): String = listOf(
        state,
        dueAtMillis,
        stability,
        difficulty,
        totalReviews,
        lapses,
        learningStep,
        lastRating,
        matureIntervalDays,
        consecutivePasses,
        lastPassedDueAtMillis,
    ).joinToString(separator = "\t")

    companion object {
        fun initial(): TaskMemory = from(
            state = "new",
            dueAtMillis = 0L,
            stability = 0.4,
            difficulty = 5.0,
            totalReviews = 0,
            lapses = 0,
            learningStep = 0,
            lastRating = "",
            matureIntervalDays = 0,
        )

        fun fromStudyFields(
            state: String?,
            dueAtMillis: Long,
            stability: Double,
            difficulty: Double,
            totalReviews: Int,
            lapses: Int,
            learningStep: Int,
            matureIntervalDays: Int,
        ): TaskMemory = from(
            state = state,
            dueAtMillis = dueAtMillis,
            stability = stability,
            difficulty = difficulty,
            totalReviews = totalReviews,
            lapses = lapses,
            learningStep = learningStep,
            lastRating = "",
            matureIntervalDays = matureIntervalDays,
        )

        fun decode(
            encoded: String?,
            fallback: TaskMemory? = null,
        ): TaskMemory {
            val safeFallback = fallback ?: initial()
            if (encoded.isNullOrEmpty()) {
                return safeFallback
            }
            val parts = encoded.split('\t')
            if (parts.size < 9) {
                return safeFallback
            }
            return try {
                from(
                    state = parts[0],
                    dueAtMillis = parts[1].toLong(),
                    stability = parts[2].toDouble(),
                    difficulty = parts[3].toDouble(),
                    totalReviews = parts[4].toInt(),
                    lapses = parts[5].toInt(),
                    learningStep = parts[6].toInt(),
                    lastRating = parts[7],
                    matureIntervalDays = parts[8].toInt(),
                    consecutivePasses = parts.getOrNull(9)?.toIntOrNull() ?: 0,
                    lastPassedDueAtMillis = parts.getOrNull(10)?.toLongOrNull() ?: 0L,
                )
            } catch (_: RuntimeException) {
                safeFallback
            }
        }

        fun from(
            state: String?,
            dueAtMillis: Long,
            stability: Double,
            difficulty: Double,
            totalReviews: Int,
            lapses: Int,
            learningStep: Int,
            lastRating: String?,
            matureIntervalDays: Int,
            consecutivePasses: Int = 0,
            lastPassedDueAtMillis: Long = 0L,
        ): TaskMemory = TaskMemory(
            state = state?.takeIf { it.isNotEmpty() } ?: "new",
            dueAtMillis = dueAtMillis.coerceAtLeast(0L),
            stability = stability,
            difficulty = difficulty,
            totalReviews = totalReviews.coerceAtLeast(0),
            lapses = lapses.coerceAtLeast(0),
            learningStep = learningStep.coerceAtLeast(0),
            lastRating = lastRating.orEmpty(),
            matureIntervalDays = matureIntervalDays.coerceAtLeast(0),
            consecutivePasses = consecutivePasses.coerceAtLeast(0),
            lastPassedDueAtMillis = lastPassedDueAtMillis.coerceAtLeast(0L),
        )
    }
}

object StudyTaskWireNames {
    const val WRITE_KANJI = "write_kanji"
    const val TYPE_MEANING = "type_meaning"
    const val TYPING_MEANING = "typing_meaning"
    const val SIMILAR_KANJI = "similar_kanji"
    const val MEANING_KANJI = "meaning_kanji"
    const val KANJI_MEANING = "kanji_meaning"
    const val FONT_MEANING = "font_meaning"
    const val WORD_READING = "word_reading"
    const val WRITING_REMEDIATION = "writing_remediation"
}

data class TaskMemoryBank(
    val typingMeaningMemory: TaskMemory = TaskMemory.initial(),
    val meaningKanjiMemory: TaskMemory = TaskMemory.initial(),
    val kanjiMeaningMemory: TaskMemory = TaskMemory.initial(),
    val fontMeaningMemory: TaskMemory = TaskMemory.initial(),
    val wordReadingMemory: TaskMemory = TaskMemory.initial(),
    val writingRemediationMemory: TaskMemory = TaskMemory.initial(),
    val similarKanjiMemory: TaskMemory = TaskMemory.initial(),
) {
    fun memoryForTaskType(taskType: String?): TaskMemory = when (taskType) {
        StudyTaskWireNames.WRITE_KANJI,
        StudyTaskWireNames.WRITING_REMEDIATION
        -> writingRemediationMemory
        StudyTaskWireNames.TYPE_MEANING,
        StudyTaskWireNames.TYPING_MEANING
        -> typingMeaningMemory
        StudyTaskWireNames.SIMILAR_KANJI -> similarKanjiMemory
        StudyTaskWireNames.MEANING_KANJI -> meaningKanjiMemory
        StudyTaskWireNames.WORD_READING -> wordReadingMemory
        StudyTaskWireNames.FONT_MEANING -> fontMeaningMemory
        else -> kanjiMeaningMemory
    }

    fun memoryForRung(rung: StudyRung?): TaskMemory = when (rung) {
        StudyRung.WRITE_KANJI -> writingRemediationMemory
        StudyRung.TYPE_MEANING -> typingMeaningMemory
        StudyRung.SIMILAR_KANJI -> similarKanjiMemory
        StudyRung.MEANING_KANJI -> meaningKanjiMemory
        StudyRung.FONT_MEANING -> fontMeaningMemory
        StudyRung.WORD_READING -> wordReadingMemory
        StudyRung.KANJI_MEANING,
        null
        -> kanjiMeaningMemory
    }

    fun withTaskMemory(
        taskType: String?,
        memory: TaskMemory,
    ): TaskMemoryBank = when (taskType) {
        StudyTaskWireNames.WRITE_KANJI,
        StudyTaskWireNames.WRITING_REMEDIATION
        -> copy(writingRemediationMemory = memory)
        StudyTaskWireNames.TYPE_MEANING,
        StudyTaskWireNames.TYPING_MEANING
        -> copy(typingMeaningMemory = memory)
        StudyTaskWireNames.SIMILAR_KANJI -> copy(similarKanjiMemory = memory)
        StudyTaskWireNames.MEANING_KANJI -> copy(meaningKanjiMemory = memory)
        StudyTaskWireNames.WORD_READING -> copy(wordReadingMemory = memory)
        StudyTaskWireNames.FONT_MEANING -> copy(fontMeaningMemory = memory)
        else -> copy(kanjiMeaningMemory = memory)
    }
}
