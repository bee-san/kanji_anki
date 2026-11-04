package dev.bee.fsrs

internal class DefaultFsrsEngine(
    parameters: FsrsParameters?,
) : FsrsEngine {
    private val parameters: FsrsParameters = Fsrs.requireNonNull(parameters, "parameters")

    override fun initialState(firstRating: FsrsRating?): FsrsMemoryState {
        val rating = Fsrs.requireNonNull(firstRating, "firstRating")
        return FsrsMemoryState(initialStability(rating), initialDifficulty(rating, clamp = true))
    }

    override fun retrievability(state: FsrsMemoryState?, elapsedDays: Int): Double {
        val resolvedState = Fsrs.requireNonNull(state, "state")
        Fsrs.validateElapsedDays(elapsedDays)
        val stability = resolvedState.stability.coerceAtLeast(Fsrs.STABILITY_MIN)
        return Math.pow(1.0 + parameters.factor() * elapsedDays / stability, parameters.decay())
    }

    override fun nextState(
        previousState: FsrsMemoryState?,
        rating: FsrsRating?,
        elapsedDays: Int,
    ): FsrsMemoryState {
        val resolvedState = Fsrs.requireNonNull(previousState, "previousState")
        val resolvedRating = Fsrs.requireNonNull(rating, PARAM_RATING)
        Fsrs.validateElapsedDays(elapsedDays)
        validateDifficulty(resolvedState.difficulty)

        val retrievability = retrievability(resolvedState, elapsedDays)
        val nextDifficulty = nextDifficulty(resolvedState.difficulty, resolvedRating)
        val nextStability = when {
            // py-fsrs v6.3.1 routes every same-day review, including Again,
            // through the short-term stability update; forget stability is
            // only used for reviews at least one day out.
            elapsedDays == 0 -> shortTermStability(resolvedState.stability, resolvedRating)
            resolvedRating == FsrsRating.AGAIN -> nextForgetStability(
                nextDifficulty,
                resolvedState.stability,
                retrievability,
            )

            else -> nextRecallStability(nextDifficulty, resolvedState.stability, retrievability, resolvedRating)
        }
        return FsrsMemoryState(nextStability, nextDifficulty)
    }

    override fun nextDifficulty(currentDifficulty: Double, rating: FsrsRating?): Double {
        validateDifficulty(currentDifficulty)
        val resolvedRating = Fsrs.requireNonNull(rating, PARAM_RATING)
        val deltaDifficulty = -(parameters.difficultyDeltaScale() * (resolvedRating.value() - 3.0))
        val linearDamping = (10.0 - currentDifficulty) * deltaDifficulty / 9.0
        val easyInitialDifficulty = initialDifficulty(FsrsRating.EASY, clamp = false)
        val meanReversion = parameters.difficultyMeanReversionWeight()
        val next = meanReversion * easyInitialDifficulty +
            (1.0 - meanReversion) * (currentDifficulty + linearDamping)
        return Fsrs.clamp(next, Fsrs.MIN_DIFFICULTY, Fsrs.MAX_DIFFICULTY)
    }

    override fun shortTermStability(stability: Double, rating: FsrsRating?): Double {
        require(stability.isFinite() && stability > 0.0) { "stability must be finite and positive" }
        val resolvedRating = Fsrs.requireNonNull(rating, PARAM_RATING)
        var increase = Math.exp(parameters.shortTermBase() * (resolvedRating.value() - 3.0 + parameters.shortTermRatingOffset())) *
            Math.pow(stability, -parameters.shortTermStabilityDecay())
        if (resolvedRating == FsrsRating.GOOD || resolvedRating == FsrsRating.EASY) {
            increase = increase.coerceAtLeast(1.0)
        }
        return (stability * increase).coerceAtLeast(Fsrs.STABILITY_MIN)
    }

    override fun nextIntervalDays(stability: Double, desiredRetention: Double, maximumInterval: Int): Int {
        require(stability.isFinite() && stability > 0.0) { "stability must be finite and positive" }
        Fsrs.validateDesiredRetention(desiredRetention)
        Fsrs.validateMaximumInterval(maximumInterval)
        val interval = (stability / parameters.factor()) *
            (Math.pow(desiredRetention, 1.0 / parameters.decay()) - 1.0)
        // Clamp in Long before narrowing so extreme stabilities cannot wrap
        // Int and collapse to the one-day minimum.
        return Math.round(interval).coerceIn(1L, maximumInterval.toLong()).toInt()
    }

    override fun review(input: FsrsReviewInput?): FsrsReviewOutput {
        val resolvedInput = Fsrs.requireNonNull(input, "input")
        val previousState = Fsrs.requireNonNull(resolvedInput.previousState, "previousState")
        val rating = Fsrs.requireNonNull(resolvedInput.rating, "rating")
        val retrievability = retrievability(previousState, resolvedInput.elapsedDays)
        val nextState = nextState(previousState, rating, resolvedInput.elapsedDays)
        val interval = nextIntervalDays(nextState.stability, resolvedInput.desiredRetention, resolvedInput.maximumInterval)
        return FsrsReviewOutput(nextState, retrievability, interval)
    }

    private fun initialStability(rating: FsrsRating): Double =
        parameters.initialStability(rating).coerceAtLeast(Fsrs.STABILITY_MIN)

    private fun initialDifficulty(rating: FsrsRating, clamp: Boolean): Double {
        val difficulty = parameters.initialDifficultyBase() -
            Math.exp(parameters.initialDifficultyExponent() * (rating.value() - 1.0)) +
            1.0
        return if (clamp) {
            Fsrs.clamp(difficulty, Fsrs.MIN_DIFFICULTY, Fsrs.MAX_DIFFICULTY)
        } else {
            difficulty
        }
    }

    private fun nextRecallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: FsrsRating,
    ): Double {
        val hardPenalty = if (rating == FsrsRating.HARD) parameters.hardPenalty() else 1.0
        val easyBonus = if (rating == FsrsRating.EASY) parameters.easyBonus() else 1.0
        val next = stability * (
            1.0 +
                Math.exp(parameters.recallStabilityBase()) *
                (11.0 - difficulty) *
                Math.pow(stability, -parameters.recallStabilityStabilityDecay()) *
                (Math.exp((1.0 - retrievability) * parameters.recallStabilityRetrievabilitySensitivity()) - 1.0) *
                hardPenalty *
                easyBonus
            )
        return next.coerceAtLeast(Fsrs.STABILITY_MIN)
    }

    private fun nextForgetStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
    ): Double {
        val longTermForget = parameters.forgetStabilityBase() *
            Math.pow(difficulty, -parameters.forgetStabilityDifficultyDecay()) *
            (Math.pow(stability + 1.0, parameters.forgetStabilityStabilityGrowth()) - 1.0) *
            Math.exp((1.0 - retrievability) * parameters.forgetStabilityRetrievabilitySensitivity())
        val shortTermForgetCap = stability / Math.exp(parameters.shortTermBase() * parameters.shortTermRatingOffset())
        return longTermForget.coerceAtMost(shortTermForgetCap).coerceAtLeast(Fsrs.STABILITY_MIN)
    }

    private companion object {
        private const val PARAM_RATING = "rating"

        private fun validateDifficulty(difficulty: Double) {
            require(
                difficulty.isFinite() &&
                    difficulty >= Fsrs.MIN_DIFFICULTY &&
                    difficulty <= Fsrs.MAX_DIFFICULTY
            ) {
                "difficulty must be finite and in [1, 10]"
            }
        }
    }
}
