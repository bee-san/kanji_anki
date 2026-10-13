package dev.bee.fsrs

/**
 * Identity of the FSRS-7 snapshot implemented by [Fsrs7Engine].
 *
 * Pinned to a commit and a blob rather than a branch, because FSRS-7 lives in a
 * research repository with no released artifact: `srs-benchmark`'s `main` moves,
 * and "the FSRS-7 in srs-benchmark" is not a reproducible statement without a
 * hash. A consumer records these values with every stored schedule so a due date
 * computed today can still be explained after upstream changes the model.
 */
object Fsrs7AlgorithmInfo {
    const val UPSTREAM_REPOSITORY: String = "open-spaced-repetition/srs-benchmark"
    const val UPSTREAM_COMMIT: String = "70cc4387f573ff20b13ac9c106333a335c8a4cb8"

    /** Blob hash of `models/fsrs_v7.py` at [UPSTREAM_COMMIT]. */
    const val UPSTREAM_MODEL_BLOB: String = "33893c3fed0f7dbe28c2b55874a50d9b3fa77df5"

    const val UPSTREAM_MODEL_PATH: String = "models/fsrs_v7.py"

    const val ALGORITHM_LABEL: String = "FSRS-7 35-parameter snapshot"

    const val PARAMETER_COUNT: Int = 35

    @JvmStatic
    fun upstreamReference(): String =
        "$UPSTREAM_REPOSITORY $UPSTREAM_MODEL_PATH @ $UPSTREAM_COMMIT"
}
