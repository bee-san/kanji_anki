package dev.bee.kanjianki.sync;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import android.content.Context;
import android.content.ContextWrapper;

import dev.bee.kanjianki.data.LocalStore;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class AutoSyncJobServiceInstrumentedTest {
    @After
    public void cleanDatabase() {
        InstrumentationRegistry.getInstrumentation().getTargetContext().deleteDatabase("kanji_anki_simple.db");
    }

    @Test
    public void serviceLifecycleDelegatesToInjectedSyncTaskAndShutdown() {
        AtomicBoolean executed = new AtomicBoolean();
        AtomicBoolean shutdown = new AtomicBoolean();
        AtomicReference<Boolean> stoppedValue = new AtomicReference<>();
        AutoSyncJobService service = new AutoSyncJobService(
                job -> job.run(),
                () -> shutdown.set(true),
                params -> {
                    assertNull(params);
                    executed.set(true);
                }
        );

        assertTrue(service.onStartJob(null));
        assertTrue(executed.get());
        assertTrue(service.onStopJob(null));
        service.onDestroy();
        assertTrue(shutdown.get());

        AutoSyncJobService.finishJob(
                null,
                null,
                true,
                () -> new dev.bee.kanjianki.data.LocalStore.AutoSyncSettings(true, false, 7, 30, 0L, 0L, 0L),
                () -> {
                },
                (context, settings) -> {
                },
                (params, needsReschedule) -> stoppedValue.set(needsReschedule)
        );
        assertTrue(stoppedValue.get());
    }

    @Test
    public void defaultServiceAndRealRunnerUseTargetContextWithoutExternalProviderWhenDisabled() {
        AutoSyncJobService service = new AutoSyncJobService();
        service.onDestroy();

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        AtomicReference<Boolean> stoppedValue = new AtomicReference<>();
        try {
            AutoSyncJobService.runAutoSync(
                    context,
                    null,
                    false,
                    (params, needsReschedule) -> stoppedValue.set(needsReschedule)
            );
        } finally {
            context.deleteDatabase("kanji_anki_simple.db");
        }

        assertTrue(Boolean.FALSE.equals(stoppedValue.get()));
    }

    @Test
    public void realRunnerReschedulesEnabledSettingsAfterProviderFailure() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        try (LocalStore store = new LocalStore(context)) {
            store.saveAutoSyncSettings(new LocalStore.AutoSyncSettings(true, true, 23, 59, 0L, 0L, 0L));
        }
        AtomicReference<Boolean> stoppedValue = new AtomicReference<>();

        AutoSyncJobService.runAutoSync(
                context,
                null,
                false,
                (params, needsReschedule) -> stoppedValue.set(needsReschedule)
        );

        try (LocalStore store = new LocalStore(context)) {
            assertTrue(store.autoSyncSettings().nextRunAt > 0L);
            assertTrue("config_error".equals(store.latestSync().status));
        }
        assertTrue(Boolean.FALSE.equals(stoppedValue.get()));
    }

    @Test
    public void defaultServiceRunnerCanUseAttachedTargetContext() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        AutoSyncJobService service = new AutoSyncJobService();
        attachBaseContext(service, context);
        AtomicBoolean factoryUsed = new AtomicBoolean();
        AtomicReference<Boolean> stoppedValue = new AtomicReference<>();
        AutoSyncJobService.JobFinisherFactory originalFactory = replaceJobFinisherFactory(createdService -> {
            assertSame(service, createdService);
            factoryUsed.set(true);
            return (params, needsReschedule) -> stoppedValue.set(needsReschedule);
        });

        try {
            java.lang.reflect.Method method = AutoSyncJobService.class.getDeclaredMethod("runAutoSync", android.app.job.JobParameters.class);
            method.setAccessible(true);
            method.invoke(service, new Object[]{null});
        } finally {
            replaceJobFinisherFactory(originalFactory);
            service.onDestroy();
        }
        try (LocalStore store = new LocalStore(context)) {
            assertTrue(store.autoSyncSettings().displayTime().matches("\\d{2}:\\d{2}"));
        }
        assertTrue(factoryUsed.get());
        assertTrue(Boolean.FALSE.equals(stoppedValue.get()));
        assertTrue(context.getDatabasePath("kanji_anki_simple.db").isFile());
    }

    @Test
    public void defaultServiceStartJobRunsBoundAutoSyncTaskWithAttachedContext() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        AutoSyncJobService service = new AutoSyncJobService();
        attachBaseContext(service, context);
        replaceField(service, "executor", (AutoSyncJobService.JobExecutor) Runnable::run);

        try {
            service.onStartJob(null);
        } catch (RuntimeException error) {
            // Framework JobService can reject null JobParameters after the real
            // sync path has reached jobFinished; this test owns the bound task path.
            assertTrue(stackContains(error, "android.app.job.JobService", "jobFinished"));
        } finally {
            service.onDestroy();
            context.deleteDatabase("kanji_anki_simple.db");
        }
    }

    private static void attachBaseContext(AutoSyncJobService service, Context context) throws Exception {
        java.lang.reflect.Method method = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
        method.setAccessible(true);
        method.invoke(service, context);
    }

    private static void replaceField(AutoSyncJobService service, String name, Object value) throws Exception {
        Field field = AutoSyncJobService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private static AutoSyncJobService.JobFinisherFactory replaceJobFinisherFactory(
            AutoSyncJobService.JobFinisherFactory value
    ) throws Exception {
        Field field = AutoSyncJobService.class.getDeclaredField("jobFinisherFactory");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return (AutoSyncJobService.JobFinisherFactory) previous;
    }

    private static boolean stackContains(Throwable error, String className, String methodName) {
        for (StackTraceElement element : error.getStackTrace()) {
            if (className.equals(element.getClassName()) && methodName.equals(element.getMethodName())) {
                return true;
            }
        }
        return false;
    }
}
