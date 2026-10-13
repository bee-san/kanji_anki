package dev.bee.fsrs

/**
 * Shared constants and validation helpers for FSRS-7 calculations.
 *
 * Separate from [Fsrs] because FSRS-7 is a different algorithm with a different
 * stability floor, not a retuning of FSRS-6. Sharing the constant would silently
 * apply FSRS-6's floor of 0.001 to an algorithm upstream runs at 0.0001, which is
 * exactly the kind of quiet substitution the reference fixture exists to catch.
 */
object Fsrs7 {
    /**
     * Minimum stability, in days.
     *
     * 0.0001 rather than FSRS-6's 0.001: upstream's `config.py` sets `s_min` from
     * the `--secs` flag, and FSRS-7's own docstring says it "is intended to be
     * always be used with `--short --secs`". Sub-day scheduling is the point of
     * the revision, so the tighter floor is part of the algorithm rather than a
     * tuning choice.
     */
    const val STABILITY_MIN: Double = 0.0001

    /** Maximum stability, in days. One hundred years, as upstream. */
    const val STABILITY_MAX: Double = 36_500.0

    const val MIN_DIFFICULTY: Double = 1.0
    const val MAX_DIFFICULTY: Double = 10.0

    /**
     * Elapsed time is a fractional day count in FSRS-7, not a whole number.
     *
     * This is the visible break from FSRS-6, whose intervals were integer days.
     * A same-day review is a real elapsed duration here — ten minutes is 0.00694
     * days — and the forgetting curve gives it a meaningful retrievability
     * instead of collapsing every same-day review onto t = 0.
     */
    @JvmStatic
    fun validateElapsedDays(elapsedDays: Double) {
        require(elapsedDays.isFinite() && elapsedDays >= 0.0) {
            "elapsedDays must be finite and non-negative"
        }
    }

    @JvmStatic
    fun validateDesiredRetention(desiredRetention: Double) {
        require(desiredRetention.isFinite() && desiredRetention > 0.0 && desiredRetention < 1.0) {
            "desiredRetention must be finite and in (0, 1)"
        }
    }

    @JvmStatic
    fun validateMaximumInterval(maximumIntervalDays: Double) {
        require(maximumIntervalDays.isFinite() && maximumIntervalDays > 0.0) {
            "maximumIntervalDays must be finite and positive"
        }
    }

    @JvmStatic
    fun validateStability(stability: Double) {
        require(stability.isFinite() && stability > 0.0) { "stability must be finite and positive" }
    }
}
