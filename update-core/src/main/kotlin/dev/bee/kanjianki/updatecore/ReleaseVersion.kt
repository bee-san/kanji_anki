package dev.bee.kanjianki.updatecore

object ReleaseVersion {
    private const val SEMVER_COMPONENTS = 3
    private val VERSION_PATTERN = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$")

    @JvmStatic
    fun isNewerSemver(currentVersion: String?, tagName: String?): Boolean {
        val current = parseVersion(stripLeadingV(currentVersion))
        val remote = parseVersion(stripLeadingV(tagName))
        for (index in 0 until SEMVER_COMPONENTS) {
            if (remote[index] != current[index]) {
                return remote[index] > current[index]
            }
        }
        return false
    }

    /**
     * Returns true only when [tagName] is a usable strict `MAJOR.MINOR.PATCH`
     * release tag (optionally `v`-prefixed). A blank/garbage tag — e.g. the
     * empty `tag_name` a captive portal / intercepting proxy produces when it
     * answers the releases API with an HTTP 200 HTML interstitial — is not a
     * valid semver, so the caller can classify it as a connectivity failure
     * instead of collapsing it to `0.0.0` and reporting "already on version".
     */
    @JvmStatic
    fun isValidSemver(tagName: String?): Boolean {
        return VERSION_PATTERN.matches(stripLeadingV(tagName))
    }

    private fun stripLeadingV(version: String?): String {
        return version?.removePrefix("v").orEmpty()
    }

    private fun parseVersion(version: String): IntArray {
        val match = VERSION_PATTERN.matchEntire(version) ?: return intArrayOf(0, 0, 0)
        return intArrayOf(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].toInt(),
        )
    }
}
