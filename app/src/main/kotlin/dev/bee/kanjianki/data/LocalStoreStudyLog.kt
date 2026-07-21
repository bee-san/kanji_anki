package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.StudyTaskTimingPolicy

internal class LocalStoreStudyLog(
    private val store: LocalStoreStudy,
) {
    fun recordStudyTaskAnswered(
        taskKey: String?,
        kanji: String?,
        taskType: String?,
        startedAt: Long,
        answeredAt: Long,
        activeElapsedMillis: Long,
        outcome: String?,
        db: SQLiteDatabase = store.writableDatabase,
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
        return db.insertWithOnConflict(
            LocalStoreBase.TABLE_STUDY_TASK_LOG,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        ) != -1L
    }
}
