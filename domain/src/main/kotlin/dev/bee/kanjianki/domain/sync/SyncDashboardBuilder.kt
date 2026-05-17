package dev.bee.kanjianki.domain.sync

import dev.bee.kanjianki.domain.importing.ImportSourceEvidence
import dev.bee.kanjianki.domain.importing.ImportedKanjiCandidate
import dev.bee.kanjianki.domain.importing.KanjiRankLookup
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.ImportSource
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyExample
import java.util.Locale

class SyncDashboardBuilder(
    private val ranks: KanjiRankLookup,
) {
    fun build(
        importCandidates: List<ImportedKanjiCandidate>,
        settings: ImportSettings,
    ): List<StudyDashboardRow> =
        importCandidates.mapNotNull { candidate ->
            val row = MutableDashboardRow(candidate.kanji)
            for (source in candidate.sources) {
                row.add(source.toStudyExample(), source.forcePractice)
            }
            row.build(ranks, settings)
        }.sortedWith(
            compareByDescending<StudyDashboardRow> { it.weaknessScore }
                .thenByDescending { it.suspendedExampleCount }
                .thenBy { it.jitenRank ?: Int.MAX_VALUE }
                .thenBy { it.kanji },
        )

    private fun ImportSourceEvidence.toStudyExample(): StudyExample = StudyExample(
        sourceType = sourceType.wireName,
        expression = expression,
        reading = reading,
        meaning = meaning,
        fsrsDifficulty = fsrsDifficulty,
        fsrsRetrievability = fsrsRetrievability,
        mature = mature,
        lapses = lapses,
        intervalDays = intervalDays,
        reps = reps,
        fsrsStability = fsrsStability,
        cardId = cardId.value,
        noteId = noteId.value,
        sentence = sentence,
    )

    private class MutableDashboardRow(
        private val kanji: String,
    ) {
        private val examples = mutableListOf<StudyExample>()
        private var forcePractice = false

        fun add(
            example: StudyExample,
            forcePractice: Boolean,
        ) {
            examples += example
            this.forcePractice = this.forcePractice || forcePractice
        }

        fun build(
            ranks: KanjiRankLookup,
            settings: ImportSettings,
        ): StudyDashboardRow? {
            val summary = summarize(settings)
            val supportDeficit = (settings.matureSupportThreshold - summary.mature).coerceAtLeast(0)
            val weakness = summary.suspended * SUSPENDED_WEIGHT +
                supportDeficit * SUPPORT_DEFICIT_WEIGHT +
                (summary.lapses * LAPSE_WEIGHT).coerceAtMost(MAX_LAPSE_PRESSURE) +
                (summary.intervalPressure * INTERVAL_PRESSURE_WEIGHT).coerceAtMost(MAX_INTERVAL_PRESSURE) +
                summary.fsrsPressure.coerceAtMost(MAX_FSRS_PRESSURE)
            if (weakness <= 0 && !forcePractice) {
                return null
            }
            val reason = reasonFor(summary, supportDeficit)
            return StudyDashboardRow(
                kanji = kanji,
                jitenRank = ranks.rankOf(kanji),
                primaryMeaning = summary.meaning,
                reading = summary.reading,
                browserSearch = browserSearchForKanji(kanji, settings),
                weaknessScore = weakness,
                reasonCode = reason.code,
                reasonText = reason.text,
                activeExampleCount = summary.active,
                suspendedExampleCount = summary.suspended,
                matureSupportCount = summary.mature,
                examples = summary.trimmed,
            )
        }

        private fun summarize(settings: ImportSettings): RowSummary {
            val summary = RowSummary()
            val seenCards = linkedSetOf<Long>()
            for (example in examples) {
                summary.addExample(example, fsrsPressure(example, settings), seenCards)
            }
            return summary
        }

        private fun reasonFor(
            summary: RowSummary,
            supportDeficit: Int,
        ): Reason = when {
            summary.suspended > 0 -> Reason(
                "suspended_archive",
                "${summary.suspended} missed example${if (summary.suspended == 1) "" else "s"} made this a writing-practice target.",
            )
            summary.fsrsPressure > 0 -> Reason(
                "fsrs_weak_memory",
                "Anki FSRS memory state marks this kanji as fragile.",
            )
            supportDeficit > 0 -> Reason(
                "weak_support",
                "Only ${summary.mature} known example${if (summary.mature == 1) "" else "s"} support this kanji.",
            )
            summary.intervalPressure > 0 -> Reason(
                "anki_scheduler_weakness",
                "Anki has ${summary.reps} active reviews but little mature support for this kanji.",
            )
            summary.lapses > 0 -> Reason(
                "anki_lapses",
                "Your active Anki cards containing this kanji have ${summary.lapses} lapse${if (summary.lapses == 1) "" else "s"}.",
            )
            else -> Reason(
                "watch",
                "This kanji appears in your active cards and is ready for examples.",
            )
        }

        private fun fsrsPressure(
            example: StudyExample,
            settings: ImportSettings,
        ): Int {
            var pressure = 0
            val retrievability = normalizedRetrievability(example.fsrsRetrievability)
            if (retrievability != null && retrievability < LOW_RETRIEVABILITY) {
                pressure += if (retrievability < VERY_LOW_RETRIEVABILITY) {
                    HIGH_FSRS_PRESSURE
                } else {
                    LOW_FSRS_PRESSURE
                }
            }
            if (example.fsrsDifficulty != null && example.fsrsDifficulty >= HIGH_FSRS_DIFFICULTY) {
                pressure += LOW_FSRS_PRESSURE
            }
            if (example.fsrsStability != null &&
                example.reps >= STABILITY_REP_THRESHOLD &&
                example.fsrsStability < settings.matureDays
            ) {
                pressure += LOW_FSRS_PRESSURE
            }
            if (example.mature &&
                example.fsrsStability != null &&
                example.fsrsStability >= settings.matureDays * STRONG_STABILITY_MULTIPLIER &&
                pressure == 0
            ) {
                pressure -= LOW_FSRS_DISCOUNT
            }
            return pressure.coerceAtLeast(0)
        }

        private fun normalizedRetrievability(value: Double?): Double? = when {
            value == null || value < 0.0 -> null
            value > 1.0 && value <= 100.0 -> value / 100.0
            value > 1.0 -> null
            else -> value
        }
    }

    private class RowSummary {
        var active = 0
            private set
        var suspended = 0
            private set
        var mature = 0
            private set
        var lapses = 0
            private set
        var reps = 0
            private set
        var intervalPressure = 0
            private set
        var fsrsPressure = 0
            private set
        var meaning = ""
            private set
        var reading = ""
            private set
        val trimmed = mutableListOf<StudyExample>()

        fun addExample(
            example: StudyExample,
            fsrsPressureValue: Int,
            seenCards: MutableSet<Long>,
        ) {
            if (seenCards.add(example.cardId) && trimmed.size < MAX_EXAMPLES) {
                trimmed += example
            }
            if (example.sourceType == ImportSource.SUSPENDED.wireName) {
                suspended++
            } else {
                addActiveExample(example, fsrsPressureValue)
            }
            if (meaning.isEmpty() && example.meaning.isNotEmpty()) {
                meaning = example.meaning
            }
            if (reading.isEmpty() && example.reading.isNotEmpty()) {
                reading = example.reading
            }
        }

        private fun addActiveExample(
            example: StudyExample,
            fsrsPressureValue: Int,
        ) {
            active++
            if (example.mature) {
                mature++
            }
            lapses += example.lapses
            reps += example.reps
            if (example.reps >= INTERVAL_REP_THRESHOLD && !example.mature) {
                intervalPressure++
            }
            fsrsPressure += fsrsPressureValue
        }
    }

    private data class Reason(
        val code: String,
        val text: String,
    )

    companion object {
        private const val SUSPENDED_WEIGHT = 12
        private const val SUPPORT_DEFICIT_WEIGHT = 5
        private const val LAPSE_WEIGHT = 2
        private const val MAX_LAPSE_PRESSURE = 8
        private const val INTERVAL_PRESSURE_WEIGHT = 2
        private const val MAX_INTERVAL_PRESSURE = 6
        private const val MAX_FSRS_PRESSURE = 12
        private const val LOW_RETRIEVABILITY = 0.75
        private const val VERY_LOW_RETRIEVABILITY = 0.50
        private const val HIGH_FSRS_PRESSURE = 6
        private const val LOW_FSRS_PRESSURE = 3
        private const val HIGH_FSRS_DIFFICULTY = 7.0
        private const val STABILITY_REP_THRESHOLD = 5
        private const val STRONG_STABILITY_MULTIPLIER = 2.0
        private const val LOW_FSRS_DISCOUNT = 2
        private const val INTERVAL_REP_THRESHOLD = 8
        private const val MAX_EXAMPLES = 8

        private fun browserSearchForKanji(
            kanji: String,
            settings: ImportSettings,
        ): String = String.format(
            Locale.ROOT,
            "note:%s %s:*%s*",
            ankiSearchToken(settings.noteMapping.noteTypeName),
            ankiSearchToken(settings.noteMapping.expressionField),
            ankiSearchValue(kanji),
        )

        private fun ankiSearchToken(value: String): String {
            val safe = ankiSearchValue(value.trim())
            return if (safe.matches(Regex("[A-Za-z0-9_\\-]+"))) {
                safe
            } else {
                "\"$safe\""
            }
        }

        private fun ankiSearchValue(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
