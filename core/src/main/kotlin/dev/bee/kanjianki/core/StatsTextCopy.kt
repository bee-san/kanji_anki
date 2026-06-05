package dev.bee.kanjianki.core

import java.util.Locale

object StatsTextCopy {
    @JvmStatic
    fun verdictWorking(weakKanjiImproved: Int, matureSupportGained: Int): Boolean {
        return weakKanjiImproved > 0 || matureSupportGained > 0
    }

    @JvmStatic
    fun verdictHasLadder(totalActiveItems: Int): Boolean {
        return totalActiveItems > 0
    }

    @JvmStatic
    fun verdictTitle(working: Boolean): String {
        return if (working) "Kani is working for you" else "Waiting for Kani evidence"
    }

    @JvmStatic
    fun verdictBody(
        hasStats: Boolean,
        working: Boolean,
        hasLadder: Boolean,
        weakKanjiImproved: Int,
        matureSupportGained: Int,
        promotionReadyCount: Int,
        demotionRiskCount: Int,
        totalActiveItems: Int,
    ): String {
        if (!hasStats) {
            return "Study and sync to unlock trends."
        }
        if (working) {
            return workingVerdictBody(
                weakKanjiImproved,
                matureSupportGained,
                promotionReadyCount,
                demotionRiskCount,
            )
        }
        if (hasLadder) {
            return "Tracking " +
                StudyTextCopy.countText(totalActiveItems, "active kanji", "active kanji") +
                ". Trends appear after reviews and sync."
        }
        return "Review and sync to compare before and after."
    }

    @JvmStatic
    fun ladderHealthBody(
        totalActiveItems: Int,
        promotionReadyCount: Int,
        demotionRiskCount: Int,
        demotionReadyCount: Int,
        ladderPromotionIntervalDays: Int,
        ladderDemotionFailStreak: Int,
    ): String {
        if (totalActiveItems == 0) {
            return "No active ladder items yet. Sync or study weak kanji to fill the ladder."
        }
        var body = StudyTextCopy.countText(
            promotionReadyCount,
            "ready to climb",
            "ready to climb",
        ) +
            " · " +
            StudyTextCopy.countText(
                demotionRiskCount,
                "at risk",
                "at risk",
            )
        if (demotionReadyCount > 0) {
            body += " · " +
                StudyTextCopy.countText(
                    demotionReadyCount,
                    "ready to fall",
                    "ready to fall",
                )
        }
        return body +
            ". Rules: climb after more than " +
            ladderPromotionIntervalDays +
            " days; fall after " +
            ladderDemotionFailStreak +
            " misses."
    }

    @JvmStatic
    fun ladderDistributionRow(rung: RecordsBase.LadderRung, count: Int): String {
        return ladderRungLabel(rung) + ": " + count
    }

    @JvmStatic
    fun ladderRungLabel(rung: RecordsBase.LadderRung): String {
        return when (rung) {
            RecordsBase.LadderRung.WRITE_KANJI -> "Write kanji"
            RecordsBase.LadderRung.TYPE_MEANING -> "Type meaning"
            RecordsBase.LadderRung.SIMILAR_KANJI -> "Similar kanji"
            RecordsBase.LadderRung.MEANING_KANJI -> "Meaning kanji"
            RecordsBase.LadderRung.KANJI_MEANING -> "Kanji meaning"
            RecordsBase.LadderRung.FONT_MEANING -> "Font meaning"
            RecordsBase.LadderRung.WORD_READING -> "Word reading"
        }
    }

    @JvmStatic
    fun weaknessImprovementBody(
        improvedCount: Int,
        averageBeforeWeakness: Double,
        averageAfterWeakness: Double,
    ): String {
        if (improvedCount == 0) {
            return "Weakness trends appear after reviews and sync."
        }
        return "Average weakness: " +
            formatWeakness(averageBeforeWeakness) +
            " -> " +
            formatWeakness(averageAfterWeakness) +
            "."
    }

    @JvmStatic
    fun weaknessImprovementExample(kanji: String?, beforeWeakness: Double, afterWeakness: Double): String {
        return clean(kanji) + "  " + formatWeakness(beforeWeakness) + " -> " + formatWeakness(afterWeakness)
    }

    @JvmStatic
    fun supportGainExample(kanji: String?, beforeMatureSupport: Int, afterMatureSupport: Int): String {
        return clean(kanji) + "  " + beforeMatureSupport + " -> " + afterMatureSupport + " mature cards"
    }

    @JvmStatic
    fun notHelpingRowText(
        kanji: String?,
        reviewCount: Int,
        sameCardCount: Int,
        retentionDelta: Double,
        difficultyDelta: Double,
    ): String {
        return clean(kanji) +
            "  " +
            reviewCount +
            " Kani reviews · " +
            sameCardCount +
            " same-card checks · retention " +
            formatSignedPercent(retentionDelta) +
            " · difficulty " +
            formatSignedDecimal(difficultyDelta)
    }

    @JvmStatic
    fun notHelpingBody(noImpactEvidence: Boolean, hasNotHelpingRows: Boolean): String {
        if (noImpactEvidence) {
            return "Review and sync to compare before and after."
        }
        if (!hasNotHelpingRows) {
            return "No kanji need attention right now."
        }
        return "Shown after enough reviews and sync."
    }

    @JvmStatic
    fun formatWeakness(weakness: Double): String {
        return String.format(Locale.ROOT, "%.2f", weakness)
    }

    @JvmStatic
    fun formatSignedPercent(value: Double): String {
        return String.format(Locale.ROOT, "%+.0f%%", value * 100.0)
    }

    @JvmStatic
    fun formatSignedDecimal(value: Double): String {
        return String.format(Locale.ROOT, "%+.1f", value)
    }

    @JvmStatic
    fun formatStudyTime(millis: Long): String {
        val seconds = maxOf(0L, Math.round(millis / 1000.0))
        if (seconds < 60L) {
            return "$seconds sec"
        }
        val minutes = seconds / 60L
        val remainingSeconds = seconds % 60L
        if (minutes < 60L) {
            return if (remainingSeconds == 0L) "$minutes min" else "$minutes min $remainingSeconds sec"
        }
        val hours = minutes / 60L
        val remainingMinutes = minutes % 60L
        return if (remainingMinutes == 0L) "$hours hr" else "$hours hr $remainingMinutes min"
    }

    private fun workingVerdictBody(
        weakKanjiImproved: Int,
        matureSupportGained: Int,
        promotionReadyCount: Int,
        demotionRiskCount: Int,
    ): String {
        val signals = mutableListOf<String>()
        if (weakKanjiImproved > 0) {
            signals += StudyTextCopy.countText(
                weakKanjiImproved,
                "weak kanji improved",
                "weak kanji improved",
            )
        }
        if (matureSupportGained > 0) {
            signals += StudyTextCopy.countText(
                matureSupportGained,
                "mature card gained",
                "mature cards gained",
            )
        }
        if (promotionReadyCount > 0) {
            signals += StudyTextCopy.countText(
                promotionReadyCount,
                "review-phase item crossed the climb threshold",
                "review-phase items crossed the climb threshold",
            )
        }
        var body = signals.joinToString(". ") + "."
        if (demotionRiskCount > 0) {
            body += " Watch " +
                StudyTextCopy.countText(
                    demotionRiskCount,
                    "review-phase item with a miss streak",
                    "review-phase items with miss streaks",
                ) +
                "."
        }
        return body
    }

    private fun clean(value: String?): String {
        return value ?: ""
    }
}
