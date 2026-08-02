package dev.bee.kanjianki.buildlogic

/** Android's documented upper bound for an application's versionCode. */
const val ANDROID_MAX_VERSION_CODE: Int = 2_100_000_000

private const val VERSION_COMPONENT_MAX = 999L
private const val MAJOR_MULTIPLIER = 1_000_000L
private const val MINOR_MULTIPLIER = 1_000L
private const val CANONICAL_COMPONENT_PATTERN = "(0|[1-9][0-9]*)"
private val RELEASE_VERSION_PATTERN = Regex(
    "^v?$CANONICAL_COMPONENT_PATTERN\\.$CANONICAL_COMPONENT_PATTERN\\.$CANONICAL_COMPONENT_PATTERN(?:-(beta))?$",
)

data class KaniVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val beta: Boolean = false,
) {
    val versionName: String = "$major.$minor.$patch" + if (beta) "-beta" else ""

    val versionCode: Int = run {
        val calculated = major.toLong() * MAJOR_MULTIPLIER + minor.toLong() * MINOR_MULTIPLIER + patch
        require(calculated in 1..ANDROID_MAX_VERSION_CODE.toLong()) {
            "Calculated versionCode $calculated is outside Android's supported range 1..$ANDROID_MAX_VERSION_CODE"
        }
        calculated.toInt()
    }
}

enum class KaniVersionSource(val machineName: String) {
    RELEASE_TAG("release-tag"),
    OVERRIDE("override"),
    GIT_TAG("git-tag"),
    CATALOG_FALLBACK("catalog-fallback"),
}

data class ResolvedKaniVersion(
    val version: KaniVersion,
    val source: KaniVersionSource,
)

object KaniVersioning {
    /**
     * Parses the release wire format used by GitHub tags and Android metadata.
     * A leading `v` is accepted at the boundary but is never included in versionName.
     * Automatic beta releases use the single canonical SemVer suffix `-beta`.
     */
    fun parse(value: String): KaniVersion {
        val normalized = value.trim()
        val match = RELEASE_VERSION_PATTERN.matchEntire(normalized)
            ?: throw IllegalArgumentException(
                "Version '$value' must be MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-beta with an optional leading v",
            )
        val components = match.groupValues.slice(1..3).mapIndexed { index, component ->
            val componentName = listOf("major", "minor", "patch")[index]
            val parsed = component.toLongOrNull()
                ?: throw IllegalArgumentException("Version $componentName component '$component' is not an integer")
            require(parsed <= VERSION_COMPONENT_MAX) {
                "Version $componentName component $parsed exceeds the supported maximum $VERSION_COMPONENT_MAX"
            }
            parsed.toInt()
        }
        return KaniVersion(
            components[0],
            components[1],
            components[2],
            beta = match.groupValues[4] == "beta",
        )
    }

    /**
     * Resolves build metadata in release order: explicit release tag, current/legacy
     * overrides, latest reachable Git tag, then the checked-in catalog fallback.
     * Explicit version codes remain supported but must match the canonical calculation.
     */
    fun resolve(
        releaseTag: String?,
        versionNameOverride: String?,
        versionCodeOverride: String?,
        latestReachableGitTag: () -> String?,
        fallbackVersionName: String,
        fallbackVersionCode: String,
    ): ResolvedKaniVersion {
        val explicitTag = releaseTag.nonBlank()
        val explicitName = versionNameOverride.nonBlank()
        val explicitCode = versionCodeOverride.nonBlank()

        val (version, source) = when {
            explicitTag != null -> parse(explicitTag) to KaniVersionSource.RELEASE_TAG
            explicitName != null -> parse(explicitName) to KaniVersionSource.OVERRIDE
            else -> {
                val gitTag = latestReachableGitTag().nonBlank()
                if (gitTag != null) {
                    parse(gitTag) to if (explicitCode == null) {
                        KaniVersionSource.GIT_TAG
                    } else {
                        KaniVersionSource.OVERRIDE
                    }
                } else {
                    parse(fallbackVersionName) to if (explicitCode == null) {
                        KaniVersionSource.CATALOG_FALLBACK
                    } else {
                        KaniVersionSource.OVERRIDE
                    }
                }
            }
        }

        if (explicitTag != null && explicitName != null) {
            val overriddenVersion = parse(explicitName)
            require(overriddenVersion == version) {
                "kaniReleaseTag resolves to ${version.versionName}, but KANI_VERSION_NAME resolves to ${overriddenVersion.versionName}"
            }
        }

        val expectedCode = version.versionCode
        val suppliedCode = explicitCode ?: if (source == KaniVersionSource.CATALOG_FALLBACK) {
            fallbackVersionCode
        } else {
            null
        }
        if (suppliedCode != null) {
            val parsedCode = parseVersionCode(suppliedCode)
            require(parsedCode == expectedCode) {
                "Version ${version.versionName} requires versionCode $expectedCode, but $parsedCode was supplied"
            }
        }

        return ResolvedKaniVersion(version, source)
    }

    fun parseVersionCode(value: String): Int {
        val parsed = value.trim().toLongOrNull()
            ?: throw IllegalArgumentException("versionCode '$value' must be an integer")
        require(parsed in 1..ANDROID_MAX_VERSION_CODE.toLong()) {
            "versionCode $parsed is outside Android's supported range 1..$ANDROID_MAX_VERSION_CODE"
        }
        return parsed.toInt()
    }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf(String::isNotEmpty)
}
