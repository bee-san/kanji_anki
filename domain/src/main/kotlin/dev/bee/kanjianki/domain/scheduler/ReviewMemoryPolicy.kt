package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.model.study.TaskMemory

class ReviewMemoryPolicy {
    fun activeTaskMemory(
        item: StudyQueueItem,
        rung: StudyRung,
    ): TaskMemory {
        val memory = item.memories.memoryForRung(rung)
        if (memory.totalReviews > 0 || item.totalReviews <= 0) {
            return memory
        }
        return TaskMemory.fromStudyFields(
            state = item.state.wireName,
            dueAtMillis = item.dueAtMillis,
            stability = item.stability,
            difficulty = item.difficulty,
            totalReviews = item.totalReviews,
            lapses = item.lapses,
            learningStep = item.learningStep,
            matureIntervalDays = item.matureIntervalDays,
        )
    }

    fun elapsedReviewDays(
        memory: TaskMemory,
        nowMillis: Long,
    ): Int {
        val previousIntervalMillis = memory.matureIntervalDays.coerceAtLeast(0) * DAY_MILLIS
        val lastReviewAtMillis = (memory.dueAtMillis - previousIntervalMillis).coerceAtLeast(0L)
        val elapsedMillis = (nowMillis - lastReviewAtMillis).coerceAtLeast(0L)
        return minOf(Int.MAX_VALUE.toLong(), elapsedMillis / DAY_MILLIS).toInt()
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
