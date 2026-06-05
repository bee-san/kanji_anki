package dev.bee.kanjianki.core

import java.util.Locale

object FocusQueueCopy {
    private const val SOURCE_ACTIVE = "active"
    private const val SOURCE_SUSPENDED = "suspended"

    @JvmStatic
    fun sourceEvidenceText(row: RecordsImportModels.DashboardRow): String {
        var active = ""
        var suspended = ""
        for (example in row.examples) {
            if (active.isEmpty() && SOURCE_ACTIVE == example.sourceType) {
                active = example.expression
            } else if (suspended.isEmpty() && SOURCE_SUSPENDED == example.sourceType) {
                suspended = example.expression
            }
        }
        if (active.isNotEmpty() && suspended.isNotEmpty()) {
            return "From $active · missed $suspended"
        }
        if (active.isNotEmpty()) {
            return "From $active"
        }
        if (suspended.isNotEmpty()) {
            return "Missed $suspended"
        }
        return "From AnkiDroid"
    }

    @JvmStatic
    fun queueCardBody(row: RecordsImportModels.DashboardRow): String {
        if (row.reasonText.isEmpty()) {
            return "Needs kanji practice."
        }
        val normalized = row.reasonText.lowercase(Locale.ROOT)
        if (
            normalized.contains("similar-kanji") ||
            normalized.contains("similar kanji") ||
            normalized.contains("similar choice")
        ) {
            return "Shape mix-up; practice writing."
        }
        return row.reasonText
    }

    @JvmStatic
    fun focusReasonLine(
        row: RecordsImportModels.DashboardRow,
        item: RecordsStudyModels.StudyItem,
        nowMillis: Long,
        matureSupportThreshold: Int,
    ): String {
        val parts = ArrayList<String>()
        if (row.weaknessScore > 0) {
            parts.add("weakness ${row.weaknessScore}")
        }
        if (row.matureSupportCount < matureSupportThreshold) {
            parts.add("support ${row.matureSupportCount}/$matureSupportThreshold")
        }
        parts.add(recognitionStageLabel(item))
        if (item.dueAtMillis <= nowMillis) {
            parts.add("due now")
        } else if (StudyLadderRules.STATE_LEARNING == item.state) {
            parts.add(StudyLadderRules.STATE_LEARNING)
        }
        return parts.joinToString(" · ")
    }

    @JvmStatic
    fun recognitionStageLabel(item: RecordsStudyModels.StudyItem): String {
        return when (item.rung) {
            RecordsBase.LadderRung.WRITE_KANJI -> "write kanji"
            RecordsBase.LadderRung.TYPE_MEANING -> "type meaning"
            RecordsBase.LadderRung.SIMILAR_KANJI -> "similar kanji"
            RecordsBase.LadderRung.MEANING_KANJI -> "meaning -> kanji"
            RecordsBase.LadderRung.FONT_MEANING -> "font -> meaning"
            RecordsBase.LadderRung.WORD_READING -> "word -> reading"
            RecordsBase.LadderRung.KANJI_MEANING -> "kanji -> meaning"
        }
    }
}
