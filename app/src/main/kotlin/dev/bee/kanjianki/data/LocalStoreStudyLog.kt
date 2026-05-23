package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyTaskTimingPolicy

internal class LocalStoreStudyLog(
    private val store: LocalStoreStudy,
) {
    fun saveLearningRepeat(repeat: RecordsSchedulerModels.LearningRepeat?) {
        if (repeat == null || repeat.kanji.isEmpty() || repeat.taskType.isEmpty()) {
            return
        }
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_KANJI, repeat.kanji)
        values.put(LocalStoreBase.COLUMN_ANSWER_SIGNATURE, repeat.answerSignature)
        values.put(LocalStoreBase.COLUMN_TASK_TYPE, repeat.taskType)
        values.put("repeat_type", repeat.repeatType)
        values.put("step_index", repeat.stepIndex)
        values.put(LocalStoreBase.COLUMN_DUE_AT, repeat.dueAtMillis)
        values.put(LocalStoreBase.COLUMN_ACTIVE_TOKEN, repeat.activeToken)
        values.put(LocalStoreBase.COLUMN_CREATED_AT, repeat.createdAtMillis)
        values.put(LocalStoreBase.COLUMN_UPDATED_AT, repeat.updatedAtMillis)
        store.writableDatabase.insertWithOnConflict(
            LocalStoreBase.TABLE_LEARNING_REPEATS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun enqueueLearningRepeat(
        item: RecordsStudyModels.StudyItem?,
        taskType: String?,
        repeatType: String?,
        stepIndex: Int,
        dueAtMillis: Long,
        nowMillis: Long,
    ) {
        if (item == null || taskType == null || taskType.isEmpty()) {
            return
        }
        saveLearningRepeat(
            RecordsSchedulerModels.LearningRepeat(
                item.kanji,
                item.answerSignature,
                taskType,
                repeatType,
                stepIndex,
                dueAtMillis,
                "",
                nowMillis,
                nowMillis,
            )
        )
    }

    fun clearLearningRepeat(repeat: RecordsSchedulerModels.LearningRepeat?) {
        if (repeat == null) {
            return
        }
        store.writableDatabase.delete(
            LocalStoreBase.TABLE_LEARNING_REPEATS,
            "kanji=? AND answer_signature=? AND task_type=?",
            arrayOf(repeat.kanji, repeat.answerSignature, repeat.taskType),
        )
    }

    fun dueLearningRepeats(nowMillis: Long): List<RecordsSchedulerModels.LearningRepeat> {
        val repeats = mutableListOf<RecordsSchedulerModels.LearningRepeat>()
        store.readableDatabase.query(
            LocalStoreBase.TABLE_LEARNING_REPEATS,
            null,
            "due_at<=?",
            arrayOf(nowMillis.toString()),
            null,
            null,
            "due_at ASC, updated_at ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                repeats.add(readLearningRepeat(cursor))
            }
        }
        return repeats
    }

    fun recordStudyTaskAnswered(
        taskKey: String?,
        kanji: String?,
        taskType: String?,
        startedAt: Long,
        answeredAt: Long,
        activeElapsedMillis: Long,
        outcome: String?,
    ): Boolean {
        val normalizedKey = taskKey ?: ""
        if (normalizedKey.isEmpty()) {
            return false
        }
        val values = ContentValues()
        values.put("task_key", normalizedKey)
        values.put(LocalStoreBase.COLUMN_KANJI, kanji ?: "")
        values.put(LocalStoreBase.COLUMN_TASK_TYPE, taskType ?: "")
        values.put(LocalStoreBase.COLUMN_STARTED_AT, startedAt.coerceAtLeast(0L))
        values.put("answered_at", answeredAt.coerceAtLeast(0L))
        values.put(
            "active_elapsed_ms",
            StudyTaskTimingPolicy.boundedElapsed(activeElapsedMillis, LocalStoreBase.MAX_STUDY_TASK_ELAPSED_MS),
        )
        values.put("outcome", outcome ?: "")
        return store.writableDatabase.insertWithOnConflict(
            LocalStoreBase.TABLE_STUDY_TASK_LOG,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        ) != -1L
    }

    private fun readLearningRepeat(cursor: Cursor): RecordsSchedulerModels.LearningRepeat {
        return RecordsSchedulerModels.LearningRepeat(
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_ANSWER_SIGNATURE),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_TASK_TYPE),
            LocalStoreBase.string(cursor, "repeat_type"),
            LocalStoreBase.integer(cursor, "step_index"),
            LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_DUE_AT),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_ACTIVE_TOKEN),
            LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_CREATED_AT),
            LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_UPDATED_AT),
        )
    }
}
