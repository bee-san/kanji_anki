package dev.bee.fsrs;

/**
 * Metadata for the algorithm snapshot implemented by this package.
 */
public final class FsrsAlgorithmInfo {
    public static final String UPSTREAM_REPOSITORY = "open-spaced-repetition/py-fsrs";
    public static final String UPSTREAM_RELEASE = "v6.3.1";
    public static final String UPSTREAM_COMMIT = "3abe686e9c058d3f3c00bbeb92e68b71211b2b31";
    public static final String UPSTREAM_SCHEDULER_BLOB = "6d42ecb259bbaaa02101f13c5e1b2ec7cdc77eae";
    public static final String ALGORITHM_LABEL = "FSRS-6.x 21-parameter snapshot";
    public static final int PARAMETER_COUNT = 21;

    private FsrsAlgorithmInfo() {
    }

    public static String upstreamReference() {
        return UPSTREAM_REPOSITORY + " " + UPSTREAM_RELEASE + " " + UPSTREAM_COMMIT;
    }
}
