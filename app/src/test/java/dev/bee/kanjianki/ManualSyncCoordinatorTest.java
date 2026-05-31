package dev.bee.kanjianki;

import dev.bee.kanjianki.sync.ManualSyncEngine;
import dev.bee.kanjianki.sync.SyncProgress;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ManualSyncCoordinatorTest {
    @Test
    public void successfulSyncRunsSuccessActionBeforeRenderingResult() throws Exception {
        ManualSyncEngine.SyncResult result = syncResult(true, false, "ok");
        List<String> events = new ArrayList<>();
        AtomicReference<ManualSyncEngine.SyncResult> rendered = new AtomicReference<>();

        ManualSyncCoordinator coordinator = new ManualSyncCoordinator(
                directExecutor(),
                Runnable::run,
                progress -> {
                    events.add("run");
                    progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.READING_NOTES));
                    return result;
                },
                () -> events.add("success"),
                syncResult -> {
                    events.add("render");
                    rendered.set(syncResult);
                }
        );

        AtomicBoolean progressCalled = new AtomicBoolean(false);
        coordinator.start(update -> progressCalled.set(true));

        assertEquals(Arrays.asList("run", "success", "render"), events);
        assertTrue(progressCalled.get());
        assertSame(result, rendered.get());
    }

    @Test
    public void failedSyncRendersWithoutSuccessAction() throws Exception {
        ManualSyncEngine.SyncResult result = syncResult(false, false, "failed");
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<ManualSyncEngine.SyncResult> rendered = new AtomicReference<>();

        ManualSyncCoordinator coordinator = new ManualSyncCoordinator(
                directExecutor(),
                Runnable::run,
                progress -> result,
                () -> success.set(true),
                rendered::set
        );

        coordinator.start(null);

        assertFalse(success.get());
        assertSame(result, rendered.get());
    }

    private static Executor directExecutor() {
        return Runnable::run;
    }

    private static ManualSyncEngine.SyncResult syncResult(boolean success, boolean skipped, String message) throws Exception {
        Constructor<ManualSyncEngine.SyncResult> constructor = ManualSyncEngine.SyncResult.class.getDeclaredConstructor(
                boolean.class,
                boolean.class,
                int.class,
                int.class,
                String.class,
                String.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(success, skipped, 0, 0, message, "");
    }
}
