package dev.bee.kanjianki.buildlogic

import java.io.File

/**
 * The JDK whose runtime is shipped inside Kani's desktop packages (Goal 204).
 *
 * This is not the compiler's `jvmTarget` and it is not the JVM that runs Gradle. It is
 * the JDK that `jlink` strips down and `jpackage` copies into `Kani/lib/runtime`, so it
 * is the JVM the user actually runs — every class Kani loads, every TLS handshake, every
 * font rasterization. It is part of the release artifact rather than part of the build
 * environment, and it is pinned here for that reason.
 *
 * The reason it needs pinning is that Compose's packaging default is
 * `System.getProperty("java.home")` — the **Gradle daemon's** JVM. Neither
 * `jvmTarget = "17"` nor `java { toolchain { … } }` constrains it, because neither is
 * consulted. This was verified rather than assumed: building this repository with
 * `-Dorg.gradle.java.home=<a Temurin 21>` and no other change produced
 * `Kani/lib/runtime/release` reading `JAVA_VERSION="21.0.11"`. Nothing failed, nothing
 * warned. A release cut from a machine whose daemon had drifted would have shipped a
 * different JVM to users than the one that was tested, and the only evidence would have
 * been one line inside the installed image.
 *
 * Two further facts shape how this is enforced:
 *
 *  - **The packaged runtime carries no vendor identity.** The `jlink` image's `release`
 *    file has `JAVA_VERSION` and `MODULES` and nothing else — no `IMPLEMENTOR`, and
 *    nothing in `conf/` or `legal/` names a distribution either. So provenance cannot be
 *    read back out of the artifact; it can only be established by checking the JDK that
 *    built it, at the moment it builds it. That is what [verify] is for.
 *  - **Gradle's toolchain spec cannot express a patch version.** `languageVersion` is the
 *    feature version only, so a toolchain query for "Adoptium 17" is satisfied by any
 *    17.x. The feature version and vendor are therefore delegated to Gradle's toolchain
 *    resolution, and the exact patch is checked here against the resolved installation's
 *    own `release` file.
 *
 * [DISTRIBUTIONS] records the download URL and SHA-256 of each host's archive. Those are
 * evidence and an install source, not something this build fetches: Kani deliberately
 * does not enable Gradle toolchain auto-provisioning, because that would route the JDK
 * for the shipped runtime through a resolver service at build time. CI installs the
 * pinned archive explicitly (`actions/setup-java` with the exact
 * `17.0.19+10`), and this object then verifies that what got installed is what was
 * pinned. See `docs/desktop-packaging-jdk.md`.
 */
object KaniPackagingJdk {
    /** The feature version, which is all a Gradle toolchain spec can ask for. */
    const val FEATURE_VERSION: Int = 17

    /** `JAVA_VERSION` in the JDK's `release` file, and in the packaged image's. */
    const val JAVA_VERSION: String = "17.0.19"

    /** The upstream build number. Adoptium's version is `$JAVA_VERSION+$BUILD`. */
    const val BUILD: String = "10"

    /** `IMPLEMENTOR` in the JDK's `release` file. */
    const val IMPLEMENTOR: String = "Eclipse Adoptium"

    /** `IMPLEMENTOR_VERSION` in the JDK's `release` file: vendor and exact build. */
    const val IMPLEMENTOR_VERSION: String = "Temurin-$JAVA_VERSION+$BUILD"

    /** The human name of the pinned distribution, for messages and documentation. */
    const val DISTRIBUTION_NAME: String = "Eclipse Temurin"

    /**
     * A pinned per-host archive of the packaging JDK.
     *
     * @param os normalized as `linux`, `windows`, or `mac` — Adoptium's own names.
     * @param architecture normalized as `x64` or `aarch64`.
     */
    data class Distribution(
        val os: String,
        val architecture: String,
        val archiveName: String,
        val downloadUrl: String,
        val sha256: String,
    )

    /**
     * Every host Kani packages on, with the exact bytes of its packaging JDK.
     *
     * Windows `aarch64` is absent because Temurin 17 has no Windows ARM64 build — the
     * Adoptium API returns nothing for that combination. The Windows package is x64 and
     * runs under emulation on ARM64 Windows. That is a real limitation of the pinned
     * runtime rather than an oversight in this table, and
     * `KaniPackagingJdkTest.theWindowsArm64GapIsRecordedRatherThanImplied` holds it so
     * the gap cannot be quietly filled with a guessed URL.
     */
    val DISTRIBUTIONS: List<Distribution> = listOf(
        Distribution(
            os = "linux",
            architecture = "x64",
            archiveName = "OpenJDK17U-jdk_x64_linux_hotspot_17.0.19_10.tar.gz",
            downloadUrl = "https://github.com/adoptium/temurin17-binaries/releases/" +
                "download/jdk-17.0.19%2B10/" +
                "OpenJDK17U-jdk_x64_linux_hotspot_17.0.19_10.tar.gz",
            sha256 = "d8afc263758141a66e0e3aafc321e783f7016696f4eaea067d340a269037d331",
        ),
        Distribution(
            os = "linux",
            architecture = "aarch64",
            archiveName = "OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.19_10.tar.gz",
            downloadUrl = "https://github.com/adoptium/temurin17-binaries/releases/" +
                "download/jdk-17.0.19%2B10/" +
                "OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.19_10.tar.gz",
            sha256 = "83a52172678ec8975164648654869cb2e71d7c748b47aca94b29bbfa10c18e81",
        ),
        Distribution(
            os = "windows",
            architecture = "x64",
            archiveName = "OpenJDK17U-jdk_x64_windows_hotspot_17.0.19_10.zip",
            downloadUrl = "https://github.com/adoptium/temurin17-binaries/releases/" +
                "download/jdk-17.0.19%2B10/" +
                "OpenJDK17U-jdk_x64_windows_hotspot_17.0.19_10.zip",
            sha256 = "b5b235c48adf6a081874b812c630b9f4b5f637b7a5ed18b9174d08a41ec4c235",
        ),
        Distribution(
            os = "mac",
            architecture = "x64",
            archiveName = "OpenJDK17U-jdk_x64_mac_hotspot_17.0.19_10.tar.gz",
            downloadUrl = "https://github.com/adoptium/temurin17-binaries/releases/" +
                "download/jdk-17.0.19%2B10/" +
                "OpenJDK17U-jdk_x64_mac_hotspot_17.0.19_10.tar.gz",
            sha256 = "03632d1fbf139ab3719a9f4b47dc206251449b87557143c822336dbf8c06560f",
        ),
        Distribution(
            os = "mac",
            architecture = "aarch64",
            archiveName = "OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.19_10.tar.gz",
            downloadUrl = "https://github.com/adoptium/temurin17-binaries/releases/" +
                "download/jdk-17.0.19%2B10/" +
                "OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.19_10.tar.gz",
            sha256 = "8fa1eff40bb637a33613b2ccb8b12c70dc3661cc22cf8e784943715769a05336",
        ),
    )

    /** Adoptium's OS name for a JVM `os.name`, or null when Kani does not package there. */
    fun normalizedOs(osName: String): String? {
        val lowered = osName.lowercase()
        return when {
            lowered.startsWith("windows") -> "windows"
            lowered.startsWith("mac") || lowered.startsWith("darwin") -> "mac"
            lowered.startsWith("linux") -> "linux"
            else -> null
        }
    }

    /** Adoptium's architecture name for a JVM `os.arch`, or null when unsupported. */
    fun normalizedArchitecture(osArch: String): String? =
        when (osArch.lowercase()) {
            "x86_64", "amd64", "x64" -> "x64"
            "aarch64", "arm64" -> "aarch64"
            else -> null
        }

    /** The pinned archive for a host, or null when no combination matches. */
    fun distributionFor(osName: String, osArch: String): Distribution? {
        val os = normalizedOs(osName) ?: return null
        val architecture = normalizedArchitecture(osArch) ?: return null
        return DISTRIBUTIONS.firstOrNull {
            it.os == os && it.architecture == architecture
        }
    }

    /**
     * Parses a JDK `release` file into its `KEY="value"` pairs.
     *
     * Values are unquoted, because `release` quotes some and not others, and a comparison
     * against a quoted string would silently never match.
     */
    fun readReleaseProperties(javaHome: File): Map<String, String> {
        val release = File(javaHome, "release")
        if (!release.isFile) {
            return emptyMap()
        }
        return release.readLines().mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) {
                null
            } else {
                val key = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim().removeSurrounding("\"")
                key to value
            }
        }.toMap()
    }

    /**
     * Every way the JDK at [javaHome] differs from the pin, as human-readable lines.
     *
     * Returns a list rather than throwing on the first problem so one failure reports the
     * whole mismatch: a wrong vendor and a wrong patch level are usually the same mistake
     * (the wrong JDK was picked up) and reporting them one build at a time is a worse way
     * to learn that.
     */
    fun mismatches(javaHome: File): List<String> {
        val properties = readReleaseProperties(javaHome)
        if (properties.isEmpty()) {
            return listOf(
                "no readable 'release' file at $javaHome, so the packaging JDK cannot " +
                    "be identified at all",
            )
        }
        val problems = mutableListOf<String>()
        val implementor = properties["IMPLEMENTOR"]
        if (implementor != IMPLEMENTOR) {
            problems += "IMPLEMENTOR is ${implementor ?: "absent"}, expected $IMPLEMENTOR"
        }
        val implementorVersion = properties["IMPLEMENTOR_VERSION"]
        if (implementorVersion != IMPLEMENTOR_VERSION) {
            problems += "IMPLEMENTOR_VERSION is ${implementorVersion ?: "absent"}, " +
                "expected $IMPLEMENTOR_VERSION"
        }
        val javaVersion = properties["JAVA_VERSION"]
        if (javaVersion != JAVA_VERSION) {
            problems += "JAVA_VERSION is ${javaVersion ?: "absent"}, expected $JAVA_VERSION"
        }
        return problems
    }

    /**
     * Fails unless [javaHome] is exactly the pinned JDK, naming how to get the right one.
     *
     * Deliberately fatal rather than a warning. The whole value of the pin is that a
     * release cannot be built with an unpinned runtime, and a warning in a Gradle log is
     * indistinguishable from silence during a release. The message carries this host's
     * pinned download URL and checksum, because the useful response to this failure is to
     * install that archive, not to read this source file.
     */
    fun verify(
        javaHome: File,
        osName: String = System.getProperty("os.name").orEmpty(),
        osArch: String = System.getProperty("os.arch").orEmpty(),
    ): File {
        val problems = mismatches(javaHome)
        if (problems.isEmpty()) {
            return javaHome
        }
        val distribution = distributionFor(osName, osArch)
        val remedy = if (distribution == null) {
            "This host ($osName/$osArch) has no pinned packaging JDK, so Kani does not " +
                "package here. Add a Distribution to KaniPackagingJdk only with a real " +
                "upstream URL and checksum."
        } else {
            "Install the pinned JDK for this host and point the build at it:\n" +
                "  ${distribution.downloadUrl}\n" +
                "  sha256 ${distribution.sha256}"
        }
        throw IllegalStateException(
            buildString {
                appendLine(
                    "The desktop packaging JDK is not the pinned " +
                        "$DISTRIBUTION_NAME $JAVA_VERSION+$BUILD.",
                )
                appendLine("Resolved: $javaHome")
                problems.forEach { appendLine("  - $it") }
                appendLine(
                    "This JDK's runtime is copied into the installed application, so it " +
                        "ships to users. It is release evidence, not build environment.",
                )
                append(remedy)
            },
        )
    }
}
