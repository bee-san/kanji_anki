package dev.bee.kanjianki;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public final class HomeSyncConfirmDialogModelTest {
    @Test
    public void createUsesSyncCopyAndRunsCallbacks() {
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger dismissed = new AtomicInteger();

        HomeSyncConfirmDialogModel model = HomeSyncConfirmDialogModels.create(
                "Kani imports suspended Kiku cards by default.",
                confirmed::incrementAndGet,
                dismissed::incrementAndGet
        );

        assertEquals("Sync AnkiDroid?", model.getTitle());
        assertEquals("Kani imports suspended Kiku cards by default.", model.getMessage());
        assertEquals("Sync cards", model.getConfirmLabel());
        assertEquals("Cancel", model.getDismissLabel());

        model.getOnConfirm().run();
        model.getOnDismiss().run();

        assertEquals(1, confirmed.get());
        assertEquals(1, dismissed.get());
    }
}
