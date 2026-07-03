package dev.bee.kanjianki

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
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
}
