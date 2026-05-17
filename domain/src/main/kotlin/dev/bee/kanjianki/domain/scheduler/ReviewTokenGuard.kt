package dev.bee.kanjianki.domain.scheduler

class ReviewTokenGuard {
    fun evaluate(input: ReviewTokenGuardInput): ReviewTokenGuardResult {
        val requestToken = input.requestToken.orEmpty()
        if (input.consumedTokens.contains(requestToken)) {
            return ReviewTokenGuardResult(
                accepted = false,
                reason = ReviewTokenRejectionReason.ALREADY_CONSUMED,
                consumedTokens = input.consumedTokens,
                message = "Review token already consumed.",
            )
        }

        val activeToken = input.activeToken.orEmpty()
        if (activeToken.isNotEmpty() && activeToken != requestToken) {
            return ReviewTokenGuardResult(
                accepted = false,
                reason = ReviewTokenRejectionReason.ACTIVE_SESSION_MISMATCH,
                consumedTokens = input.consumedTokens,
                message = "Review token does not match the active session.",
            )
        }

        return ReviewTokenGuardResult(
            accepted = true,
            reason = null,
            consumedTokens = input.consumedTokens + requestToken,
            message = "Review token accepted.",
        )
    }
}

data class ReviewTokenGuardInput(
    val requestToken: String?,
    val activeToken: String?,
    val consumedTokens: Set<String> = emptySet(),
)

data class ReviewTokenGuardResult(
    val accepted: Boolean,
    val reason: ReviewTokenRejectionReason?,
    val consumedTokens: Set<String>,
    val message: String,
)

enum class ReviewTokenRejectionReason {
    ALREADY_CONSUMED,
    ACTIVE_SESSION_MISMATCH,
}
