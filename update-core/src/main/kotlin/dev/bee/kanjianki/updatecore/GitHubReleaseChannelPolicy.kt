package dev.bee.kanjianki.updatecore

/** Selects the GitHub release feed without weakening artifact verification. */
object GitHubReleaseChannelPolicy {
    private const val STABLE_RELEASE_PATH = "/releases/latest"
    private const val BETA_RELEASE_PATH = "/releases?per_page=30"

    @JvmStatic
    fun apiPath(betaEnabled: Boolean): String =
        if (betaEnabled) BETA_RELEASE_PATH else STABLE_RELEASE_PATH

    @JvmStatic
    fun parseRelease(betaEnabled: Boolean, json: String?): GitHubReleaseMetadata {
        val releaseJson = if (betaEnabled) {
            GitHubReleaseMetadataParser.objectValues(json.orEmpty())
                .firstOrNull(GitHubReleaseMetadataParser::isPrerelease)
                .orEmpty()
        } else {
            json.orEmpty()
        }
        return GitHubReleaseMetadataParser.parseLatest(releaseJson)
    }
}
