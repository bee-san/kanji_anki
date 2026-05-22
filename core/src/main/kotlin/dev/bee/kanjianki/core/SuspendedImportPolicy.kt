package dev.bee.kanjianki.core

import java.util.LinkedHashMap

object SuspendedImportPolicy {
    @JvmStatic
    fun activeRows(
        rows: List<RecordsImportModels.DashboardRow>?,
        suspendedKanji: Set<String>?,
    ): List<RecordsImportModels.DashboardRow> {
        if (rows == null) {
            return emptyList()
        }
        if (suspendedKanji.isNullOrEmpty()) {
            return rows
        }
        val out = ArrayList<RecordsImportModels.DashboardRow>()
        for (row in rows) {
            if (!suspendedKanji.contains(row.kanji)) {
                out.add(row)
            }
        }
        return out
    }

    @JvmStatic
    fun mergeSuspendedImports(
        stored: List<RecordsImportModels.SuspendedImport>?,
        current: List<RecordsImportModels.SuspendedImport>?,
        settings: RecordsSyncModels.Settings?,
    ): List<RecordsImportModels.SuspendedImport> {
        val byKanji = LinkedHashMap<String, MutableImport>()
        addImports(byKanji, stored, settings)
        addImports(byKanji, current, settings)
        val out = ArrayList<RecordsImportModels.SuspendedImport>()
        for (item in byKanji.values) {
            out.add(item.build())
        }
        return out
    }

    @JvmStatic
    fun suspendedImportsOnly(
        imports: List<RecordsImportModels.SuspendedImport>?,
    ): List<RecordsImportModels.SuspendedImport> {
        val out = ArrayList<RecordsImportModels.SuspendedImport>()
        for (imported in imports.orEmpty()) {
            val suspendedSources = ArrayList<RecordsImportModels.SuspendedSource>()
            for (source in imported.sources) {
                if (source.suspended) {
                    suspendedSources.add(source)
                }
            }
            if (suspendedSources.isNotEmpty()) {
                out.add(
                    RecordsImportModels.SuspendedImport(
                        imported.kanji,
                        imported.jitenRank,
                        imported.rankKnown,
                        imported.cutoffUsed,
                        suspendedSources,
                    ),
                )
            }
        }
        return out
    }

    @JvmStatic
    fun importInFrequencyRange(
        imported: RecordsImportModels.SuspendedImport?,
        settings: RecordsSyncModels.Settings?,
    ): Boolean {
        val safeSettings = settings ?: RecordsSyncModels.Settings.kikuDefaults()
        return imported != null &&
            imported.jitenRank != null &&
            imported.jitenRank >= safeSettings.suspendedRankMin &&
            imported.jitenRank <= safeSettings.suspendedRankMax
    }

    private fun addImports(
        byKanji: MutableMap<String, MutableImport>,
        imports: List<RecordsImportModels.SuspendedImport>?,
        settings: RecordsSyncModels.Settings?,
    ) {
        for (imported in imports.orEmpty()) {
            if (!importInFrequencyRange(imported, settings)) {
                continue
            }
            val target = byKanji.computeIfAbsent(imported.kanji) { MutableImport(imported) }
            target.add(imported)
        }
    }

    private class MutableImport(imported: RecordsImportModels.SuspendedImport) {
        private val kanji = imported.kanji
        private var rank = imported.jitenRank
        private var rankKnown = imported.rankKnown
        private var cutoffUsed = imported.cutoffUsed
        private val sources = LinkedHashMap<Long, RecordsImportModels.SuspendedSource>()

        fun add(imported: RecordsImportModels.SuspendedImport) {
            if (rank == null && imported.jitenRank != null) {
                rank = imported.jitenRank
                rankKnown = true
            }
            cutoffUsed = maxOf(cutoffUsed, imported.cutoffUsed)
            for (source in imported.sources) {
                sources[source.cardId] = source
            }
        }

        fun build(): RecordsImportModels.SuspendedImport {
            return RecordsImportModels.SuspendedImport(kanji, rank, rankKnown, cutoffUsed, ArrayList(sources.values))
        }
    }
}
