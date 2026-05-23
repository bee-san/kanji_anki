package dev.bee.kanjianki.sync;

import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;

public final class ManualSyncEngineBranchTest {
    @Test
    public void syncResultNormalizesNullAdaptiveSummary() throws Exception {
        Class<?> resultClass = ManualSyncEngine.SyncResult.class;
        Constructor<?> constructor = resultClass.getDeclaredConstructor(
                boolean.class,
                boolean.class,
                int.class,
                int.class,
                String.class,
                String.class
        );
        constructor.setAccessible(true);

        ManualSyncEngine.SyncResult result = (ManualSyncEngine.SyncResult) constructor.newInstance(
                true,
                false,
                3,
                2,
                "ok",
                null
        );

        assertEquals("", result.adaptiveSummary);
    }

    @Test
    public void syncResultKeepsAdaptiveSummaryWhenPlannerReportsStatus() throws Exception {
        Class<?> resultClass = ManualSyncEngine.SyncResult.class;
        Constructor<?> constructor = resultClass.getDeclaredConstructor(
                boolean.class,
                boolean.class,
                int.class,
                int.class,
                String.class,
                String.class
        );
        constructor.setAccessible(true);

        ManualSyncEngine.SyncResult result = (ManualSyncEngine.SyncResult) constructor.newInstance(
                true,
                false,
                3,
                2,
                "ok",
                "3 due, 1 new"
        );

        assertEquals("3 due, 1 new", result.adaptiveSummary);
    }
}
