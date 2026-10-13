package dev.bee.kanjianki.updatecore

import dev.bee.kanjianki.updatecore.DesktopUpdatePolicy.InstallationChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopInstallationChannelPolicyTest {
    @Test
    fun aRunThatIsNotAPackagedInstallIsOfferedNothing() {
        for (path in listOf(null, "", "   ")) {
            assertEquals(
                InstallationChannel.UNKNOWN,
                DesktopInstallationChannelPolicy.detect("Linux", path),
            )
        }
        // A source or Gradle run has no jpackage launcher path, and whoever built it owns
        // updating it.
        assertFalse(
            DesktopInstallationChannelPolicy.detect("Linux", null).participatesInAutomaticHandoff,
        )
    }

    @Test
    fun theLauncherPathComesFromTheJpackageProperty() {
        // Pinned because it is jpackage's contract, not Kani's: a typo would silently
        // read null on every packaged install and offer nobody an update.
        assertEquals("jpackage.app-path", DesktopInstallationChannelPolicy.APP_PATH_PROPERTY)
    }

    @Test
    fun windowsAndMacosResolveToTheirOnlyShippedPackage() {
        assertEquals(
            InstallationChannel.WINDOWS_MSI,
            DesktopInstallationChannelPolicy.detect("Windows 11", "C:\\Program Files\\Kani\\Kani.exe"),
        )
        // A per-user or custom-directory MSI install is still an MSI install; keying off
        // Program Files would misreport it as unknown.
        assertEquals(
            InstallationChannel.WINDOWS_MSI,
            DesktopInstallationChannelPolicy.detect("Windows 10", "D:\\Apps\\Kani\\Kani.exe"),
        )
        assertEquals(
            InstallationChannel.MACOS_DMG,
            DesktopInstallationChannelPolicy.detect("Mac OS X", "/Applications/Kani.app/Contents/MacOS/Kani"),
        )
        // "Darwin" contains "win": a substring match would report macOS as an MSI install
        // and offer it a Windows installer.
        assertEquals(
            InstallationChannel.MACOS_DMG,
            DesktopInstallationChannelPolicy.detect("Darwin", "/Applications/Kani.app/Contents/MacOS/Kani"),
        )
    }

    @Test
    fun linuxDistinguishesTheDebFromThePortableTarball() {
        assertEquals(
            InstallationChannel.LINUX_DEB,
            DesktopInstallationChannelPolicy.detect("Linux", "/opt/kani/bin/Kani"),
        )
        // /usr/local and a home directory are places a user unpacks an archive.
        assertEquals(
            InstallationChannel.LINUX_TAR_GZ,
            DesktopInstallationChannelPolicy.detect("Linux", "/usr/local/kani/bin/Kani"),
        )
        assertEquals(
            InstallationChannel.LINUX_TAR_GZ,
            DesktopInstallationChannelPolicy.detect("Linux", "/home/user/kani/bin/Kani"),
        )
    }

    @Test
    fun aPackageKaniDoesNotShipIsLeftToWhoeverOwnsIt() {
        // Distro repository, Flatpak, and Snap installs are updated by their packager;
        // Kani replacing them would fight the package manager.
        for (path in listOf("/usr/bin/kani", "/app/bin/Kani", "/snap/kani/current/bin/Kani")) {
            assertEquals(
                path,
                InstallationChannel.UNKNOWN,
                DesktopInstallationChannelPolicy.detect("Linux", path),
            )
        }
        // An OS Kani ships no desktop package for is likewise not updatable.
        assertEquals(
            InstallationChannel.UNKNOWN,
            DesktopInstallationChannelPolicy.detect("FreeBSD", "/usr/local/kani/bin/Kani"),
        )
    }

    @Test
    fun onlyTheDebParticipatesInAutomaticHandoffOnLinux() {
        assertTrue(
            DesktopInstallationChannelPolicy
                .detect("Linux", "/opt/kani/bin/Kani")
                .participatesInAutomaticHandoff,
        )
        // Kani did not create a portable unpack and must not replace it in place.
        assertFalse(
            DesktopInstallationChannelPolicy
                .detect("Linux", "/home/user/kani/bin/Kani")
                .participatesInAutomaticHandoff,
        )
    }

    @Test
    fun everyChannelHasAStableDistinctStorageTokenThatRoundTrips() {
        val tokens = InstallationChannel.entries.map { DesktopInstallationChannelPolicy.storageToken(it) }

        assertEquals(tokens.size, tokens.toSet().size)
        for (channel in InstallationChannel.entries) {
            val token = DesktopInstallationChannelPolicy.storageToken(channel)
            assertEquals(channel, DesktopInstallationChannelPolicy.fromStoredToken(token))
            // Stored settings round-trip through text, so tolerate case and padding.
            assertEquals(
                channel,
                DesktopInstallationChannelPolicy.fromStoredToken("  ${token.uppercase()}  "),
            )
        }
        // Pinned literally: changing a token silently re-reads every stored install as
        // unknown, which would stop offering updates to everyone at once.
        assertEquals(
            listOf("windows_msi", "macos_dmg", "linux_deb", "linux_tar_gz", "unknown"),
            tokens,
        )
    }

    @Test
    fun anAbsentOrUnrecognizedStoredTokenFailsClosed() {
        for (token in listOf(null, "", "   ", "linux_rpm", "LINUX-DEB")) {
            assertEquals(
                InstallationChannel.UNKNOWN,
                DesktopInstallationChannelPolicy.fromStoredToken(token),
            )
        }
    }

    @Test
    fun theStoredTokenIsWrittenOnlyWhenItIsOutOfDate() {
        val unchanged = DesktopInstallationChannelPolicy.resolve(
            osName = "Linux",
            appPath = "/opt/kani/bin/Kani",
            storedToken = "linux_deb",
        )
        assertEquals(InstallationChannel.LINUX_DEB, unchanged.channel)
        assertNull(unchanged.tokenToPersist)

        // A first launch has nothing stored.
        val first = DesktopInstallationChannelPolicy.resolve("Linux", "/opt/kani/bin/Kani", null)
        assertEquals("linux_deb", first.tokenToPersist)
    }

    @Test
    fun liveDetectionOverridesAStaleStoredChannel() {
        // Upgrading a portable unpack to the .deb must be noticed, not remembered wrongly.
        val upgraded = DesktopInstallationChannelPolicy.resolve(
            osName = "Linux",
            appPath = "/opt/kani/bin/Kani",
            storedToken = "linux_tar_gz",
        )
        assertEquals(InstallationChannel.LINUX_DEB, upgraded.channel)
        assertEquals("linux_deb", upgraded.tokenToPersist)

        // And the reverse: an install that is no longer packaged must stop being offered
        // updates for the package it used to be, so detection wins even when it is UNKNOWN.
        val noLongerPackaged = DesktopInstallationChannelPolicy.resolve(
            osName = "Linux",
            appPath = null,
            storedToken = "linux_deb",
        )
        assertEquals(InstallationChannel.UNKNOWN, noLongerPackaged.channel)
        assertEquals("unknown", noLongerPackaged.tokenToPersist)
    }
}
