package dev.bee.kanjianki.core

import java.util.Locale

object FocusQueueCopy {
    private const val JAPANESE_LANGUAGE = "ja"
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
            return localizedText("From $active · missed $suspended", "$active から · $suspended を見逃し")
        }
        if (active.isNotEmpty()) {
            return localizedText("From $active", "$active から")
        }
        if (suspended.isNotEmpty()) {
            return localizedText("Missed $suspended", "$suspended を見逃し")
        }
        return localizedText("From AnkiDroid", "AnkiDroid から")
    }

    @JvmStatic
    fun queueCardBody(row: RecordsImportModels.DashboardRow): String {
        if (row.reasonText.isEmpty()) {
            return localizedText("Needs kanji practice.", "漢字練習が必要です。")
        }
        val normalized = row.reasonText.lowercase(Locale.ROOT)
        if (
            normalized.contains("similar-kanji") ||
            normalized.contains("similar kanji") ||
            normalized.contains("similar choice")
        ) {
            return localizedText("Shape mix-up; practice writing.", "形の取り違え。書いて練習しましょう。")
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
            parts.add(localizedText("weakness ${row.weaknessScore}", "弱点 ${row.weaknessScore}"))
        }
        if (row.matureSupportCount < matureSupportThreshold) {
            parts.add(
                localizedText(
                    "support ${row.matureSupportCount}/$matureSupportThreshold",
                    "成熟サポート ${row.matureSupportCount}/$matureSupportThreshold",
                ),
            )
        }
        parts.add(recognitionStageLabel(item))
        if (item.dueAtMillis <= nowMillis) {
            parts.add(localizedText("due now", "今すぐ復習"))
        } else if (StudyLadderRules.STATE_LEARNING == item.state) {
            parts.add(localizedText(StudyLadderRules.STATE_LEARNING, "学習中"))
        }
        return parts.joinToString(" · ")
    }

    @JvmStatic
    fun recognitionStageLabel(item: RecordsStudyModels.StudyItem): String {
        return when (item.rung) {
            RecordsBase.LadderRung.WRITE_KANJI -> localizedText("write kanji", "漢字を書く")
            RecordsBase.LadderRung.TYPE_MEANING -> localizedText("type meaning", "意味を入力")
            RecordsBase.LadderRung.SIMILAR_KANJI -> localizedText("similar kanji", "似た漢字")
            RecordsBase.LadderRung.MEANING_KANJI -> localizedText("meaning -> kanji", "意味→漢字")
            RecordsBase.LadderRung.FONT_MEANING -> localizedText("font -> meaning", "フォント→意味")
            RecordsBase.LadderRung.WORD_READING -> localizedText("word -> reading", "単語→読み")
            RecordsBase.LadderRung.KANJI_MEANING -> localizedText("kanji -> meaning", "漢字→意味")
        }
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
