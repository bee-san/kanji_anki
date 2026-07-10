package dev.bee.kanjianki.core

import java.util.Collections
import kotlin.math.max
import kotlin.math.min

/** Public helpers for ladder movement and rung availability. */
object StudyLadderRules {
    const val DAY: Long = 86_400_000L

    const val MINUTE: Long = 60_000L

    /**
     * Anki's "learn ahead" horizon (default 20 minutes). When the only work
     * left in an active run is that run's own learning-step repeats, the
     * session keeps serving them up to this far in the future instead of
     * ending — 20 minutes covers the default `1m/10m` learning steps entirely,
     * so a session ends only once every served card has graduated past its
     * steps. This widens the effective horizon ONLY for same-session learning
     * repeats; ordinary queue building keeps the user's configured
     * `study_ahead_minutes`. Serving a repeat early still schedules
     * `now + step` at the real answer time, so no FSRS timing changes.
     */
    const val LEARN_AHEAD_MILLIS: Long = 20L * MINUTE

    const val MIN_RECOGNITION_STAGE: Int = -1
    const val MAX_RECOGNITION_STAGE: Int = 2
    const val STATE_NEW: String = "new"
    const val STATE_LEARNING: String = "learning"
    const val STATE_REVIEW: String = "review"
    const val STATE_RETIRED: String = "retired"

    @JvmStatic
    fun promoteRung(
        current: RecordsBase.LadderRung,
        availability: RecordsBase.RungAvailability,
    ): RecordsBase.LadderRung {
        return promoteRung(current, availability, RecordsBase.StudyLadderSettings.defaults())
    }

    @JvmStatic
    fun promoteRung(
        current: RecordsBase.LadderRung,
        availability: RecordsBase.RungAvailability,
        ladder: RecordsBase.StudyLadderSettings?,
    ): RecordsBase.LadderRung {
        return safeLadder(ladder).nextRung(current, availability)
    }

    @JvmStatic
    fun demoteRung(
        current: RecordsBase.LadderRung,
        availability: RecordsBase.RungAvailability,
    ): RecordsBase.LadderRung {
        return demoteRung(current, availability, RecordsBase.StudyLadderSettings.defaults())
    }

    @JvmStatic
    fun demoteRung(
        current: RecordsBase.LadderRung,
        availability: RecordsBase.RungAvailability,
        ladder: RecordsBase.StudyLadderSettings?,
    ): RecordsBase.LadderRung {
        return safeLadder(ladder).previousRung(current, availability)
    }

    @JvmStatic
    fun rungsForItem(item: RecordsStudyModels.StudyItem): List<RecordsBase.LadderRung> {
        return rungsForItem(item, RecordsBase.StudyLadderSettings.defaults())
    }

    @JvmStatic
    fun rungsForItem(
        item: RecordsStudyModels.StudyItem,
        ladder: RecordsBase.StudyLadderSettings?,
    ): List<RecordsBase.LadderRung> {
        val out = ArrayList<RecordsBase.LadderRung>()
        val safeLadder = safeLadder(ladder)
        val availability = item.rungAvailability()
        for (rung in safeLadder.orderedRungs) {
            if (safeLadder.isValidForItem(rung, availability)) {
                out.add(rung)
            }
        }
        return Collections.unmodifiableList(out)
    }

    @JvmStatic
    fun safeLadder(ladder: RecordsBase.StudyLadderSettings?): RecordsBase.StudyLadderSettings {
        return ladder ?: RecordsBase.StudyLadderSettings.defaults()
    }

    @JvmStatic
    fun alignRungToLadder(
        item: RecordsStudyModels.StudyItem,
        ladder: RecordsBase.StudyLadderSettings?,
    ): RecordsStudyModels.StudyItem {
        val effective = safeLadder(ladder).effectiveRung(item.rung, item.rungAvailability())
        return if (effective == item.rung) item else item.withRung(effective)
    }

    @JvmStatic
    fun stepDelayMillis(minutes: Int): Long {
        return max(1L, max(1, minutes).toLong()) * MINUTE
    }

    @JvmStatic
    fun clampStudyAheadMillis(studyAheadMillis: Long): Long {
        if (studyAheadMillis <= 0L) {
            return 0L
        }
        return min(studyAheadMillis, SettingsInputRules.MAX_STUDY_AHEAD_MINUTES.toLong() * MINUTE)
    }

    @JvmStatic
    fun rungToLegacyStage(rung: RecordsBase.LadderRung): Int {
        return when (rung) {
            RecordsBase.LadderRung.TYPE_MEANING -> MIN_RECOGNITION_STAGE
            RecordsBase.LadderRung.FONT_MEANING -> 1
            RecordsBase.LadderRung.WORD_READING -> MAX_RECOGNITION_STAGE
            else -> 0
        }
    }
}
