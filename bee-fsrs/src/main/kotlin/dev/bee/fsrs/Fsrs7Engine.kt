package dev.bee.fsrs

/**
 * FSRS-7 memory mathematics.
 *
 * A port of `models/fsrs_v7.py` from
 * [`open-spaced-repetition/srs-benchmark`](https://github.com/open-spaced-repetition/srs-benchmark),
 * pinned in [Fsrs7AlgorithmInfo]. Three things differ from [FsrsEngine]'s FSRS-6,
 * and each one changes results rather than just parameter counts:
 *
 * 1. **The forgetting curve is two power laws**, blended by weights that depend on
 *    stability, replacing FSRS-6's single power law.
 * 2. **Elapsed time and intervals are fractional days.** FSRS-6 rounded to whole
 *    days, which forced every same-day review onto t = 0.
 * 3. **Long-term and short-term stability are blended continuously** by
 *    `1 - w[26] * exp(-w[25] * t)`, instead of switching on `elapsed < 1 day`.
 *
 * Like the FSRS-6 engine, this has no clock and no I/O: elapsed time is an input,
 * so a stored schedule stays reproducible.
 */
interface Fsrs7Engine {
    /** Memory state after a first review rated [firstRating]. */
    fun initialState(firstRating: FsrsRating?): FsrsMemoryState

    /** Probability of recall after [elapsedDays] fractional days. */
    fun retrievability(state: FsrsMemoryState?, elapsedDays: Double): Double

    fun nextDifficulty(currentDifficulty: Double, rating: FsrsRating?): Double

    fun nextState(previousState: FsrsMemoryState?, rating: FsrsRating?, elapsedDays: Double): FsrsMemoryState

    /**
     * Fractional days until retrievability falls to [desiredRetention].
     *
     * Fractional because FSRS-7 is designed for it, and because rounding here
     * would discard the sub-day scheduling the revision exists to provide.
     */
    fun nextIntervalDays(stability: Double, desiredRetention: Double, maximumIntervalDays: Double): Double

    fun review(input: Fsrs7ReviewInput?): Fsrs7ReviewOutput

    companion object {
        @JvmStatic
        fun create(parameters: Fsrs7Parameters?): Fsrs7Engine = DefaultFsrs7Engine(parameters)

        @JvmStatic
        fun latestDefault(): Fsrs7Engine = create(Fsrs7Parameters.latestDefault())
    }
}

internal class DefaultFsrs7Engine(
    parameters: Fsrs7Parameters?,
) : Fsrs7Engine {
    private val parameters: Fsrs7Parameters = Fsrs.requireNonNull(parameters, "parameters")

    override fun initialState(firstRating: FsrsRating?): FsrsMemoryState {
        val rating = Fsrs.requireNonNull(firstRating, PARAM_RATING)
        return FsrsMemoryState(
            clampStability(parameters.initialStability(rating)),
            clampDifficulty(initialDifficulty(rating)),
        )
    }

    override fun retrievability(state: FsrsMemoryState?, elapsedDays: Double): Double {
        val resolved = Fsrs.requireNonNull(state, "state")
        Fsrs7.validateElapsedDays(elapsedDays)
        return retrievability(clampStability(resolved.stability), elapsedDays)
    }

    /**
     * FSRS-7's mixed power-law forgetting curve.
     *
     * Each power law is written so that R equals its base at t = s: the factor
     * `base^(1/decay) - 1` is chosen to make that identity hold, which is what
     * makes the two bases interpretable as "retention after one stability period".
     * The blend weights depend on stability in opposite directions, so a
     * well-known item forgets on a different curve shape than a new one.
     */
    private fun retrievability(stability: Double, elapsedDays: Double): Double {
        val curve = parameters.forgettingCurve()
        val timeOverStability = elapsedDays / stability

        val first = powerLawRetention(curve.firstBase(), curve.firstDecay(), timeOverStability)
        val second = powerLawRetention(curve.secondBase(), curve.secondDecay(), timeOverStability)

        val firstWeight = curve.firstWeight() * Math.pow(stability, -curve.firstStabilityWeightPower())
        val secondWeight = curve.secondWeight() * Math.pow(stability, curve.secondStabilityWeightPower())

        val blended = (firstWeight * first + secondWeight * second) / (firstWeight + secondWeight)
        // Clamped because the caller-facing contract is a probability. The
        // mathematics stays inside [0, 1] for valid parameters; this guards the
        // last-bit overshoot that would otherwise fail a downstream range check.
        return Fsrs.clamp(blended, 0.0, 1.0)
    }

    private fun powerLawRetention(base: Double, decay: Double, timeOverStability: Double): Double {
        val factor = Math.pow(base, 1.0 / decay) - 1.0
        return Math.pow(1.0 + factor * timeOverStability, decay)
    }

    override fun nextDifficulty(currentDifficulty: Double, rating: FsrsRating?): Double {
        validateDifficulty(currentDifficulty)
        val resolvedRating = Fsrs.requireNonNull(rating, PARAM_RATING)

        val deltaDifficulty = -parameters.difficultyDeltaScale() * (resolvedRating.value() - 3.0)
        // Linear damping: a hard rating moves an already-hard item less than an
        // easy one, so difficulty approaches its ceiling asymptotically instead of
        // saturating after a few lapses.
        val damped = currentDifficulty + deltaDifficulty * (10.0 - currentDifficulty) / 9.0
        // 1% mean reversion toward the Easy-rated initial difficulty, so difficulty
        // drifts back rather than ratcheting permanently upward.
        val reverted = MEAN_REVERSION_WEIGHT * initialDifficulty(FsrsRating.EASY) +
            (1.0 - MEAN_REVERSION_WEIGHT) * damped
        return clampDifficulty(reverted)
    }

    override fun nextState(
        previousState: FsrsMemoryState?,
        rating: FsrsRating?,
        elapsedDays: Double,
    ): FsrsMemoryState {
        val resolved = Fsrs.requireNonNull(previousState, "previousState")
        val resolvedRating = Fsrs.requireNonNull(rating, PARAM_RATING)
        Fsrs7.validateElapsedDays(elapsedDays)
        validateDifficulty(resolved.difficulty)

        val stability = clampStability(resolved.stability)
        val difficulty = resolved.difficulty
        val retrievability = retrievability(stability, elapsedDays)

        // Both branches read the *previous* difficulty, not the updated one.
        // Upstream's step() computes new_d after new_s and never feeds it back,
        // and FSRS-6's Kotlin engine does the opposite — so this is a real
        // behavioural difference between the two engines, not a transcription
        // preference. The reference fixture is what pins it.
        val longTerm = branchStability(parameters.longTerm(), stability, difficulty, retrievability, resolvedRating)
        val shortTerm = branchStability(parameters.shortTerm(), stability, difficulty, retrievability, resolvedRating)

        // 0 at zero elapsed time (pure short-term) rising to 1 (pure long-term).
        // Continuous, so no review sits on a discontinuity the way FSRS-6's
        // one-day cutoff made a 23-hour and a 25-hour review incomparable.
        val coefficient = 1.0 - parameters.transitionWeight() *
            Math.exp(-parameters.transitionRate() * elapsedDays)
        val blended = coefficient * longTerm + (1.0 - coefficient) * shortTerm

        return FsrsMemoryState(
            clampStability(blended),
            nextDifficulty(difficulty, resolvedRating),
        )
    }

    /**
     * One stability branch, long-term or short-term.
     *
     * Written once and applied to both parameter offsets, because FSRS-7 uses the
     * same nine equations twice. Duplicating it per branch would let the two drift
     * during a later edit, and the fixture would only catch that if it happened to
     * cover the branch that drifted.
     */
    private fun branchStability(
        branch: Fsrs7Parameters.StabilityBranch,
        stability: Double,
        difficulty: Double,
        retrievability: Double,
        rating: FsrsRating,
    ): Double {
        // Stability after a lapse, capped at the current value: forgetting an item
        // must never make it more durable than it already was.
        val failure = Math.min(
            stability,
            branch.failureMultiplier() *
                Math.pow(difficulty, -branch.failureDifficultyExponent()) *
                (Math.pow(stability + 1.0, branch.failureStabilityExponent()) - 1.0) *
                Math.exp((1.0 - retrievability) * branch.failureRetrievabilityMultiplier()),
        )

        if (rating == FsrsRating.AGAIN) {
            return failure
        }

        val hardPenalty = if (rating == FsrsRating.HARD) branch.hardPenalty() else 1.0
        val easyBonus = if (rating == FsrsRating.EASY) branch.easyBonus() else 1.0
        val increase = 1.0 +
            Math.exp(branch.increaseBase() - INCREASE_BASE_OFFSET) *
            (11.0 - difficulty) *
            Math.pow(stability, -branch.increaseStabilityExponent()) *
            (Math.exp((1.0 - retrievability) * branch.increaseRetrievabilityMultiplier()) - 1.0) *
            hardPenalty *
            easyBonus

        // Upstream takes the maximum of the success and failure paths. Under this
        // engine's clamps that maximum provably cannot select `failure`: every
        // factor of `increase` is non-negative — retrievability is clamped into
        // [0, 1] so `exp((1 - R) * k) - 1 >= 0`, and difficulty into [1, 10] so
        // `11 - difficulty > 0` — hence `increase >= 1` and
        // `stability * increase >= stability >= failure`.
        //
        // Kept anyway, and deliberately not simplified away: it is upstream's
        // expression, and the argument above depends on the retrievability clamp
        // holding. If that clamp is ever loosened, dropping this would turn a
        // guarded case into a silent stability regression. A mutation test
        // confirms no fixture vector can distinguish the two, so the comment is
        // the only thing that can carry this.
        return Math.max(failure, stability * increase)
    }

    /**
     * Invert the forgetting curve for the interval at [desiredRetention].
     *
     * FSRS-7's curve has no closed-form inverse — that is the price of mixing two
     * power laws — so the root is bracketed and bisected in log space. Bisection
     * rather than the Newton iteration upstream's training code uses: Newton there
     * runs a fixed few steps inside an autograd graph and floors its result at one
     * second, which is fine for a penalty term but would quietly return the floor
     * instead of the answer here. A bracketed method cannot converge to the wrong
     * branch, and log space keeps the step size sane across the seven orders of
     * magnitude stability legitimately spans.
     */
    override fun nextIntervalDays(
        stability: Double,
        desiredRetention: Double,
        maximumIntervalDays: Double,
    ): Double {
        Fsrs7.validateStability(stability)
        Fsrs7.validateDesiredRetention(desiredRetention)
        Fsrs7.validateMaximumInterval(maximumIntervalDays)

        val clamped = clampStability(stability)

        // R is monotonically decreasing in t, so if it is still above target at the
        // cap the answer is the cap and there is no root to find in range.
        if (retrievability(clamped, maximumIntervalDays) >= desiredRetention) {
            return maximumIntervalDays
        }

        var low = INTERVAL_SEARCH_MIN
        if (retrievability(clamped, low) <= desiredRetention) {
            // Even the smallest representable interval is already below target.
            return low
        }
        var high = maximumIntervalDays

        // Geometric bisection: ~100 halvings of a log-space bracket spanning
        // 1e-9..36500 leaves a relative width far below the fixture's tolerance.
        repeat(INTERVAL_BISECTION_STEPS) {
            val middle = Math.sqrt(low * high)
            if (retrievability(clamped, middle) > desiredRetention) {
                low = middle
            } else {
                high = middle
            }
        }
        return Math.sqrt(low * high)
    }

    override fun review(input: Fsrs7ReviewInput?): Fsrs7ReviewOutput {
        val resolved = Fsrs.requireNonNull(input, "input")
        val previous = Fsrs.requireNonNull(resolved.previousState, "previousState")
        val rating = Fsrs.requireNonNull(resolved.rating, PARAM_RATING)

        val retrievability = retrievability(previous, resolved.elapsedDays)
        val next = nextState(previous, rating, resolved.elapsedDays)
        val interval = nextIntervalDays(
            next.stability,
            resolved.desiredRetention,
            resolved.maximumIntervalDays,
        )
        return Fsrs7ReviewOutput(next, retrievability, interval)
    }

    private fun initialDifficulty(rating: FsrsRating): Double =
        parameters.initialDifficultyBase() -
            Math.exp(parameters.initialDifficultyExponent() * (rating.value() - 1.0)) +
            1.0

    private fun clampStability(stability: Double): Double =
        Fsrs.clamp(stability, Fsrs7.STABILITY_MIN, Fsrs7.STABILITY_MAX)

    private fun clampDifficulty(difficulty: Double): Double =
        Fsrs.clamp(difficulty, Fsrs7.MIN_DIFFICULTY, Fsrs7.MAX_DIFFICULTY)

    private companion object {
        private const val PARAM_RATING = "rating"

        /** Upstream's `exp(w[base] - 1.5)`; the offset lives inside the exponent. */
        private const val INCREASE_BASE_OFFSET = 1.5

        /** Upstream's `0.01 * init + 0.99 * current`. */
        private const val MEAN_REVERSION_WEIGHT = 0.01

        /** Well below one second, so a real sub-day interval is never the floor. */
        private const val INTERVAL_SEARCH_MIN = 1.0e-9

        private const val INTERVAL_BISECTION_STEPS = 200

        private fun validateDifficulty(difficulty: Double) {
            require(
                difficulty.isFinite() &&
                    difficulty >= Fsrs7.MIN_DIFFICULTY &&
                    difficulty <= Fsrs7.MAX_DIFFICULTY,
            ) {
                "difficulty must be finite and in [1, 10]"
            }
        }
    }
}
