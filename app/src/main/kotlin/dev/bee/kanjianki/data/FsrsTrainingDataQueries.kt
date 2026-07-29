package dev.bee.kanjianki.data

import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.FsrsElapsedTime
import dev.bee.kanjianki.core.FsrsReplaySample
import dev.bee.kanjianki.core.FsrsReplaySequence
import dev.bee.kanjianki.core.RecordsStudyModels
import org.json.JSONObject

/** Extracts real-due review sequences from the rich review log (DB v12+). */
internal class FsrsTrainingDataQueries(
    private val db: SQLiteDatabase,
) {
    data class GroupKey(
        val kanji: String,
        val answerSignature: String,
        val taskType: String,
    )

    fun sequences(): List<FsrsReplaySequence> {
        val groups = linkedMapOf<GroupKey, MutableList<TrainingRow>>()
        db.rawQuery(
            "SELECT kanji, answer_signature, task_type, rating, reviewed_at, " +
                "memory_before, scheduler_state_before_json, core_skill FROM review_log " +
                "ORDER BY reviewed_at ASC, id ASC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val memoryText = cursor.getString(5).orEmpty()
                val schedulerJson = cursor.getString(6).orEmpty()
                if (memoryText.isEmpty() || phase(schedulerJson) != REVIEW_PHASE) {
                    continue
                }
                val memory = decodedMemory(memoryText) ?: continue
                val rating = ratingValue(cursor.getString(3)) ?: continue
                val reviewedAt = cursor.getLong(4)
                val taskType = when (CoreSkill.fromWireName(cursor.getString(7))) {
                    CoreSkill.RECOGNITION -> BridgeScheduler.TASK_KANJI_MEANING
                    CoreSkill.CONTEXTUAL_READING -> BridgeScheduler.TASK_WORD_READING
                    null -> cursor.getString(2).orEmpty()
                }
                val key = GroupKey(
                    cursor.getString(0).orEmpty(),
                    cursor.getString(1).orEmpty(),
                    taskType,
                )
                groups.getOrPut(key) { ArrayList() }.add(
                    TrainingRow(memory, reviewedAt, rating),
                )
            }
        }
        return groups.values.mapNotNull { rows ->
            val first = rows.firstOrNull() ?: return@mapNotNull null
            FsrsReplaySequence(
                first.memory.stability,
                first.memory.difficulty,
                rows.map { row ->
                    FsrsReplaySample(
                        elapsedDays = elapsedDays(row.reviewedAtMillis, row.memory),
                        rating = row.rating,
                        outcome = row.rating != 1,
                        reviewedAtMillis = row.reviewedAtMillis,
                    )
                },
            )
        }
    }

    private fun phase(json: String): String? = try {
        JSONObject(json).optString("phase").takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    private fun decodedMemory(encoded: String): RecordsStudyModels.TaskMemory? {
        val fallback = RecordsStudyModels.TaskMemory.initial()
        val decoded = RecordsStudyModels.TaskMemory.decode(encoded, fallback)
        if (decoded === fallback || !decoded.stability.isFinite() || decoded.stability <= 0.0 ||
            !decoded.difficulty.isFinite() || decoded.difficulty !in 1.0..10.0
        ) {
            return null
        }
        return decoded
    }

    private fun ratingValue(rating: String?): Int? = when (rating) {
        BridgeScheduler.RATING_AGAIN -> 1
        BridgeScheduler.RATING_HARD -> 2
        BridgeScheduler.RATING_GOOD -> 3
        BridgeScheduler.RATING_EASY -> 4
        else -> null
    }

    private data class TrainingRow(
        val memory: RecordsStudyModels.TaskMemory,
        val reviewedAtMillis: Long,
        val rating: Int,
    )

    companion object {
        private const val REVIEW_PHASE = "review"

        /**
         * Delegates to [FsrsElapsedTime] rather than mirroring it.
         *
         * This used to be a hand-copied duplicate of the scheduler's computation,
         * annotated "exact mirror of ReviewContext.elapsedReviewDays()". A mirror
         * maintained by comment is one edit away from being a lie, and the failure
         * would be near-invisible: the fitter would train on a different elapsed
         * time than the scheduler schedules with, and the loss would just come out
         * slightly wrong.
         */
        @JvmStatic
        fun elapsedDays(reviewedAtMillis: Long, memory: RecordsStudyModels.TaskMemory): Double =
            FsrsElapsedTime.elapsedDays(
                reviewedAtMillis,
                memory.lastReviewedAtMillis,
                memory.dueAtMillis,
                memory.matureIntervalDays,
            )
    }
}
