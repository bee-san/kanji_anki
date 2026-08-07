package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.UpdateDeliveryResult
import dev.bee.kanjianki.platform.UpdatePackageKind
import dev.bee.kanjianki.platform.VerifiedUpdatePackage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.writeText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the delivery adapter agrees to execute.
 *
 * Every refusal case gets its own test, because a false OPENED here means Kani asked
 * the operating system to run a file it should not have. The `openFile` seam records
 * whether the launch happened at all: asserting only on the returned result would let
 * a bug that launches *and then* reports FAILED pass.
 */
class DesktopUpdateDeliveryTest {
    private val temporary: Path = Files.createTempDirectory("kani-update-delivery-")

    @After
    fun tearDown() {
        temporary.toFile().deleteRecursively()
    }

    private fun installer(name: String, bytes: String = "installer"): Path =
        temporary.resolve(name).also { it.writeText(bytes) }

    private fun packageFor(
        file: Path,
        kind: UpdatePackageKind = UpdatePackageKind.DEB,
        sha256: String = DesktopUpdateDelivery.sha256Of(file),
        version: String = "1.2.3",
    ) = VerifiedUpdatePackage(file = file, version = version, kind = kind, sha256 = sha256)

    @Test
    fun opensAnInstallerWhoseBytesStillMatchItsDigest() {
        var opened: Path? = null
        val delivery = DesktopUpdateDelivery(
            openFile = { path -> opened = path; true },
            hostKind = UpdatePackageKind.DEB,
        )
        val file = installer("kani-desktop-linux-x64.deb")

        assertEquals(UpdateDeliveryResult.OPENED, delivery.deliver(packageFor(file)))
        assertEquals(file, opened)
    }

    @Test
    fun refusesAnInstallerWhoseBytesChangedAfterVerification() {
        var opened = false
        val file = installer("kani-desktop-linux-x64.deb")
        val update = packageFor(file)

        // The whole reason the digest is re-checked here: verification happened earlier,
        // and the file sat in a writable directory in between.
        file.writeText("substituted after verification")

        val delivery = DesktopUpdateDelivery(
            openFile = { opened = true; true },
            hostKind = UpdatePackageKind.DEB,
        )
        assertEquals(UpdateDeliveryResult.FAILED, delivery.deliver(update))
        assertFalse("a substituted installer must never be launched", opened)
    }

    @Test
    fun refusesASymbolicLink() {
        var opened = false
        val real = installer("real.deb")
        val link = temporary.resolve("kani-desktop-linux-x64.deb")
        try {
            link.createSymbolicLinkPointingTo(real)
        } catch (_: UnsupportedOperationException) {
            return // A host without symlink support cannot exhibit the hazard.
        }

        // A link that passed verification can be repointed afterwards, so the check
        // would describe one file and the launch would run another.
        val delivery = DesktopUpdateDelivery(
            openFile = { opened = true; true },
            hostKind = UpdatePackageKind.DEB,
            digestOf = { DesktopUpdateDelivery.sha256Of(real) },
        )
        val update = packageFor(link, sha256 = DesktopUpdateDelivery.sha256Of(real))

        assertEquals(UpdateDeliveryResult.FAILED, delivery.deliver(update))
        assertFalse(opened)
    }

    @Test
    fun refusesADirectoryWearingAnInstallerName() {
        var opened = false
        val directory = temporary.resolve("Kani.dmg").also { it.createDirectories() }
        val delivery = DesktopUpdateDelivery(
            openFile = { opened = true; true },
            hostKind = UpdatePackageKind.DMG,
            digestOf = { "0".repeat(64) },
        )
        val update = packageFor(directory, kind = UpdatePackageKind.DMG, sha256 = "0".repeat(64))

        assertEquals(UpdateDeliveryResult.FAILED, delivery.deliver(update))
        assertFalse(opened)
    }

    @Test
    fun refusesAMissingFile() {
        var opened = false
        val absent = temporary.resolve("absent.deb")
        val delivery = DesktopUpdateDelivery(
            openFile = { opened = true; true },
            hostKind = UpdatePackageKind.DEB,
            digestOf = { "0".repeat(64) },
        )

        assertEquals(
            UpdateDeliveryResult.FAILED,
            delivery.deliver(packageFor(absent, sha256 = "0".repeat(64))),
        )
        assertFalse(opened)
    }

    @Test
    fun reportsAnotherPlatformsPackageAsUnsupportedRatherThanFailed() {
        var opened = false
        val file = installer("kani-desktop-windows-x64.msi")
        val delivery = DesktopUpdateDelivery(
            openFile = { opened = true; true },
            hostKind = UpdatePackageKind.DEB,
        )

        // Not FAILED: nothing is broken, the package is simply for another OS, and
        // "failed" would send the user hunting for a fault in their own install.
        assertEquals(
            UpdateDeliveryResult.UNSUPPORTED,
            delivery.deliver(packageFor(file, kind = UpdatePackageKind.MSI)),
        )
        assertFalse(opened)
    }

    @Test
    fun reportsUnsupportedOnAHostWithNoKnownInstallerFormat() {
        val file = installer("kani-desktop-linux-x64.deb")
        val delivery = DesktopUpdateDelivery(
            openFile = { error("an unknown host must not launch anything") },
            hostKind = null,
        )

        assertEquals(UpdateDeliveryResult.UNSUPPORTED, delivery.deliver(packageFor(file)))
    }

    @Test
    fun refusesAFileWhoseExtensionContradictsItsDeclaredKind() {
        var opened = false
        // The name says one format and the kind says another, so one of them is lying
        // about what would actually run.
        val file = installer("kani-desktop-linux-x64.msi")
        val delivery = DesktopUpdateDelivery(
            openFile = { opened = true; true },
            hostKind = UpdatePackageKind.DEB,
        )

        assertEquals(UpdateDeliveryResult.FAILED, delivery.deliver(packageFor(file)))
        assertFalse(opened)
    }

    @Test
    fun treatsAFailedOrThrowingLaunchAsAResult() {
        val file = installer("kani-desktop-linux-x64.deb")

        val declined = DesktopUpdateDelivery(
            openFile = { false },
            hostKind = UpdatePackageKind.DEB,
        )
        assertEquals(UpdateDeliveryResult.FAILED, declined.deliver(packageFor(file)))

        // An update that cannot be opened must leave the app running; throwing out of
        // delivery would take down the window that offered the update.
        val throwing = DesktopUpdateDelivery(
            openFile = { throw UnsupportedOperationException("no handler registered") },
            hostKind = UpdatePackageKind.DEB,
        )
        assertEquals(UpdateDeliveryResult.FAILED, throwing.deliver(packageFor(file)))
    }

    @Test
    fun mapsEachHostToItsOwnInstallerFormat() {
        assertEquals(
            UpdatePackageKind.MSI,
            DesktopUpdateDelivery.currentHostKind("Windows 11"),
        )
        assertEquals(
            UpdatePackageKind.DMG,
            DesktopUpdateDelivery.currentHostKind("Mac OS X"),
        )
        assertEquals(
            UpdatePackageKind.DEB,
            DesktopUpdateDelivery.currentHostKind("Linux"),
        )
        // Null rather than a fallback: not knowing which format would run is exactly
        // when handing a file to the OS is the wrong move.
        assertNull(DesktopUpdateDelivery.currentHostKind("SunOS"))
        assertNull(DesktopUpdateDelivery.currentHostKind(""))
    }

    @Test
    fun hashesTheFileItIsGivenStreamingLargeInput() {
        // Larger than the streaming buffer, so a single-read implementation would
        // produce a digest of only the first chunk and silently accept a modified tail.
        val large = temporary.resolve("large.deb")
        val block = "kani".repeat(1_024)
        Files.newBufferedWriter(large).use { writer ->
            repeat(100) { writer.write(block) }
        }

        val digest = DesktopUpdateDelivery.sha256Of(large)
        assertEquals(64, digest.length)
        assertTrue(digest.all { it in "0123456789abcdef" })

        // Appending must change it.
        Files.newBufferedWriter(large, java.nio.file.StandardOpenOption.APPEND).use { writer ->
            writer.write("x")
        }
        assertFalse(digest == DesktopUpdateDelivery.sha256Of(large))
    }

    @Test
    fun theRealHostKindIsUsedByDefault() {
        // Covers the default argument, so the production path is exercised rather than
        // only the injected one every other case uses.
        val delivery = DesktopUpdateDelivery(openFile = { true })
        val file = installer("kani-desktop-linux-x64.deb")

        val result = delivery.deliver(packageFor(file))
        val expected = if (DesktopUpdateDelivery.currentHostKind() == UpdatePackageKind.DEB) {
            UpdateDeliveryResult.OPENED
        } else {
            UpdateDeliveryResult.UNSUPPORTED
        }
        assertEquals(expected, result)
    }
}
