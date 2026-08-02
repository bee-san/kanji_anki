package dev.bee.kanjianki.updatecore

object ReleaseVersion {
    private const val SEMVER_COMPONENTS = 3
    private val VERSION_PATTERN = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-(beta))?$")

    @JvmStatic
    fun isNewerSemver(currentVersion: String?, tagName: String?): Boolean {
        val current = parseVersion(stripLeadingV(currentVersion))
        val remote = parseVersion(stripLeadingV(tagName))
        for (index in 0 until SEMVER_COMPONENTS) {
            val comparison = compareNumericComponents(remote.components[index], current.components[index])
            if (comparison != 0) {
                return comparison > 0
            }
        }
        return current.beta && !remote.beta
    }

    /**
     * Returns true only when [tagName] is a usable `MAJOR.MINOR.PATCH` stable
     * tag or `MAJOR.MINOR.PATCH-beta` prerelease tag (optionally `v`-prefixed).
     * A blank/garbage tag — e.g. the
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

    private fun parseVersion(version: String): ParsedVersion {
        val match = VERSION_PATTERN.matchEntire(version)
            ?: return ParsedVersion(List(SEMVER_COMPONENTS) { "0" }, beta = false)
        val components = List(SEMVER_COMPONENTS) { index ->
            match.groupValues[index + 1].trimStart('0').ifEmpty { "0" }
        }
        return ParsedVersion(components, beta = match.groupValues[4] == "beta")
    }

    private fun compareNumericComponents(left: String, right: String): Int {
        if (left.length != right.length) {
            return left.length.compareTo(right.length)
        }
        return left.compareTo(right)
    }

    private data class ParsedVersion(
        val components: List<String>,
        val beta: Boolean,
    )
}
