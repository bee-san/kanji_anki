package dev.bee.fsrs

/**
 * Metadata for the algorithm snapshot implemented by this package.
 */
object FsrsAlgorithmInfo {
    const val UPSTREAM_REPOSITORY: String = "open-spaced-repetition/py-fsrs"
    const val UPSTREAM_RELEASE: String = "v6.3.1"
    const val UPSTREAM_COMMIT: String = "3abe686e9c058d3f3c00bbeb92e68b71211b2b31"
    const val UPSTREAM_SCHEDULER_BLOB: String = "6d42ecb259bbaaa02101f13c5e1b2ec7cdc77eae"
    const val ALGORITHM_LABEL: String = "FSRS-6.x 21-parameter snapshot"
    const val PARAMETER_COUNT: Int = 21

    @JvmStatic
    fun upstreamReference(): String =
        "$UPSTREAM_REPOSITORY $UPSTREAM_RELEASE $UPSTREAM_COMMIT"
}
