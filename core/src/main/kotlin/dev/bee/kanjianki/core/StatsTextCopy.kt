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
        return if (working) "Kani is working for you" else "Kani is not currently working for you"
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
            return "No Kani evidence is available yet. Study weak kanji, then sync AnkiDroid so this page can compare before and after."
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
            return "Kani is tracking " +
                StudyTextCopy.countText(totalActiveItems, "active kanji", "active kanji") +
                ", but no weakness burn-down or mature Anki support conversion has landed yet. Study due reviews, then sync AnkiDroid."
        }
        return "No before-and-after evidence yet. Do Kani reviews, then sync AnkiDroid so this page can compare weak kanji and mature support."
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
            return "No active ladder items yet. Sync AnkiDroid or study imported weak kanji to fill the ladder."
        }
        var body = StudyTextCopy.countText(
            promotionReadyCount,
            "FSRS-mature review item",
            "FSRS-mature review items",
        ) +
            " · " +
            StudyTextCopy.countText(
                demotionRiskCount,
                "demotion-risk review item",
                "demotion-risk review items",
            )
        if (demotionReadyCount > 0) {
            body += " · " +
                StudyTextCopy.countText(
                    demotionReadyCount,
                    "at the demotion threshold",
                    "at the demotion threshold",
                )
        }
        return body +
            ". Thresholds: climb when FSRS schedules more than " +
            ladderPromotionIntervalDays +
            " days; demote after " +
            ladderDemotionFailStreak +
            " real due-review fails."
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
            return "Weakness improvements will show after Kani reviews are followed by a successful AnkiDroid sync."
        }
        return "Average weakness: " +
            formatWeakness(averageBeforeWeakness) +
            " -> " +
            formatWeakness(averageAfterWeakness) +
            " after Kani practice."
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
            return "No Kani impact evidence yet. Review in Kani, then sync AnkiDroid so this page can compare before and after."
        }
        if (!hasNotHelpingRows) {
            return "No sufficiently proven not-helping kanji right now. Sparse cases stay out of this list until Kani has enough reviews and synced Anki evidence."
        }
        return "Only kanji with at least 3 Kani reviews, 2 current Anki cards, and same-card before/after evidence appear here."
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
                "weak kanji is burning down",
                "weak kanji are burning down",
            )
        }
        if (matureSupportGained > 0) {
            signals += StudyTextCopy.countText(
                matureSupportGained,
                "mature Anki card has been gained",
                "mature Anki cards have been gained",
            )
        }
        if (promotionReadyCount > 0) {
            signals += StudyTextCopy.countText(
                promotionReadyCount,
                "review-phase item crossed the FSRS climb threshold",
                "review-phase items crossed the FSRS climb threshold",
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
