package dev.bee.kanjianki

import dev.bee.kanjianki.core.NewCardSortPlanner
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.SettingsTextCopy
import java.util.Locale

internal data class SettingsNewCardSortPreviewRowsSnapshot(
    val sourceRows: List<RecordsImportModels.DashboardRow>,
    val previewRowsByMode: Map<String, List<SettingsNewCardSortPreviewRowModel>>,
    val previewWarningsByMode: Map<String, String>,
)

internal object SettingsNewCardSortPreviewCache {
    fun resolve(
        rows: List<RecordsImportModels.DashboardRow>,
        cached: SettingsNewCardSortPreviewRowsSnapshot?,
        hasSimilarLocalPair: (String?, String?) -> Boolean,
    ): SettingsNewCardSortPreviewRowsSnapshot {
        if (cached != null && cached.sourceRows === rows) {
            return cached
        }
        val previewRowsByMode = buildPreviewRowsByMode(rows)
        return SettingsNewCardSortPreviewRowsSnapshot(
            sourceRows = rows,
            previewRowsByMode = previewRowsByMode,
            previewWarningsByMode = buildPreviewWarningsByMode(
                previewRowsByMode = previewRowsByMode,
                hasSimilarLocalPair = hasSimilarLocalPair,
            ),
        )
    }

    internal fun buildPreviewRowsByMode(rows: List<RecordsImportModels.DashboardRow>): Map<String, List<SettingsNewCardSortPreviewRowModel>> {
        if (rows.isEmpty()) {
            return emptyMap()
        }
        return listOf(
            RecordsBase.NEW_CARD_SORT_FREQUENCY,
            RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY,
            RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY,
            RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK,
            RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS,
        ).associate { mode ->
            mode to NewCardSortPlanner.sortedAdmissionRows(rows, mode)
                .take(PREVIEW_LIMIT)
                .map { row -> previewRow(row, mode) }
        }
    }

    internal fun buildPreviewWarningsByMode(
        previewRowsByMode: Map<String, List<SettingsNewCardSortPreviewRowModel>>,
        hasSimilarLocalPair: (String?, String?) -> Boolean,
    ): Map<String, String> {
        if (previewRowsByMode.isEmpty()) {
            return emptyMap()
        }
        val warnings = LinkedHashMap<String, String>()
        for ((mode, rows) in previewRowsByMode) {
            val examples = SettingsNewCardSortPreviewWarnings.nearbySimilarPairExamples(rows) { first, second ->
                hasSimilarLocalPair(first, second)
            }
            if (examples.isNotEmpty()) {
                warnings[mode] = SettingsTextCopy.newCardSortConfusablePreviewWarning(examples)
            }
        }
        return warnings
    }

    private fun previewRow(
        row: RecordsImportModels.DashboardRow,
        mode: String,
    ): SettingsNewCardSortPreviewRowModel {
        return SettingsNewCardSortPreviewRowModel(
            kanji = row.kanji,
            primaryMeaning = row.primaryMeaning,
            scoreLabel = previewScoreLabel(row, mode),
        )
    }

    private fun previewScoreLabel(
        row: RecordsImportModels.DashboardRow,
        mode: String,
    ): String {
        return when (mode) {
            RecordsBase.NEW_CARD_SORT_FREQUENCY -> rankLabel(row.jitenRank)
            RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY -> decimalLabel("Difficulty", maxDifficulty(row))
            RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK -> riskLabel(minRetrievability(row))
            RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS -> "Weak ${row.weaknessScore.coerceAtLeast(0)}"
            RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY -> balancedLabel(row)
            else -> rankLabel(row.jitenRank)
        }
    }

    private fun balancedLabel(row: RecordsImportModels.DashboardRow): String {
        return "Balanced · weak ${row.weaknessScore.coerceAtLeast(0)} · ${rankLabel(row.jitenRank)}"
    }

    private fun rankLabel(rank: Int?): String {
        return if (rank != null && rank > 0) "#${rank} frequency" else "No rank"
    }

    private fun decimalLabel(prefix: String, value: Double?): String {
        return if (value == null) prefix else String.format(Locale.ROOT, "%s %.1f", prefix, value)
    }

    private fun riskLabel(retrievability: Double?): String {
        if (retrievability == null) {
            return "Risk unknown"
        }
        val riskPercent = ((1.0 - retrievability.coerceIn(0.0, 1.0)) * 100.0).toInt()
        return "Risk ${riskPercent}%"
    }

    private fun maxDifficulty(row: RecordsImportModels.DashboardRow): Double? {
        return row.examples.mapNotNull { example -> example.fsrsDifficulty?.takeIf(Double::isFinite) }.maxOrNull()
    }

    private fun minRetrievability(row: RecordsImportModels.DashboardRow): Double? {
        return row.examples.mapNotNull { example -> normalizedRetrievability(example.fsrsRetrievability) }.minOrNull()
    }

    private fun normalizedRetrievability(value: Double?): Double? {
        if (value == null || !value.isFinite() || value < 0.0) {
            return null
        }
        if (value > 1.0 && value <= 100.0) {
            return value / 100.0
        }
        return if (value > 1.0) null else value
    }

    private const val PREVIEW_LIMIT = 10
}