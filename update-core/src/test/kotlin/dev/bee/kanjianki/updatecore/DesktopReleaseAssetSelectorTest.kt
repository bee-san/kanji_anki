package dev.bee.kanjianki.updatecore

import dev.bee.kanjianki.updatecore.DesktopReleaseAssetSelector.DesktopPackageType
import dev.bee.kanjianki.updatecore.DesktopReleaseAssetSelector.DesktopTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopReleaseAssetSelectorTest {
    @Test
    fun canonicalNamesMatchTheGoal202Contract() {
        assertEquals(
            "kani-desktop-windows-x64-1.2.3.msi",
            DesktopReleaseAssetSelector.canonicalName(DesktopTarget.WINDOWS_X64, DesktopPackageType.MSI, "1.2.3"),
        )
        assertEquals(
            "kani-desktop-macos-arm64-1.2.3.dmg",
            DesktopReleaseAssetSelector.canonicalName(DesktopTarget.MACOS_ARM64, DesktopPackageType.DMG, "1.2.3"),
        )
        assertEquals(
            "kani-desktop-linux-x64-1.2.3.deb",
            DesktopReleaseAssetSelector.canonicalName(DesktopTarget.LINUX_X64, DesktopPackageType.DEB, "1.2.3"),
        )
        assertEquals(
            "kani-desktop-linux-x64-1.2.3.tar.gz",
            DesktopReleaseAssetSelector.canonicalName(DesktopTarget.LINUX_X64, DesktopPackageType.TAR_GZ, "1.2.3"),
        )
    }

    @Test
    fun eachTargetSelectsItsOwnPackage() {
        val release = release(
            "kani-desktop-windows-x64-1.2.3.msi",
            "kani-desktop-macos-arm64-1.2.3.dmg",
            "kani-desktop-linux-x64-1.2.3.deb",
        )
        assertEquals(
            "kani-desktop-windows-x64-1.2.3.msi",
            DesktopReleaseAssetSelector.select(release, DesktopTarget.WINDOWS_X64, "1.2.3")?.asset?.name(),
        )
        assertEquals(
            DesktopPackageType.DMG,
            DesktopReleaseAssetSelector.select(release, DesktopTarget.MACOS_ARM64, "1.2.3")?.packageType,
        )
    }

    @Test
    fun linuxPrefersTheDebOverThePortableTarball() {
        val bothLinux = release(
            "kani-desktop-linux-x64-1.2.3.deb",
            "kani-desktop-linux-x64-1.2.3.tar.gz",
        )
        val selection = DesktopReleaseAssetSelector.select(bothLinux, DesktopTarget.LINUX_X64, "1.2.3")
        assertEquals(DesktopPackageType.DEB, selection?.packageType)
        assertTrue(selection?.packageType?.participatesInAutomaticHandoff == true)

        // With only the tarball, it is selected but flagged manual-update.
        val tarballOnly = release("kani-desktop-linux-x64-1.2.3.tar.gz")
        val fallback = DesktopReleaseAssetSelector.select(tarballOnly, DesktopTarget.LINUX_X64, "1.2.3")
        assertEquals(DesktopPackageType.TAR_GZ, fallback?.packageType)
        assertFalse(fallback?.packageType?.participatesInAutomaticHandoff == true)
    }

    @Test
    fun aWrongArchOrMissingAssetIsNoUpdateNotAFallback() {
        // Only a Windows build present: a macOS host gets nothing, never the .msi.
        val windowsOnly = release("kani-desktop-windows-x64-1.2.3.msi")
        assertNull(DesktopReleaseAssetSelector.select(windowsOnly, DesktopTarget.MACOS_ARM64, "1.2.3"))

        // A version mismatch is not a match, even for the right platform.
        assertNull(DesktopReleaseAssetSelector.select(windowsOnly, DesktopTarget.WINDOWS_X64, "9.9.9"))

        // A null release (metadata absent) is no update.
        assertNull(DesktopReleaseAssetSelector.select(null, DesktopTarget.WINDOWS_X64, "1.2.3"))
    }

    @Test
    fun theTarballExtensionIsNotConfusedWithAPlainGz() {
        // A stray .gz that is not the canonical tar.gz name must not match.
        val stray = release("kani-desktop-linux-x64-1.2.3.gz")
        assertNull(DesktopReleaseAssetSelector.select(stray, DesktopTarget.LINUX_X64, "1.2.3"))
    }

    private fun release(vararg names: String) = GitHubReleaseMetadata(
        tagName = "v1.2.3",
        htmlUrl = "https://example.invalid/releases/v1.2.3",
        assets = names.map { GitHubReleaseMetadata.ReleaseAsset(it, "https://example.invalid/$it") },
    )
}
