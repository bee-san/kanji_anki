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
            return localizedText("From $active · missed $suspended", "出典 $active · 見逃し $suspended")
        }
        if (active.isNotEmpty()) {
            return localizedText("From $active", "出典 $active")
        }
        if (suspended.isNotEmpty()) {
            return localizedText("Missed $suspended", "見逃し $suspended")
        }
        return localizedText("From AnkiDroid", "AnkiDroidから")
    }

    @JvmStatic
    fun queueCardBody(row: RecordsImportModels.DashboardRow): String {
        if (row.reasonText.isEmpty()) {
            return localizedText("Needs kanji practice.", "漢字の練習が必要です。")
        }
        val normalized = row.reasonText.lowercase(Locale.ROOT)
        if (
            normalized.contains("similar-kanji") ||
            normalized.contains("similar kanji") ||
            normalized.contains("similar choice")
        ) {
            return localizedText("Shape mix-up; practice writing.", "字形の取り違えです。書いて練習しましょう。")
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
            parts.add(localizedText("support ${row.matureSupportCount}/$matureSupportThreshold", "支え ${row.matureSupportCount}/$matureSupportThreshold"))
        }
        parts.add(recognitionStageLabel(item))
        if (item.dueAtMillis <= nowMillis) {
            parts.add(localizedText("due now", "今すぐ"))
        } else if (StudyLadderRules.STATE_LEARNING == item.state) {
            parts.add(localizedText(StudyLadderRules.STATE_LEARNING, "学習中"))
        }
        return parts.joinToString(" · ")
    }

    @JvmStatic
    fun recognitionStageLabel(item: RecordsStudyModels.StudyItem): String {
        return if (isJapaneseLocale()) {
            when (item.rung) {
                RecordsBase.LadderRung.WRITE_KANJI -> "漢字を書く"
                RecordsBase.LadderRung.TYPE_MEANING -> "意味を入力"
                RecordsBase.LadderRung.SIMILAR_KANJI -> "似た漢字"
                RecordsBase.LadderRung.MEANING_KANJI -> "意味→漢字"
                RecordsBase.LadderRung.FONT_MEANING -> "フォント→意味"
                RecordsBase.LadderRung.WORD_READING -> "単語→読み"
                RecordsBase.LadderRung.KANJI_MEANING -> "漢字→意味"
                RecordsBase.LadderRung.KANJI_READING -> "漢字の読み"
                RecordsBase.LadderRung.READING_KANJI -> "読み→漢字"
            }
        } else {
            when (item.rung) {
                RecordsBase.LadderRung.WRITE_KANJI -> "write kanji"
                RecordsBase.LadderRung.TYPE_MEANING -> "type meaning"
                RecordsBase.LadderRung.SIMILAR_KANJI -> "similar kanji"
                RecordsBase.LadderRung.MEANING_KANJI -> "meaning -> kanji"
                RecordsBase.LadderRung.FONT_MEANING -> "font -> meaning"
                RecordsBase.LadderRung.WORD_READING -> "word -> reading"
                RecordsBase.LadderRung.KANJI_MEANING -> "kanji -> meaning"
                RecordsBase.LadderRung.KANJI_READING -> "kanji -> reading"
                RecordsBase.LadderRung.READING_KANJI -> "reading -> kanji"
            }
        }
    }

    private fun localizedText(english: String, japanese: String): String {
        return if (isJapaneseLocale()) japanese else english
    }

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
