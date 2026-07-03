package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallPermissionPromptPolicyTest {
    @Test
    fun firstCompletedCheckWithoutPermissionPrompts() {
        assertTrue(
            InstallPermissionPromptPolicy.shouldPrompt(
                true,
                false,
                true,
                false,
                false,
                "",
                "",
            ),
        )
    }

    @Test
    fun freshInstallWithoutAnyUpdateCheckStaysQuiet() {
        assertFalse(
            InstallPermissionPromptPolicy.shouldPrompt(
                true,
                false,
                false,
                false,
                false,
                "",
                "",
            ),
        )
    }

    @Test
    fun grantedPermissionNeverPrompts() {
        assertFalse(
            InstallPermissionPromptPolicy.shouldPrompt(
                true,
                true,
                true,
                false,
                true,
                "v0.5.0",
                "",
            ),
        )
    }

    @Test
    fun disabledAutomaticUpdatesNeverPrompt() {
        assertFalse(
            InstallPermissionPromptPolicy.shouldPrompt(
                false,
                false,
                true,
                false,
                true,
                "v0.5.0",
                "",
            ),
        )
    }

    @Test
    fun declinedFirstPromptStaysQuietWithoutPendingUpdate() {
        assertFalse(
            InstallPermissionPromptPolicy.shouldPrompt(
                true,
                false,
                true,
                true,
                false,
                "",
                "",
            ),
        )
    }

    @Test
    fun pendingUpdateForNewVersionPromptsAgain() {
        assertTrue(
            InstallPermissionPromptPolicy.shouldPrompt(
                true,
                false,
                true,
                true,
                true,
                "v0.5.0",
                "",
            ),
        )
    }

    @Test
    fun pendingUpdateForAlreadyPromptedVersionStaysQuiet() {
        assertFalse(
            InstallPermissionPromptPolicy.shouldPrompt(
                true,
                false,
                true,
                true,
                true,
                "v0.5.0",
                "v0.5.0",
            ),
        )
    }

    @Test
    fun promptedVersionComparisonIgnoresSurroundingWhitespace() {
        assertFalse(
            InstallPermissionPromptPolicy.shouldPrompt(
                true,
                false,
                true,
                true,
                true,
                " v0.5.0 ",
                "v0.5.0",
            ),
        )
    }

    @Test
    fun normalizedVersionTrimsAndDefaultsToEmpty() {
        assertEquals("", InstallPermissionPromptPolicy.normalizedVersion(null))
        assertEquals("", InstallPermissionPromptPolicy.normalizedVersion("   "))
        assertEquals("v0.5.0", InstallPermissionPromptPolicy.normalizedVersion(" v0.5.0 "))
    }
}
