package dev.bee.kanjianki.update;

import androidx.work.ListenableWorker;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class AutoUpdateWorkerTest {
    @Test
    public void autoUpdateDecisionSkipsDisabledAndPendingUpdates() {
        AtomicInteger checks = new AtomicInteger();

        ListenableWorker.Result disabled = AutoUpdateWorker.runAutoUpdate(
                false,
                false,
                () -> {
                    checks.incrementAndGet();
                    return result(false);
                }
        );
        ListenableWorker.Result pending = AutoUpdateWorker.runAutoUpdate(
                true,
                true,
                () -> {
                    checks.incrementAndGet();
                    return result(false);
                }
        );

        assertSuccess(disabled);
        assertSuccess(pending);
        assertEquals(0, checks.get());
    }

    @Test
    public void autoUpdateDecisionMapsCheckerResultToWorkerOutcome() {
        assertSuccess(AutoUpdateWorker.runAutoUpdate(true, false, () -> result(false)));
        assertRetry(AutoUpdateWorker.runAutoUpdate(true, false, () -> result(true)));
    }

    @Test
    public void automaticUpdateCheckerFactoryRunsInjectedAutomaticRunner() {
        AtomicInteger checks = new AtomicInteger();

        AutoUpdateWorker.UpdateChecker checker = AutoUpdateWorker.automaticUpdateCheckerFactory(context -> {
            checks.incrementAndGet();
            return result(false);
        }).create(null);

        GitHubUpdater.UpdateResult result = checker.check();

        assertEquals(1, checks.get());
        assertEquals("done", result.message);
    }

    private static GitHubUpdater.UpdateResult result(boolean retryable) {
        return new GitHubUpdater.UpdateResult(!retryable, retryable ? "retry later" : "done", null, false, retryable);
    }

    private static void assertSuccess(ListenableWorker.Result result) {
        assertTrue(result instanceof ListenableWorker.Result.Success);
    }

    private static void assertRetry(ListenableWorker.Result result) {
        assertTrue(result instanceof ListenableWorker.Result.Retry);
    }
}
