package dev.bee.kanjianki.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.StudyTaskTimingPolicy;

import java.util.ArrayList;
import java.util.List;

final class LocalStoreStudyLog {
    private final LocalStoreStudy store;

    LocalStoreStudyLog(LocalStoreStudy store) {
        this.store = store;
    }

    void saveLearningRepeat(RecordsSchedulerModels.LearningRepeat repeat) {
        if (repeat == null || repeat.kanji.isEmpty() || repeat.taskType.isEmpty()) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(LocalStoreBase.COLUMN_KANJI, repeat.kanji);
        values.put(LocalStoreBase.COLUMN_ANSWER_SIGNATURE, repeat.answerSignature);
        values.put(LocalStoreBase.COLUMN_TASK_TYPE, repeat.taskType);
        values.put("repeat_type", repeat.repeatType);
        values.put("step_index", repeat.stepIndex);
        values.put(LocalStoreBase.COLUMN_DUE_AT, repeat.dueAtMillis);
        values.put(LocalStoreBase.COLUMN_ACTIVE_TOKEN, repeat.activeToken);
        values.put(LocalStoreBase.COLUMN_CREATED_AT, repeat.createdAtMillis);
        values.put(LocalStoreBase.COLUMN_UPDATED_AT, repeat.updatedAtMillis);
        store.getWritableDatabase().insertWithOnConflict(LocalStoreBase.TABLE_LEARNING_REPEATS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void enqueueLearningRepeat(RecordsStudyModels.StudyItem item, String taskType, String repeatType, int stepIndex, long dueAtMillis, long nowMillis) {
        if (item == null || taskType == null || taskType.isEmpty()) {
            return;
        }
        saveLearningRepeat(new RecordsSchedulerModels.LearningRepeat(
                item.kanji,
                item.answerSignature,
                taskType,
                repeatType,
                stepIndex,
                dueAtMillis,
                "",
                nowMillis,
                nowMillis
        ));
    }

    void clearLearningRepeat(RecordsSchedulerModels.LearningRepeat repeat) {
        if (repeat == null) {
            return;
        }
        store.getWritableDatabase().delete(
                LocalStoreBase.TABLE_LEARNING_REPEATS,
                "kanji=? AND answer_signature=? AND task_type=?",
                new String[]{repeat.kanji, repeat.answerSignature, repeat.taskType}
        );
    }

    List<RecordsSchedulerModels.LearningRepeat> dueLearningRepeats(long nowMillis) {
        List<RecordsSchedulerModels.LearningRepeat> repeats = new ArrayList<>();
        Cursor cursor = store.getReadableDatabase().query(
                LocalStoreBase.TABLE_LEARNING_REPEATS,
                null,
                "due_at<=?",
                new String[]{Long.toString(nowMillis)},
                null,
                null,
                "due_at ASC, updated_at ASC"
        );
        try {
            while (cursor.moveToNext()) {
                repeats.add(readLearningRepeat(cursor));
            }
        } finally {
            cursor.close();
        }
        return repeats;
    }

    boolean recordStudyTaskAnswered(String taskKey, String kanji, String taskType, long startedAt, long answeredAt, long activeElapsedMillis, String outcome) {
        String normalizedKey = taskKey == null ? "" : taskKey;
        if (normalizedKey.isEmpty()) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put("task_key", normalizedKey);
        values.put(LocalStoreBase.COLUMN_KANJI, kanji == null ? "" : kanji);
        values.put(LocalStoreBase.COLUMN_TASK_TYPE, taskType == null ? "" : taskType);
        values.put(LocalStoreBase.COLUMN_STARTED_AT, Math.max(0L, startedAt));
        values.put("answered_at", Math.max(0L, answeredAt));
        values.put(
                "active_elapsed_ms",
                StudyTaskTimingPolicy.boundedElapsed(activeElapsedMillis, LocalStoreBase.MAX_STUDY_TASK_ELAPSED_MS)
        );
        values.put("outcome", outcome == null ? "" : outcome);
        return store.getWritableDatabase().insertWithOnConflict(LocalStoreBase.TABLE_STUDY_TASK_LOG, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L;
    }

    private RecordsSchedulerModels.LearningRepeat readLearningRepeat(Cursor cursor) {
        return new RecordsSchedulerModels.LearningRepeat(
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_ANSWER_SIGNATURE),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_TASK_TYPE),
                LocalStoreBase.string(cursor, "repeat_type"),
                LocalStoreBase.integer(cursor, "step_index"),
                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_DUE_AT),
                LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_ACTIVE_TOKEN),
                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_CREATED_AT),
                LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_UPDATED_AT)
        );
    }
}
