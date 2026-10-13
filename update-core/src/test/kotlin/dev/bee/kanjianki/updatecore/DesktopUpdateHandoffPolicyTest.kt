package dev.bee.kanjianki.updatecore

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopUpdateHandoffPolicyTest {
    private val originalLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun anAutomaticChannelConfirmsTheExactVersionAndFileFirst() {
        val handoff = DesktopUpdateHandoffPolicy.handoffFor(offer(), STAGED_PATH)

        val confirm = handoff as DesktopUpdateHandoffPolicy.Handoff.ConfirmThenOpenInstaller
        assertEquals("0.5.0", confirm.version)
        assertEquals(STAGED_PATH, confirm.artifactPath)
        // A confirmation that names neither the version nor the file is not informed
        // consent, so both must appear in what the user reads.
        assertTrue(confirm.confirmationTitle, confirm.confirmationTitle.contains("0.5.0"))
        assertTrue(confirm.confirmationBody, confirm.confirmationBody.contains(STAGED_PATH))
    }

    @Test
    fun theInstallerOpensOnlyAfterConfirmationOfThatSameVersion() {
        val handoff = DesktopUpdateHandoffPolicy.handoffFor(offer(), STAGED_PATH)

        assertTrue(DesktopUpdateHandoffPolicy.mayOpenInstaller(handoff, "0.5.0"))
        // Trimmed, because the confirmed version round-trips through stored state.
        assertTrue(DesktopUpdateHandoffPolicy.mayOpenInstaller(handoff, " 0.5.0 "))
        // Nothing confirmed: a background check must not reach the OS installer.
        assertFalse(DesktopUpdateHandoffPolicy.mayOpenInstaller(handoff, null))
        assertFalse(DesktopUpdateHandoffPolicy.mayOpenInstaller(handoff, "  "))
        // Consent is per-version: confirming 0.4.9 does not authorise installing 0.5.0.
        assertFalse(DesktopUpdateHandoffPolicy.mayOpenInstaller(handoff, "0.4.9"))
    }

    @Test
    fun aPortableTarballIsRevealedRatherThanInstalled() {
        val handoff = DesktopUpdateHandoffPolicy.handoffFor(
            offer(
                packageType = DesktopReleaseAssetSelector.DesktopPackageType.TAR_GZ,
                manualInstall = true,
            ),
            STAGED_PATH,
        )

        val reveal = handoff as DesktopUpdateHandoffPolicy.Handoff.RevealForManualInstall
        assertEquals("0.5.0", reveal.version)
        assertEquals(STAGED_PATH, reveal.artifactPath)
        assertTrue(reveal.guidance, reveal.guidance.contains("0.5.0"))
        // Kani did not create a portable install and must not replace it, so there is no
        // install button to authorise.
        assertFalse(DesktopUpdateHandoffPolicy.mayOpenInstaller(handoff, "0.5.0"))
    }

    @Test
    fun nothingIsOfferedUntilTheArtifactIsActuallyStaged() {
        for (path in listOf(null, "", "   ")) {
            val handoff = DesktopUpdateHandoffPolicy.handoffFor(offer(), path)

            val blocked = handoff as DesktopUpdateHandoffPolicy.Handoff.Blocked
            assertEquals(DesktopUpdateHandoffPolicy.blockedNothingStagedMessage(), blocked.reason)
            assertFalse(DesktopUpdateHandoffPolicy.mayOpenInstaller(handoff, "0.5.0"))
        }
    }

    @Test
    fun everyUserFacingStringIsLocalized() {
        Locale.setDefault(Locale.JAPANESE)
        val japanese = listOf(
            DesktopUpdateHandoffPolicy.confirmationTitle("0.5.0"),
            DesktopUpdateHandoffPolicy.confirmationBody(STAGED_PATH),
            DesktopUpdateHandoffPolicy.manualInstallGuidance("0.5.0"),
            DesktopUpdateHandoffPolicy.blockedNothingStagedMessage(),
        )

        Locale.setDefault(Locale.ENGLISH)
        val english = listOf(
            DesktopUpdateHandoffPolicy.confirmationTitle("0.5.0"),
            DesktopUpdateHandoffPolicy.confirmationBody(STAGED_PATH),
            DesktopUpdateHandoffPolicy.manualInstallGuidance("0.5.0"),
            DesktopUpdateHandoffPolicy.blockedNothingStagedMessage(),
        )

        for ((index, text) in japanese.withIndex()) {
            val differs = text != english[index]
            assertTrue("string $index must have a Japanese form", differs)
        }
    }

    private fun offer(
        packageType: DesktopReleaseAssetSelector.DesktopPackageType =
            DesktopReleaseAssetSelector.DesktopPackageType.DEB,
        manualInstall: Boolean = false,
    ) = DesktopUpdatePolicy.Outcome.UpdateAvailable(
        releaseTag = "v0.5.0",
        semanticVersion = "0.5.0",
        downloadUrl = "https://example.invalid/kani-desktop-linux-x64-0.5.0.deb",
        asset = ManifestAsset(
            filename = "kani-desktop-linux-x64-0.5.0.deb",
            sizeBytes = 4_096L,
            sha256 = "e".repeat(64),
            os = "linux",
            arch = "x64",
            packageType = packageType.manifestToken,
        ),
        packageType = packageType,
        manualInstall = manualInstall,
    )

    private companion object {
        const val STAGED_PATH = "/home/user/.cache/kani/updates/kani-desktop-linux-x64-0.5.0.deb"
    }
}
