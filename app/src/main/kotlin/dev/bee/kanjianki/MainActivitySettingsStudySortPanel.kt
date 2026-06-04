package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.NewCardSortPlanner
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy
import java.util.Locale

internal class MainActivitySettingsStudySortPanel(private val activity: MainActivitySettings) {
    fun newCardSortSettingsPanelModel(current: RecordsSyncModels.Settings): SettingsNewCardSortPanelModel {
        val previewRowsByMode = newCardSortPreviewRowsByMode()
        return SettingsNewCardSortPanelModel(
            title = SettingsTextCopy.newCardSortTitle(),
            body = SettingsTextCopy.newCardSortBody(),
            initialMode = current.newCardSortMode,
            options = newCardSortOptions(),
            saveLabel = SettingsTextCopy.saveNewCardSortLabel(),
            previewRowsByMode = previewRowsByMode,
            previewWarningsByMode = newCardSortPreviewWarningsByMode(previewRowsByMode),
            onSave = SettingsNewCardSortSaver { mode -> saveNewCardSort(mode) }
        )
    }

    private fun newCardSortOptions(): List<SettingsNewCardSortOptionModel> {
        return listOf(
            newCardSortOption(RecordsBase.NEW_CARD_SORT_FREQUENCY),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_BALANCED_PRIORITY),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_FSRS_DIFFICULTY),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
            newCardSortOption(RecordsBase.NEW_CARD_SORT_KANI_WEAKNESS)
        )
    }

    private fun newCardSortOption(mode: String): SettingsNewCardSortOptionModel {
        return SettingsNewCardSortOptionModel(
            label = SettingsTextCopy.newCardSortLabel(mode),
            mode = mode,
            description = SettingsTextCopy.newCardSortDescription(mode)
        )
    }

    private fun newCardSortPreviewRowsByMode(): Map<String, List<SettingsNewCardSortPreviewRowModel>> {
        val rows = activity.store.activeDashboardRows()
        if (rows.isEmpty()) {
            return emptyMap()
        }
        return newCardSortOptions().associate { option ->
            option.mode to NewCardSortPlanner
                .sortedAdmissionRows(rows, option.mode)
                .take(PREVIEW_LIMIT)
                .map { row -> previewRow(row, option.mode) }
        }
    }

    private fun newCardSortPreviewWarningsByMode(
        previewRowsByMode: Map<String, List<SettingsNewCardSortPreviewRowModel>>,
    ): Map<String, String> {
        if (previewRowsByMode.isEmpty()) {
            return emptyMap()
        }
        val warnings = LinkedHashMap<String, String>()
        for ((mode, rows) in previewRowsByMode) {
            val examples = SettingsNewCardSortPreviewWarnings.nearbySimilarPairExamples(rows) { first, second ->
                activity.store.hasSimilarLocalPair(first, second)
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
        return row.examples.mapNotNull { it.fsrsDifficulty?.takeIf(Double::isFinite) }.maxOrNull()
    }

    private fun minRetrievability(row: RecordsImportModels.DashboardRow): Double? {
        return row.examples.mapNotNull { normalizedRetrievability(it.fsrsRetrievability) }.minOrNull()
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

    private fun saveNewCardSort(mode: String) {
        val request = activity.store.saveNewCardSortMode(mode)
        Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
        activity.renderSettings(true)
    }

    private companion object {
        private const val PREVIEW_LIMIT = 10
    }
}
