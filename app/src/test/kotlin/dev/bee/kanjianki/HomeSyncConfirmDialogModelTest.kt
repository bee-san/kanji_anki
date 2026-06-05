package dev.bee.kanjianki

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class HomeSyncConfirmDialogModelTest {
    @Test
    fun createUsesSyncCopyAndRunsCallbacks() {
        val confirmed = AtomicInteger()
        val dismissed = AtomicInteger()

        val model = HomeSyncConfirmDialogModels.create(
            "Kani imports suspended Kiku cards by default, archives them locally, and only uses active cards when that filter is enabled.",
            onConfirm = confirmed::incrementAndGet,
            onDismiss = dismissed::incrementAndGet,
        )

        assertEquals("Sync AnkiDroid?", model.title)
        assertEquals("Kani imports suspended Kiku cards by default, archives them locally, and only uses active cards when that filter is enabled.", model.message)
        assertEquals("Sync cards", model.confirmLabel)
        assertEquals("Cancel", model.dismissLabel)

        model.onConfirm.run()
        model.onDismiss.run()

        assertEquals(1, confirmed.get())
        assertEquals(1, dismissed.get())
    }

    @Test
    fun createAcceptsOnboardingConfirmLabel() {
        val model = HomeSyncConfirmDialogModels.create(
            "Install AnkiDroid first.",
            "Install AnkiDroid",
            Runnable {},
            Runnable {},
        )

        assertEquals("Install AnkiDroid", model.confirmLabel)
    }
}
