package dev.bee.kanjianki.data

/** Aborts the owning review transaction and maps the result to STALE. */
internal class StaleReviewCommitException : RuntimeException()
