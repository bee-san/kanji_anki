package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SuspendedImportPolicy {
    private SuspendedImportPolicy() {
    }

    public static List<RecordsImportModels.DashboardRow> activeRows(
            List<RecordsImportModels.DashboardRow> rows,
            Set<String> suspendedKanji
    ) {
        if (rows == null) {
            return Collections.emptyList();
        }
        if (suspendedKanji == null || suspendedKanji.isEmpty()) {
            return rows;
        }
        List<RecordsImportModels.DashboardRow> out = new ArrayList<>();
        for (RecordsImportModels.DashboardRow row : rows) {
            if (!suspendedKanji.contains(row.kanji)) {
                out.add(row);
            }
        }
        return out;
    }

    public static List<RecordsImportModels.SuspendedImport> mergeSuspendedImports(
            List<RecordsImportModels.SuspendedImport> stored,
            List<RecordsImportModels.SuspendedImport> current,
            RecordsSyncModels.Settings settings
    ) {
        Map<String, MutableImport> byKanji = new LinkedHashMap<>();
        addImports(byKanji, stored, settings);
        addImports(byKanji, current, settings);
        List<RecordsImportModels.SuspendedImport> out = new ArrayList<>();
        for (MutableImport item : byKanji.values()) {
            out.add(item.build());
        }
        return out;
    }

    public static List<RecordsImportModels.SuspendedImport> suspendedImportsOnly(
            List<RecordsImportModels.SuspendedImport> imports
    ) {
        List<RecordsImportModels.SuspendedImport> out = new ArrayList<>();
        for (RecordsImportModels.SuspendedImport imported : safeImports(imports)) {
            List<RecordsImportModels.SuspendedSource> suspendedSources = new ArrayList<>();
            for (RecordsImportModels.SuspendedSource source : imported.sources) {
                if (source.suspended) {
                    suspendedSources.add(source);
                }
            }
            if (!suspendedSources.isEmpty()) {
                out.add(new RecordsImportModels.SuspendedImport(
                        imported.kanji,
                        imported.jitenRank,
                        imported.rankKnown,
                        imported.cutoffUsed,
                        suspendedSources
                ));
            }
        }
        return out;
    }

    public static boolean importInFrequencyRange(
            RecordsImportModels.SuspendedImport imported,
            RecordsSyncModels.Settings settings
    ) {
        RecordsSyncModels.Settings safeSettings = settings == null ? RecordsSyncModels.Settings.kikuDefaults() : settings;
        return imported != null
                && imported.jitenRank != null
                && imported.jitenRank >= safeSettings.suspendedRankMin
                && imported.jitenRank <= safeSettings.suspendedRankMax;
    }

    private static void addImports(
            Map<String, MutableImport> byKanji,
            List<RecordsImportModels.SuspendedImport> imports,
            RecordsSyncModels.Settings settings
    ) {
        for (RecordsImportModels.SuspendedImport imported : safeImports(imports)) {
            if (!importInFrequencyRange(imported, settings)) {
                continue;
            }
            MutableImport target = byKanji.computeIfAbsent(imported.kanji, ignored -> new MutableImport(imported));
            target.add(imported);
        }
    }

    private static List<RecordsImportModels.SuspendedImport> safeImports(List<RecordsImportModels.SuspendedImport> imports) {
        return imports == null ? Collections.emptyList() : imports;
    }

    private static final class MutableImport {
        private final String kanji;
        private Integer rank;
        private boolean rankKnown;
        private int cutoffUsed;
        private final Map<Long, RecordsImportModels.SuspendedSource> sources = new LinkedHashMap<>();

        private MutableImport(RecordsImportModels.SuspendedImport imported) {
            this.kanji = imported.kanji;
            this.rank = imported.jitenRank;
            this.rankKnown = imported.rankKnown;
            this.cutoffUsed = imported.cutoffUsed;
        }

        private void add(RecordsImportModels.SuspendedImport imported) {
            if (rank == null && imported.jitenRank != null) {
                rank = imported.jitenRank;
                rankKnown = true;
            }
            cutoffUsed = Math.max(cutoffUsed, imported.cutoffUsed);
            for (RecordsImportModels.SuspendedSource source : imported.sources) {
                sources.put(source.cardId, source);
            }
        }

        private RecordsImportModels.SuspendedImport build() {
            return new RecordsImportModels.SuspendedImport(kanji, rank, rankKnown, cutoffUsed, new ArrayList<>(sources.values()));
        }
    }
}
