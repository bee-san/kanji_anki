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
