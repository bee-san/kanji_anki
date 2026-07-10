package dev.bee.kanjianki.core

import kotlin.math.roundToInt

/** Groups persisted task-type wire names into the four user-facing skill families. */
object TaskTypeAccuracyPolicy {
    enum class Group {
        MEANING,
        READING,
        WRITING,
        DISCRIMINATION,
    }

    data class Summary(
        val taskType: String,
        val correct: Int,
        val total: Int,
    )

    data class Accuracy(
        val group: Group,
        val correct: Int,
        val total: Int,
    ) {
        val percent: Int
            get() = if (total == 0) 0 else ((correct * 100.0) / total).roundToInt().coerceIn(0, 100)
    }

    /**
     * Mapping of scheduler wire names:
     * meaning = typed/reverse/recognition/font meaning; reading = kanji, word,
     * and sentence reading; writing = handwriting; discrimination = visual and
     * same-reading choice cards. Legacy typed/writing names remain accepted.
     */
    @JvmStatic
    fun groupFor(taskType: String?): Group? = when (taskType?.trim()?.lowercase()) {
        "type_meaning", "typing_meaning", "meaning_kanji", "kanji_meaning", "font_meaning" -> Group.MEANING
        "kanji_reading", "word_reading", "sentence_reading" -> Group.READING
        "write_kanji", "writing_remediation" -> Group.WRITING
        "similar_kanji", "reading_kanji" -> Group.DISCRIMINATION
        else -> null
    }

    @JvmStatic
    fun summarize(rows: List<Summary>?): List<Accuracy> {
        val counts = linkedMapOf<Group, IntArray>()
        Group.entries.forEach { counts[it] = intArrayOf(0, 0) }
        rows.orEmpty().forEach { row ->
            val group = groupFor(row.taskType) ?: return@forEach
            val total = row.total.coerceAtLeast(0)
            val correct = row.correct.coerceIn(0, total)
            counts.getValue(group).also {
                it[0] += correct
                it[1] += total
            }
        }
        return Group.entries.mapNotNull { group ->
            val count = counts.getValue(group)
            if (count[1] == 0) null else Accuracy(group, count[0], count[1])
        }
    }
}
