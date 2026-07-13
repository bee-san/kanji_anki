package dev.bee.kanjianki

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import org.json.JSONObject

/** Durable description of the answered card that must remain visible until Continue. */
data class StudyPendingAnswerSnapshot(
    val feedback: StudyAnswerFeedbackSnapshot,
    val kanji: String,
    val taskType: String,
    val writingRequired: Boolean,
    val prompt: String,
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

/** SharedPreferences-backed process-restart boundary for one pending answered card. */
class StudyPendingAnswerStore(private val preferences: SharedPreferences) {
    fun save(snapshot: StudyPendingAnswerSnapshot) {
        preferences.edit { putString(KEY_SNAPSHOT, encode(snapshot)) }
    }

    fun read(): StudyPendingAnswerSnapshot? {
        val encoded = preferences.getString(KEY_SNAPSHOT, null) ?: return null
        return decode(encoded) ?: run {
            clear()
            null
        }
    }

    fun clear() {
        preferences.edit { remove(KEY_SNAPSHOT) }
    }

    private fun encode(snapshot: StudyPendingAnswerSnapshot): String {
        return JSONObject()
            .put(KEY_VERSION, FORMAT_VERSION)
            .put(KEY_TOKEN, snapshot.feedback.sessionToken)
            .put(KEY_PHASE, snapshot.feedback.phase.name)
            .put(KEY_OUTCOME, snapshot.feedback.outcome?.name.orEmpty())
            .put(KEY_SELECTED_ANSWER, snapshot.feedback.selectedAnswer)
            .put(KEY_KANJI, snapshot.kanji)
            .put(KEY_TASK_TYPE, snapshot.taskType)
            .put(KEY_WRITING_REQUIRED, snapshot.writingRequired)
            .put(KEY_PROMPT, snapshot.prompt)
            .toString()
    }

    private fun decode(encoded: String): StudyPendingAnswerSnapshot? {
        return runCatching {
            val json = JSONObject(encoded)
            if (json.getInt(KEY_VERSION) != FORMAT_VERSION) {
                return null
            }
            val token = json.getString(KEY_TOKEN)
            val kanji = json.getString(KEY_KANJI)
            if (token.isBlank() || kanji.isBlank()) {
                return null
            }
            val outcomeName = json.optString(KEY_OUTCOME)
            StudyPendingAnswerSnapshot(
                feedback = StudyAnswerFeedbackSnapshot(
                    sessionToken = token,
                    phase = StudyAnswerFeedbackPhase.valueOf(json.getString(KEY_PHASE)),
                    outcome = outcomeName.takeIf { it.isNotBlank() }?.let(StudyAnswerOutcome::valueOf),
                    selectedAnswer = json.optString(KEY_SELECTED_ANSWER),
                ),
                kanji = kanji,
                taskType = json.getString(KEY_TASK_TYPE),
                writingRequired = json.getBoolean(KEY_WRITING_REQUIRED),
                prompt = json.optString(KEY_PROMPT),
            )
        }.getOrNull()
    }

    companion object {
        private const val FORMAT_VERSION = 1
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_VERSION = "version"
        private const val KEY_TOKEN = "token"
        private const val KEY_PHASE = "phase"
        private const val KEY_OUTCOME = "outcome"
        private const val KEY_SELECTED_ANSWER = "selected_answer"
        private const val KEY_KANJI = "kanji"
        private const val KEY_TASK_TYPE = "task_type"
        private const val KEY_WRITING_REQUIRED = "writing_required"
        private const val KEY_PROMPT = "prompt"
    }
}
