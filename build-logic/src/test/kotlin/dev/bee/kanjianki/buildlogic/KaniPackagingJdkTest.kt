package dev.bee.kanjianki.buildlogic

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KaniPackagingJdkTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun thePackagingJdkIsPinnedToAnExactVendorAndBuild() {
        assertEquals(17, KaniPackagingJdk.FEATURE_VERSION)
        assertEquals("17.0.19", KaniPackagingJdk.JAVA_VERSION)
        assertEquals("10", KaniPackagingJdk.BUILD)
        assertEquals("Eclipse Adoptium", KaniPackagingJdk.IMPLEMENTOR)
        assertEquals("Temurin-17.0.19+10", KaniPackagingJdk.IMPLEMENTOR_VERSION)
        // The feature version must agree with the pinned patch level, or a toolchain query
        // built from FEATURE_VERSION could never resolve to the JDK the checksums describe.
        assertTrue(
            "JAVA_VERSION must be a ${KaniPackagingJdk.FEATURE_VERSION}.x release",
            KaniPackagingJdk.JAVA_VERSION.startsWith(
                "${KaniPackagingJdk.FEATURE_VERSION}.",
            ),
        )
    }

    /**
     * Every pinned archive must carry a real upstream URL and a full SHA-256.
     *
     * The checksum is the whole point of the pin: without it, "Temurin 17.0.19+10" names a
     * build but not bytes, and this runtime ships inside the application. Asserted
     * structurally (64 hex characters, a URL naming the pinned version) rather than by
     * re-listing each digest, because a second copy of the digests here would be checking
     * that a constant equals itself.
     */
    @Test
    fun everyPinnedDistributionHasARealUrlAndAFullChecksum() {
        val hexadecimal = Regex("[0-9a-f]{64}")
        for (distribution in KaniPackagingJdk.DISTRIBUTIONS) {
            val where = "${distribution.os}/${distribution.architecture}"
            assertTrue(
                "$where must pin a sha256, not a truncated or placeholder digest",
                hexadecimal.matches(distribution.sha256),
            )
            assertTrue(
                "$where must download from Adoptium's own release assets",
                distribution.downloadUrl.startsWith(
                    "https://github.com/adoptium/temurin17-binaries/releases/download/",
                ),
            )
            assertTrue(
                "$where must download the pinned build, not a floating latest",
                distribution.downloadUrl.contains("jdk-17.0.19%2B10"),
            )
            assertTrue(
                "$where url must end with the archive it claims to be",
                distribution.downloadUrl.endsWith(distribution.archiveName),
            )
            assertTrue(
                "$where archive must name the pinned version",
                distribution.archiveName.contains("17.0.19_10"),
            )
        }
        // Distinct bytes per host: an accidentally duplicated digest would mean two
        // platforms verifying against one archive, which is a verification that passes and
        // proves nothing.
        assertEquals(
            KaniPackagingJdk.DISTRIBUTIONS.size,
            KaniPackagingJdk.DISTRIBUTIONS.map { it.sha256 }.toSet().size,
        )
    }

    @Test
    fun everyHostKaniPackagesOnHasAPinnedDistribution() {
        assertEquals(
            setOf(
                "linux" to "x64",
                "linux" to "aarch64",
                "windows" to "x64",
                "mac" to "x64",
                "mac" to "aarch64",
            ),
            KaniPackagingJdk.DISTRIBUTIONS.map { it.os to it.architecture }.toSet(),
        )
    }

    /**
     * Windows on ARM64 has no pinned JDK, and that is recorded rather than implied.
     *
     * Temurin 17 has no Windows ARM64 build at all, so there is no archive to pin. The
     * honest consequence is that Kani's Windows package is x64 and runs under emulation
     * there. This test exists so that gap stays a documented absence: filling the table
     * with a guessed URL would produce a checksum failure at install time on a platform
     * nobody tested, which reads as a corrupted download rather than as an unsupported
     * host.
     */
    @Test
    fun theWindowsArm64GapIsRecordedRatherThanImplied() {
        assertNull(KaniPackagingJdk.distributionFor("Windows 11", "aarch64"))
        assertEquals(
            "x64",
            KaniPackagingJdk.distributionFor("Windows 11", "amd64")?.architecture,
        )
    }

    @Test
    fun hostNamesMapToAdoptiumNamesAndUnknownHostsResolveToNothing() {
        assertEquals("windows", KaniPackagingJdk.normalizedOs("Windows 11"))
        assertEquals("mac", KaniPackagingJdk.normalizedOs("Mac OS X"))
        assertEquals("linux", KaniPackagingJdk.normalizedOs("Linux"))
        assertNull(KaniPackagingJdk.normalizedOs("SunOS"))

        assertEquals("x64", KaniPackagingJdk.normalizedArchitecture("x86_64"))
        assertEquals("x64", KaniPackagingJdk.normalizedArchitecture("amd64"))
        assertEquals("aarch64", KaniPackagingJdk.normalizedArchitecture("arm64"))
        assertNull(KaniPackagingJdk.normalizedArchitecture("ppc64le"))

        assertEquals(
            "OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.19_10.tar.gz",
            KaniPackagingJdk.distributionFor("Mac OS X", "aarch64")?.archiveName,
        )
        assertNull(KaniPackagingJdk.distributionFor("SunOS", "x86_64"))
    }

    /** `release` mixes quoted and unquoted values, so the reader must unquote. */
    @Test
    fun releasePropertiesAreReadWithQuotesStripped() {
        val javaHome = temporaryFolder.newFolder("jdk")
        File(javaHome, "release").writeText(
            """
            IMPLEMENTOR="Eclipse Adoptium"
            JAVA_VERSION="17.0.19"
            MODULES="java.base java.desktop"
            OS_ARCH=x86_64
            """.trimIndent() + "\n",
        )

        val properties = KaniPackagingJdk.readReleaseProperties(javaHome)
        assertEquals("Eclipse Adoptium", properties["IMPLEMENTOR"])
        assertEquals("17.0.19", properties["JAVA_VERSION"])
        assertEquals("x86_64", properties["OS_ARCH"])
    }

    @Test
    fun aJdkWithNoReadableReleaseFileFailsRatherThanPassingUnidentified() {
        val javaHome = temporaryFolder.newFolder("no-release")
        assertEquals(emptyMap<String, String>(), KaniPackagingJdk.readReleaseProperties(javaHome))

        val mismatches = KaniPackagingJdk.mismatches(javaHome)
        assertEquals(1, mismatches.size)
        assertTrue(
            "an unidentifiable JDK must say so rather than list absent fields",
            mismatches.single().contains("cannot be identified"),
        )
    }

    @Test
    fun theExactPinnedJdkVerifiesWithNoMismatches() {
        val javaHome = writeReleaseFile(
            implementor = KaniPackagingJdk.IMPLEMENTOR,
            implementorVersion = KaniPackagingJdk.IMPLEMENTOR_VERSION,
            javaVersion = KaniPackagingJdk.JAVA_VERSION,
        )
        assertEquals(emptyList<String>(), KaniPackagingJdk.mismatches(javaHome))
        assertEquals(javaHome, KaniPackagingJdk.verify(javaHome))
    }

    /**
     * The reproduced defect: a JDK of the right vendor but the wrong feature version.
     *
     * This is not hypothetical. Building this repository with a Temurin 21 daemon and no
     * other change shipped `JAVA_VERSION="21.0.11"` inside `Kani/lib/runtime/release`,
     * with no warning and no failure, because Compose's packaging default is the daemon's
     * own `java.home`.
     */
    @Test
    fun aDifferentJdkVersionFromTheSameVendorIsRejected() {
        val javaHome = writeReleaseFile(
            implementor = "Eclipse Adoptium",
            implementorVersion = "Temurin-21.0.11+10",
            javaVersion = "21.0.11",
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            KaniPackagingJdk.verify(javaHome, osName = "Linux", osArch = "x86_64")
        }
        val message = failure.message.orEmpty()
        assertTrue(
            "the failure must name what was found",
            message.contains("21.0.11"),
        )
        assertTrue(
            "the failure must name what was expected",
            message.contains("Temurin-17.0.19+10"),
        )
        assertTrue(
            "the failure must say the runtime ships to users",
            message.contains("ships to users"),
        )
        assertTrue(
            "the failure must carry this host's pinned download URL",
            message.contains(
                "OpenJDK17U-jdk_x64_linux_hotspot_17.0.19_10.tar.gz",
            ),
        )
        assertTrue(
            "the failure must carry this host's pinned checksum",
            message.contains(
                "d8afc263758141a66e0e3aafc321e783f7016696f4eaea067d340a269037d331",
            ),
        )
    }

    /**
     * A right-version JDK from the wrong vendor is also rejected.
     *
     * Vendor matters beyond preference here: Compose's own packaging check refuses
     * Homebrew's JDK outright, and a distribution's `jlink`/`jpackage` behavior and
     * bundled certificate set are part of what ships. Also asserts that a wrong vendor and
     * a wrong build are reported together, since they are usually one mistake.
     */
    @Test
    fun aRightVersionJdkFromTheWrongVendorIsRejected() {
        val javaHome = writeReleaseFile(
            implementor = "Azul Systems, Inc.",
            implementorVersion = "Zulu17.60+17-CA",
            javaVersion = KaniPackagingJdk.JAVA_VERSION,
        )

        val mismatches = KaniPackagingJdk.mismatches(javaHome)
        assertEquals(2, mismatches.size)
        assertTrue(
            "the vendor mismatch must be reported",
            mismatches.any { it.contains("IMPLEMENTOR is Azul Systems, Inc.") },
        )
        assertTrue(
            "the build mismatch must be reported in the same pass",
            mismatches.any { it.contains("Zulu17.60+17-CA") },
        )
    }

    /**
     * A patch-level drift within the pinned vendor and feature version is rejected.
     *
     * This is the case Gradle's toolchain spec cannot catch on its own: a query for
     * Adoptium 17 is satisfied by any 17.x, so without this check a CI image that moved
     * from 17.0.19 to 17.0.20 would ship a different runtime than the tested one and
     * nothing would say so.
     */
    @Test
    fun aPatchLevelDriftInsideTheSameFeatureVersionIsRejected() {
        val javaHome = writeReleaseFile(
            implementor = KaniPackagingJdk.IMPLEMENTOR,
            implementorVersion = "Temurin-17.0.20+7",
            javaVersion = "17.0.20",
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            KaniPackagingJdk.verify(javaHome, osName = "Linux", osArch = "x86_64")
        }
        assertTrue(
            "the failure must name the drifted patch level",
            failure.message.orEmpty().contains("17.0.20"),
        )
    }

    /** On a host Kani does not package for, the message says that instead of a URL. */
    @Test
    fun anUnpackagedHostIsToldItIsUnpackagedRatherThanGivenSomeoneElsesArchive() {
        val javaHome = writeReleaseFile(
            implementor = "Eclipse Adoptium",
            implementorVersion = "Temurin-21.0.11+10",
            javaVersion = "21.0.11",
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            KaniPackagingJdk.verify(javaHome, osName = "SunOS", osArch = "sparcv9")
        }
        val message = failure.message.orEmpty()
        assertTrue(
            "an unpackaged host must be told Kani does not package there",
            message.contains("no pinned packaging JDK"),
        )
        assertTrue(
            "an unpackaged host must not be handed another platform's archive",
            !message.contains("OpenJDK17U-jdk"),
        )
    }

    /**
     * The convention must actually consume the pin on every packaging task.
     *
     * Read from the convention source, because the failure this guards is the pin existing
     * and not being wired: `KaniPackagingJdk` would be a correct, tested, unused object
     * while the image kept shipping the daemon's JVM — which is precisely the state this
     * commit found the build in.
     */
    @Test
    fun theDesktopConventionResolvesEveryPackagingToolFromThePinnedJdk() {
        val convention = File(
            File(requireNotNull(System.getProperty("kani.repositoryRoot"))),
            "build-logic/src/main/kotlin/" +
                "kani.desktop-application-conventions.gradle.kts",
        ).readText()

        assertTrue(
            "the toolchain must be requested from the pinned feature version",
            convention.contains("KaniPackagingJdk.FEATURE_VERSION"),
        )
        assertTrue(
            "the toolchain must request the pinned vendor",
            convention.contains("JvmVendorSpec.ADOPTIUM"),
        )
        assertTrue(
            "the resolved JDK must be verified against the exact pinned build",
            convention.contains("KaniPackagingJdk.verify("),
        )
        // jlink and jpackage both extend AbstractJvmToolOperationTask; the module scan and
        // the runtime probe are separate task types with their own JDK inputs. Missing any
        // one of them leaves that step on the daemon's JVM, and the probe in particular
        // decides which modules the plugin believes exist.
        for (taskType in listOf(
            "AbstractJvmToolOperationTask",
            "AbstractSuggestModulesTask",
            "AbstractCheckNativeDistributionRuntime",
        )) {
            assertTrue(
                "$taskType must resolve its JDK from the pin",
                convention.contains("tasks.withType<$taskType>()"),
            )
        }
        assertTrue(
            "the module scan must read the pinned JDK home",
            convention.contains("javaHome.set(packagingJdkHome)"),
        )
        assertTrue(
            "the runtime probe must read the pinned JDK home",
            convention.contains("jdkHome.set(packagingJdkHome)"),
        )
        // Assigning the DSL's plain-String `javaHome` would resolve the toolchain during
        // configuration, making `:desktop-app:test` require the packaging JDK to exist.
        assertTrue(
            "the packaging JDK must not be resolved at configuration time",
            !convention.contains("javaHome = "),
        )
    }

    private fun writeReleaseFile(
        implementor: String,
        implementorVersion: String,
        javaVersion: String,
    ): File {
        val javaHome = temporaryFolder.newFolder(
            "jdk-${implementorVersion.replace(Regex("[^A-Za-z0-9.]"), "-")}",
        )
        File(javaHome, "release").writeText(
            """
            IMPLEMENTOR="$implementor"
            IMPLEMENTOR_VERSION="$implementorVersion"
            JAVA_VERSION="$javaVersion"
            """.trimIndent() + "\n",
        )
        return javaHome
    }
}
