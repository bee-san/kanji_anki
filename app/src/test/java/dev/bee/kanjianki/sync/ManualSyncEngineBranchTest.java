package dev.bee.kanjianki.sync;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSyncModels;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class ManualSyncEngineBranchTest {
    @Test
    public void activeRowsReturnsOriginalWhenNothingIsSuspendedAndFiltersLocalSuspensions() throws Exception {
        ManualSyncEngine engine = engineWithSettings(RecordsSyncModels.Settings.kikuDefaults());
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(row("拉"), row("裂"));

        @SuppressWarnings("unchecked")
        List<RecordsImportModels.DashboardRow> unchanged = (List<RecordsImportModels.DashboardRow>) invoke(
                engine,
                "activeRows",
                new Class<?>[]{List.class, Set.class},
                rows,
                Collections.emptySet()
        );
        @SuppressWarnings("unchecked")
        List<RecordsImportModels.DashboardRow> filtered = (List<RecordsImportModels.DashboardRow>) invoke(
                engine,
                "activeRows",
                new Class<?>[]{List.class, Set.class},
                rows,
                Collections.singleton("裂")
        );

        assertSame(rows, unchanged);
        assertEquals(1, filtered.size());
        assertEquals("拉", filtered.get(0).kanji);
    }

    @Test
    public void importRangeChecksRejectUnknownAndOutOfRangeRanks() throws Exception {
        ManualSyncEngine engine = engineWithSettings(RecordsSyncModels.Settings.kikuDefaults());

        assertFalse(importInFrequencyRange(engine, suspendedImport("謎", null, 1L)));
        assertFalse(importInFrequencyRange(engine, suspendedImport("謎", 99, 2L)));
        assertFalse(importInFrequencyRange(engine, suspendedImport("謎", 3001, 3L)));
        assertTrue(importInFrequencyRange(engine, suspendedImport("箱", 2500, 4L)));
    }

    @Test
    public void addImportsSkipsOutOfRangeEntriesAndKeepsInRangeEntries() throws Exception {
        ManualSyncEngine engine = engineWithSettings(RecordsSyncModels.Settings.kikuDefaults());
        Map<String, Object> byKanji = new LinkedHashMap<>();

        invoke(
                engine,
                "addImports",
                new Class<?>[]{Map.class, List.class},
                byKanji,
                Arrays.asList(
                        suspendedImport("低", 50, 1L),
                        suspendedImport("箱", 2500, 2L),
                        suspendedImport("謎", null, 3L)
                )
        );

        assertEquals(1, byKanji.size());
        assertTrue(byKanji.containsKey("箱"));
    }

    @Test
    public void suspendedImportsOnlyDropsActiveSourcesAndEmptyImports() throws Exception {
        ManualSyncEngine engine = engineWithSettings(RecordsSyncModels.Settings.kikuDefaults());
        RecordsImportModels.SuspendedImport mixed = new RecordsImportModels.SuspendedImport(
                "箱",
                2500,
                true,
                3000,
                Arrays.asList(
                        suspendedImport("箱", 2500, 1L).sources.get(0),
                        activeSource("箱", 2L)
                )
        );
        RecordsImportModels.SuspendedImport activeOnly = new RecordsImportModels.SuspendedImport(
                "認",
                200,
                true,
                3000,
                Collections.singletonList(activeSource("認", 3L))
        );

        @SuppressWarnings("unchecked")
        List<RecordsImportModels.SuspendedImport> filtered = (List<RecordsImportModels.SuspendedImport>) invoke(
                engine,
                "suspendedImportsOnly",
                new Class<?>[]{List.class},
                Arrays.asList(mixed, activeOnly)
        );

        assertEquals(1, filtered.size());
        assertEquals("箱", filtered.get(0).kanji);
        assertEquals(1, filtered.get(0).sources.size());
        assertTrue(filtered.get(0).sources.get(0).suspended);
    }

    @Test
    public void mutableImportTakesFirstKnownRankAndLargestCutoff() throws Exception {
        Class<?> mutableClass = Class.forName(ManualSyncEngine.class.getName() + "$MutableImport");
        Constructor<?> constructor = mutableClass.getDeclaredConstructor(RecordsImportModels.SuspendedImport.class);
        constructor.setAccessible(true);
        Object mutable = constructor.newInstance(suspendedImport("箱", null, 1L, 1200));
        Method add = mutableClass.getDeclaredMethod("add", RecordsImportModels.SuspendedImport.class);
        add.setAccessible(true);
        Method build = mutableClass.getDeclaredMethod("build");
        build.setAccessible(true);

        add.invoke(mutable, suspendedImport("箱", null, 1L, 1200));
        add.invoke(mutable, suspendedImport("箱", 2500, 2L, 3000));
        RecordsImportModels.SuspendedImport built = (RecordsImportModels.SuspendedImport) build.invoke(mutable);

        assertEquals(Integer.valueOf(2500), built.jitenRank);
        assertTrue(built.rankKnown);
        assertEquals(3000, built.cutoffUsed);
        assertEquals(2, built.sources.size());
    }

    @Test
    public void mutableImportKeepsInitialKnownRankWhenLaterSourcesAlsoHaveRanks() throws Exception {
        Class<?> mutableClass = Class.forName(ManualSyncEngine.class.getName() + "$MutableImport");
        Constructor<?> constructor = mutableClass.getDeclaredConstructor(RecordsImportModels.SuspendedImport.class);
        constructor.setAccessible(true);
        Object mutable = constructor.newInstance(suspendedImport("箱", 1800, 1L, 1200));
        Method add = mutableClass.getDeclaredMethod("add", RecordsImportModels.SuspendedImport.class);
        add.setAccessible(true);
        Method build = mutableClass.getDeclaredMethod("build");
        build.setAccessible(true);

        add.invoke(mutable, suspendedImport("箱", 1800, 1L, 1200));
        add.invoke(mutable, suspendedImport("箱", 2500, 2L, 3000));
        RecordsImportModels.SuspendedImport built = (RecordsImportModels.SuspendedImport) build.invoke(mutable);

        assertEquals(Integer.valueOf(1800), built.jitenRank);
        assertTrue(built.rankKnown);
        assertEquals(3000, built.cutoffUsed);
        assertEquals(2, built.sources.size());
    }

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

    private static boolean importInFrequencyRange(ManualSyncEngine engine, RecordsImportModels.SuspendedImport imported) throws Exception {
        return (Boolean) invoke(
                engine,
                "importInFrequencyRange",
                new Class<?>[]{RecordsImportModels.SuspendedImport.class},
                imported
        );
    }

    private static ManualSyncEngine engineWithSettings(RecordsSyncModels.Settings settings) throws Exception {
        ManualSyncEngine engine = (ManualSyncEngine) allocate(ManualSyncEngine.class);
        Field settingsField = ManualSyncEngine.class.getDeclaredField("settings");
        settingsField.setAccessible(true);
        settingsField.set(engine, settings);
        return engine;
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = ManualSyncEngine.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Object allocate(Class<?> targetClass) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return allocateInstance.invoke(unsafe, targetClass);
    }

    private static RecordsImportModels.DashboardRow row(String kanji) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                100,
                "meaning",
                "reading",
                "browser",
                0,
                "reason",
                "reason",
                1,
                0,
                0,
                Collections.emptyList()
        );
    }

    private static RecordsImportModels.SuspendedImport suspendedImport(String kanji, Integer rank, long cardId) {
        return suspendedImport(kanji, rank, cardId, 3000);
    }

    private static RecordsImportModels.SuspendedImport suspendedImport(String kanji, Integer rank, long cardId, int cutoff) {
        return new RecordsImportModels.SuspendedImport(
                kanji,
                rank,
                rank != null,
                cutoff,
                Collections.singletonList(new RecordsImportModels.SuspendedSource(
                        kanji,
                        cardId,
                        cardId,
                        kanji,
                        "かな",
                        "meaning",
                        RecordsImportModels.SuspendedSourceDetails.builder(kanji + "を見た。").build()
                ))
        );
    }

    private static RecordsImportModels.SuspendedSource activeSource(String kanji, long cardId) {
        return new RecordsImportModels.SuspendedSource(
                kanji,
                cardId,
                cardId,
                kanji,
                "かな",
                "meaning",
                RecordsImportModels.SuspendedSourceDetails.builder(kanji + "を見た。")
                        .suspended(false)
                        .sourceType(RecordsBase.SOURCE_ACTIVE)
                        .build()
        );
    }
}
