package dev.bee.kanjianki

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeUpdatePermissionDialogModelTest {
    @Test
    fun createUsesUpdatePermissionCopyAndRunsCallbacks() {
        val allowed = AtomicInteger()
        val declined = AtomicInteger()

        val model = HomeUpdatePermissionDialogModels.create(
            pendingVersion = null,
            onAllow = allowed::incrementAndGet,
            onNotNow = declined::incrementAndGet,
        )

        assertEquals("Keep Kani up to date", model.title)
        assertEquals(
            "Kani can download and install verified updates by itself. " +
                "Allow Kani to install updates on the next Android settings screen.",
            model.message,
        )
        assertEquals("Allow", model.allowLabel)
        assertEquals("Not now", model.notNowLabel)

        model.onAllow.run()
        model.onNotNow.run()

        assertEquals(1, allowed.get())
        assertEquals(1, declined.get())
    }

    @Test
    fun createMentionsPendingVersionWhenKnown() {
        val model = HomeUpdatePermissionDialogModels.create(
            pendingVersion = "v0.5.0",
            onAllow = Runnable {},
            onNotNow = Runnable {},
        )

        assertEquals(
            "Kani 0.5.0 is verified and ready to install. " +
                "Allow Kani to install updates on the next Android settings screen " +
                "and it will update itself automatically.",
            model.message,
        )
    }

    @Test
    fun promptSnapshotCapturesTheBackgroundDecisionWithoutRenderTimeReads() {
        val firstPrompt = HomeUpdatePermissionPromptSnapshots.create(
            autoUpdateEnabled = true,
            canRequestPackageInstalls = false,
            hasCompletedUpdateCheck = true,
            firstPromptShown = false,
            hasPendingUpdate = false,
            latestVersion = "",
            lastPromptedVersion = "",
        )
        val pendingUpdate = HomeUpdatePermissionPromptSnapshots.create(
            autoUpdateEnabled = true,
            canRequestPackageInstalls = false,
            hasCompletedUpdateCheck = true,
            firstPromptShown = true,
            hasPendingUpdate = true,
            latestVersion = " v0.5.0 ",
            lastPromptedVersion = "v0.4.9",
        )
        val alreadyGranted = HomeUpdatePermissionPromptSnapshots.create(
            autoUpdateEnabled = true,
            canRequestPackageInstalls = true,
            hasCompletedUpdateCheck = true,
            firstPromptShown = false,
            hasPendingUpdate = false,
            latestVersion = "",
            lastPromptedVersion = "",
        )

        assertEquals(HomeUpdatePermissionPromptSnapshot("", null), firstPrompt)
        assertEquals(HomeUpdatePermissionPromptSnapshot("v0.5.0", "v0.5.0"), pendingUpdate)
        assertNull(alreadyGranted)
    }
}
